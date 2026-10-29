package com.arn.scrobble.api.atproto

import androidx.datastore.core.DataStore
import com.arn.scrobble.api.AccountType
import com.arn.scrobble.atproto.http.AtProtoHttpClientLease
import com.arn.scrobble.atproto.oauth.AtProtoAccountAuthority
import com.arn.scrobble.atproto.oauth.OAuthClientConfig
import com.arn.scrobble.atproto.storage.AtProtoAccountStore
import com.arn.scrobble.atproto.storage.JvmVaultPersistence
import com.arn.scrobble.atproto.storage.StaleSessionException
import com.arn.scrobble.atproto.storage.VaultCipher
import com.arn.scrobble.pref.MainPrefs
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import com.arn.scrobble.api.lastfm.ScrobbleData
import com.arn.scrobble.atproto.posting.AtProtoTarget
import java.io.File
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.file.Files
import kotlin.test.*

private class MemoryPrefs : DataStore<MainPrefs> {
    override val data = MutableStateFlow(MainPrefs())
    private val lock = Mutex()
    var fail = false
    var writing = false
    var entered: CompletableDeferred<Unit>? = null
    var release: CompletableDeferred<Unit>? = null
    override suspend fun updateData(transform: suspend (MainPrefs) -> MainPrefs): MainPrefs = lock.withLock {
        entered?.complete(Unit)
        release?.await()
        if (fail) throw IOException("projection persistence failed")
        writing = true
        try { transform(data.value).also { data.value = it } } finally { writing = false }
    }
}

private class AccountFixture {
    private val root = Files.createTempDirectory(File("build/atproto-test-vaults").also { it.mkdirs() }.toPath(), "projection-").toFile()
    val prefs = MemoryPrefs()
    val store = AtProtoAccountStore(JvmVaultPersistence(root), VaultCipher.Plaintext)
    var did = "did:plc:abcdefghijklmnopqrstuvwx"
    private var state = ""
    var tokens = 0
    var tokenEntered: CompletableDeferred<Unit>? = null
    var tokenRelease: CompletableDeferred<Unit>? = null
    val posts = mutableListOf<kotlinx.serialization.json.JsonObject>()
    private val client = HttpClient(MockEngine { request ->
        check(!prefs.writing) { "HTTP must not run in a general preference transaction" }
        val path = request.url.encodedPath
        val body = when {
            path.endsWith("resolveHandle") -> """{"did":"$did"}"""
            request.url.host == "plc.directory" -> """{"id":"$did","alsoKnownAs":["at://alice.example.com"],"service":[{"id":"#atproto_pds","type":"AtprotoPersonalDataServer","serviceEndpoint":"https://pds.example"}]}"""
            path.endsWith("oauth-protected-resource") -> """{"resource":"https://pds.example","authorization_servers":["https://pds.example"]}"""
            path.endsWith("oauth-authorization-server") -> """{"issuer":"https://pds.example","authorization_endpoint":"https://pds.example/authorize","token_endpoint":"https://pds.example/token","pushed_authorization_request_endpoint":"https://pds.example/par","response_types_supported":["code"],"grant_types_supported":["authorization_code","refresh_token"],"code_challenge_methods_supported":["S256"],"token_endpoint_auth_methods_supported":["none","private_key_jwt"],"token_endpoint_auth_signing_alg_values_supported":["ES256"],"scopes_supported":["atproto"],"dpop_signing_alg_values_supported":["ES256"],"authorization_response_iss_parameter_supported":true,"require_pushed_authorization_requests":true,"client_id_metadata_document_supported":true}"""
            path == "/par" -> {
                state = (request.body as TextContent).text.split('&').associate {
                    it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8")
                }.getValue("state")
                """{"request_uri":"urn:fixture:request","expires_in":300}"""
            }
            path == "/token" -> {
                tokens++
                tokenEntered?.complete(Unit)
                tokenRelease?.await()
                """{"access_token":"synthetic-access-secret","refresh_token":"synthetic-refresh-secret","token_type":"DPoP","scope":"atproto repo:fm.teal.feed.play?action=create","sub":"$did","expires_in":120}"""
            }
            path.endsWith("createRecord") -> {
                val post = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
                posts += post
                """{"uri":"at://$did/${post.getValue("collection").jsonPrimitive.content}/fixture","cid":"bafyrei${"a".repeat(52)}"}"""
            }
            else -> error("Unexpected fixture request")
        }
        respond(body, if (path == "/par") HttpStatusCode.Created else HttpStatusCode.OK,
            headersOf("Content-Type" to listOf("application/json"), "DPoP-Nonce" to listOf("n")))
    }) { followRedirects = false }
    val config = OAuthClientConfig("https://client.example/metadata.json", "example.client:/callback", "atproto repo:fm.teal.feed.play?action=create")
    fun authorityFor(selected: OAuthClientConfig = config) = AtProtoAccountAuthority(
        AtProtoAccountStore(JvmVaultPersistence(root), VaultCipher.Plaintext), selected, { AtProtoHttpClientLease(client) {} })
    val authority = authorityFor()
    val accounts = PanoAtProtoAccounts(authority, prefs)
    fun callback(redirect: String = config.redirectUri) = "$redirect?state=$state&code=fixture&iss=${URLEncoder.encode("https://pds.example", "UTF-8")}"
    suspend fun authorize(selected: AtProtoAccountAuthority = authority, redirect: String = config.redirectUri) = selected.run {
        beginAuthorization("alice.example.com")
        completeAuthorization(callback(redirect))
    }
}

class PanoAtProtoAccountsTest {
    @Test fun automaticBrowserLaunchIsClaimedOnceAcrossScreenReentryWhileExplicitReopeningRemainsAvailable(): Unit = runBlocking {
        val f = AccountFixture()
        val login = AtProtoLogin(f.accounts, CoroutineScope(coroutineContext))
        login.begin("alice.example.com")
        val launch = withTimeout(5_000) { login.state.first { it.launch != null } }.launch!!
        val screens = List(2) { async { login.consumeBrowserLaunch(launch) } }.map { it.await() }
        assertEquals(1, screens.count { it })
        assertEquals(launch, login.state.value.launch) // Explicit browser button still has the URL.
        assertFalse(login.consumeBrowserLaunch(launch)) // A recreated screen cannot reopen it automatically.
        login.completeCallback(f.callback())
        withTimeout(5_000) { login.state.first { it.completed != null } }
        assertFalse(login.consumeBrowserLaunch(launch))
        assertNull(login.state.value.launch)
    }

    @Test fun sameDidReplacementPreservesDisabledChoicesWhileNewDidStartsWithDefaults(): Unit = runBlocking {
        val f = AccountFixture()
        val first = f.authorize()
        f.accounts.publish(first.session)
        f.prefs.updateData { it.copy(scrobbleAccounts = it.scrobbleAccounts.map { reference ->
            reference.copy(canScrobble = false, atProtoTargets = emptySet())
        }) }
        val same = f.authorize()
        f.accounts.publish(same.session)
        assertFalse(f.prefs.data.value.scrobbleAccounts.single().canScrobble)
        assertTrue(f.prefs.data.value.scrobbleAccounts.single().atProtoTargets.isEmpty())
        f.did = "did:plc:zzzzzzzzzzzzzzzzzzzzzzzz"
        val other = f.authorize()
        f.accounts.publish(other.session)
        assertTrue(f.prefs.data.value.scrobbleAccounts.single().canScrobble)
        assertEquals(AtProtoTarget.entries.toSet(), f.prefs.data.value.scrobbleAccounts.single().atProtoTargets)
    }

    @Test fun restoredLoginCompletesExactSingleSlashCallbackWithoutUsingExistingAccountAsSuccess(): Unit = runBlocking {
        val f = AccountFixture()
        val old = f.authorize()
        f.accounts.publish(old.session)
        f.authority.beginAuthorization("alice.example.com")
        val valid = f.callback()
        val restored = AtProtoLogin(PanoAtProtoAccounts(f.authorityFor(), f.prefs), CoroutineScope(coroutineContext))
        assertNull(restored.state.value.completed) // Existing account is not replacement-login completion.
        restored.completeCallback(valid.replace("example.client:/callback", "example.client://callback"))
        withTimeout(5_000) { restored.state.first { !it.busy && it.error != null } }
        assertEquals(1, f.tokens)
        assertEquals(old, f.authority.currentAccount())
        restored.completeCallback(valid)
        val result = withTimeout(5_000) { restored.state.first { it.completed != null } }.completed!!
        assertNotEquals(old.session, result.session)
        assertEquals(result.session, f.prefs.data.value.scrobbleAccounts.single().atProtoSession)
        assertEquals(2, f.tokens)
        assertTrue(restored.consumeCompleted(result.session))
        restored.completeCallback(valid) // Duplicate initial/new intent in the same process is ignored.
        assertNull(restored.state.value.completed)
        assertEquals(2, f.tokens)
    }

    @Test fun overlappingScreensConsumeOnlyNewCompletionOnceAndDuplicateCallbacksCannotOverwriteIt(): Unit = runBlocking {
        val f = AccountFixture()
        f.authority.beginAuthorization("alice.example.com")
        f.tokenEntered = CompletableDeferred()
        f.tokenRelease = CompletableDeferred()
        val login = AtProtoLogin(f.accounts, CoroutineScope(coroutineContext))
        login.completeCallback(f.callback())
        f.tokenEntered!!.await()
        login.completeCallback(f.callback())
        login.begin("other.example.com") // A delayed exchange cannot be overwritten by a competing UI job.
        assertTrue(login.state.value.busy)
        assertEquals(1, f.tokens)
        f.tokenRelease!!.complete(Unit)
        val completed = withTimeout(5_000) { login.state.first { it.completed != null } }.completed!!
        assertEquals(1, f.tokens)
        assertFalse(login.consumeCompleted(completed.session.copy(generation = "wrong-generation")))
        val consumers = List(2) { async { login.consumeCompleted(completed.session) } }.map { it.await() }
        assertEquals(1, consumers.count { it })
        assertNull(login.state.value.completed)
    }

    @Test fun publicationFailureRetriesOnlyCompletedGenerationAndNeverExchangesCallbackAgain(): Unit = runBlocking {
        val f = AccountFixture()
        val previous = f.authorize()
        f.accounts.publish(previous.session)
        f.authority.beginAuthorization("alice.example.com")
        f.prefs.fail = true
        val login = AtProtoLogin(f.accounts, CoroutineScope(coroutineContext))
        login.completeCallback(f.callback())
        val failed = withTimeout(5_000) { login.state.first { it.publication != null && !it.busy } }.publication!!
        assertNotEquals(previous.session, failed.session)
        assertNull(login.state.value.completed)
        assertEquals(previous.session, f.prefs.data.value.scrobbleAccounts.single().atProtoSession)
        login.retryPublication()
        withTimeout(5_000) { login.state.first { !it.busy } }
        assertEquals(failed, login.state.value.publication) // A failed retry remains explicitly retryable.
        f.prefs.fail = false
        login.retryPublication()
        assertEquals(failed, withTimeout(5_000) { login.state.first { it.completed != null } }.completed)
        assertEquals(2, f.tokens)
        assertEquals(failed.session, f.prefs.data.value.scrobbleAccounts.single().atProtoSession)
        assertFalse(login.state.value.toString().contains("synthetic-access-secret"))
    }

    @Test fun publicationRetryCannotPublishAnArbitraryReplacement(): Unit = runBlocking {
        val f = AccountFixture()
        f.authority.beginAuthorization("alice.example.com")
        f.prefs.fail = true
        val login = AtProtoLogin(f.accounts, CoroutineScope(coroutineContext))
        login.completeCallback(f.callback())
        val failed = withTimeout(5_000) { login.state.first { it.publication != null && !it.busy } }.publication!!
        f.prefs.fail = false
        val replacement = f.authorize()
        f.accounts.publish(replacement.session)
        login.retryPublication()
        withTimeout(5_000) { login.state.first { !it.busy } }
        assertNull(login.state.value.completed)
        assertEquals(failed, login.state.value.publication)
        assertEquals(replacement.session, f.prefs.data.value.scrobbleAccounts.single().atProtoSession)
    }

    @Test fun staleReferenceRemovalCannotDeleteSameOrDifferentDidReplacement(): Unit = runBlocking {
        for (differentDid in listOf(true, false)) {
            val f = AccountFixture()
            val old = f.authorize()
            f.accounts.publish(old.session)
            val staleReference = f.prefs.data.value.scrobbleAccounts.single()
            if (differentDid) f.did = "did:plc:zzzzzzzzzzzzzzzzzzzzzzzz"
            val replacement = f.authorize()
            f.accounts.publish(replacement.session)
            f.accounts.remove(staleReference)
            assertEquals(replacement, f.authority.currentAccount())
            assertEquals(replacement.session, f.prefs.data.value.scrobbleAccounts.single().atProtoSession)
        }
    }

    @Test fun buildIdentitySwitchRequiresFreshLoginAndExplicitOldReferenceRemovalTargetsItsOwnNamespace(): Unit = runBlocking {
        val f = AccountFixture()
        val old = f.authorize()
        f.accounts.publish(old.session)
        val reference = f.prefs.data.value.scrobbleAccounts.single()
        val switched = f.config.copy(clientId = "https://other.example.com/metadata.json", redirectUri = "com.example.other:/callback")
        val newAuthority = f.authorityFor(switched)
        val newAccounts = PanoAtProtoAccounts(newAuthority, f.prefs)
        assertNull(newAuthority.currentAccount())
        assertTrue(AtProtoScrobblable(reference, newAuthority, f.prefs, "fixture").scrobble(
            ScrobbleData("Artist", "Track", "Album", 1_700_000_000_000, albumArtist = null, duration = 12_345, appId = "fixture")).isFailure)
        val fresh = f.authorize(newAuthority, switched.redirectUri)
        newAccounts.remove(reference) // Explicit recovery can remove the old client's saved credential.
        assertNull(f.authority.currentAccount())
        assertEquals(fresh, newAuthority.currentAccount())
        assertTrue(f.prefs.data.value.scrobbleAccounts.isEmpty())
        newAccounts.publish(fresh.session)
        assertEquals(fresh.session, f.prefs.data.value.scrobbleAccounts.single().atProtoSession)
        newAccounts.remove(reference)
        assertEquals(fresh, newAuthority.currentAccount())
        assertEquals(fresh.session, f.prefs.data.value.scrobbleAccounts.single().atProtoSession)
    }

    @Test fun removingAlreadyOrphanedReferenceDoesNotCancelNewPendingAuthorization(): Unit = runBlocking {
        val f = AccountFixture()
        val account = f.authorize()
        f.accounts.publish(account.session)
        val orphan = f.prefs.data.value.scrobbleAccounts.single()
        f.authority.logout() // Models a removed vault credential with a stale display reference.
        f.authority.beginAuthorization("alice.example.com")
        val callback = f.callback()
        f.accounts.remove(orphan)
        assertTrue(f.prefs.data.value.scrobbleAccounts.isEmpty())
        val completed = f.accounts.complete(callback)
        assertEquals(completed.session, f.prefs.data.value.scrobbleAccounts.single().atProtoSession)
    }

    @Test fun failedGenerationBoundRemovalLeavesNoAuthorizationAndExplicitRetryRemovesStaleReference(): Unit = runBlocking {
        val f = AccountFixture()
        val account = f.authorize()
        f.accounts.publish(account.session)
        val reference = f.prefs.data.value.scrobbleAccounts.single()
        f.prefs.fail = true
        assertFailsWith<IOException> { f.accounts.remove(reference) }
        assertNull(f.authority.currentAccount())
        assertEquals(reference, f.prefs.data.value.scrobbleAccounts.single())
        f.prefs.fail = false
        f.accounts.remove(reference)
        assertTrue(f.prefs.data.value.scrobbleAccounts.isEmpty())
    }

    @Test fun cachedProviderUsesFreshSelectionsAndCannotPostOrRepublishAfterReplacementOrLogout(): Unit = runBlocking {
        val f = AccountFixture()
        val account = f.authorize()
        f.accounts.publish(account.session)
        val reference = f.prefs.data.value.scrobbleAccounts.single()
        val cached = AtProtoScrobblable(reference, f.authority, f.prefs, "pano-fixture")
        f.prefs.updateData { it.copy(scrobbleAccounts = listOf(reference.copy(atProtoTargets = setOf(AtProtoTarget.TEAL)))) }
        val play = ScrobbleData("Artist", "Track", "Album", 1_700_000_000_000, albumArtist = null, duration = 12_345, appId = "fixture")
        assertTrue(cached.scrobble(play).isSuccess)
        assertEquals(1, f.posts.size)
        assertEquals("fm.teal.feed.play", f.posts.single().getValue("collection").jsonPrimitive.content)
        assertEquals("12", f.posts.single().getValue("record").jsonObject.getValue("duration").jsonPrimitive.content)
        val replacement = f.authorize()
        f.accounts.publish(replacement.session)
        assertEquals(setOf(AtProtoTarget.TEAL), f.prefs.data.value.scrobbleAccounts.single().atProtoTargets)
        assertTrue(cached.scrobble(play).isFailure)
        assertEquals(1, f.posts.size)
        f.accounts.logout()
        assertTrue(cached.scrobble(play).isFailure)
        assertTrue(f.prefs.data.value.scrobbleAccounts.isEmpty())
        assertTrue(cached.updateNowPlaying(play).getOrThrow().ignored)
    }

    @Test fun failedPublicationLeavesOnlyTheVaultAuthoritativeAndExplicitRetryPublishesNoSecrets(): Unit = runBlocking {
        val f = AccountFixture()
        val account = f.authorize()
        f.prefs.fail = true
        assertFailsWith<IOException> { f.accounts.publish(account.session) }
        assertTrue(f.prefs.data.value.scrobbleAccounts.isEmpty())
        assertEquals(account, f.authority.currentAccount())
        f.prefs.fail = false
        f.accounts.publish(account.session)
        val pointer = f.prefs.data.value.scrobbleAccounts.single()
        assertEquals(AccountType.ATPROTO, pointer.type)
        assertEquals(account.session, pointer.atProtoSession)
        assertEquals(account.session.did, pointer.authKey)
        val encoded = Json.encodeToString(f.prefs.data.value)
        assertFalse(encoded.contains("synthetic-access-secret"))
        assertFalse(encoded.contains("synthetic-refresh-secret"))
    }

    @Test fun oldSameDidAndDifferentDidPublicationsCannotRestoreReplacedOrRemovedAccounts(): Unit = runBlocking {
        val f = AccountFixture()
        val first = f.authorize()
        f.accounts.publish(first.session)
        val same = f.authorize()
        assertNotEquals(first.session, same.session)
        assertFailsWith<StaleSessionException> { f.accounts.publish(first.session) }
        f.accounts.publish(same.session)
        f.did = "did:plc:zzzzzzzzzzzzzzzzzzzzzzzz"
        val other = f.authorize()
        f.accounts.publish(other.session)
        assertFailsWith<StaleSessionException> { f.accounts.publish(same.session) }
        assertEquals(other.session, f.prefs.data.value.scrobbleAccounts.single().atProtoSession)
        f.accounts.logout()
        assertFailsWith<StaleSessionException> { f.accounts.publish(other.session) }
        assertTrue(f.prefs.data.value.scrobbleAccounts.isEmpty())
    }

    @Test fun logoutOrdersAfterShortPublicationAndFailedRemovalNeverLeavesUsableCredentials(): Unit = runBlocking {
        val f = AccountFixture()
        val account = f.authorize()
        f.prefs.entered = CompletableDeferred()
        f.prefs.release = CompletableDeferred()
        val publish = async { f.accounts.publish(account.session) }
        f.prefs.entered!!.await()
        val logout = async { f.accounts.logout() }
        delay(100)
        assertFalse(logout.isCompleted)
        f.prefs.release!!.complete(Unit)
        publish.await(); logout.await()
        assertNull(f.authority.currentAccount())
        assertTrue(f.prefs.data.value.scrobbleAccounts.isEmpty())
        val next = f.authorize()
        f.accounts.publish(next.session)
        f.prefs.fail = true
        assertFailsWith<IOException> { f.accounts.logout() }
        assertNull(f.authority.currentAccount())
        assertFailsWith<StaleSessionException> { f.authority.createRecord(next.session, "fm.teal.feed.play", buildJsonObject { put("\$type", "fm.teal.feed.play") }) }
        f.prefs.fail = false
        f.accounts.logout()
        assertTrue(f.prefs.data.value.scrobbleAccounts.isEmpty())
    }
}
