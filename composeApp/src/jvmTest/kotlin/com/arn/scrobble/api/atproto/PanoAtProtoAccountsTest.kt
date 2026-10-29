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
    var entered: CompletableDeferred<Unit>? = null
    var release: CompletableDeferred<Unit>? = null
    override suspend fun updateData(transform: suspend (MainPrefs) -> MainPrefs): MainPrefs = lock.withLock {
        entered?.complete(Unit)
        release?.await()
        if (fail) throw IOException("projection persistence failed")
        transform(data.value).also { data.value = it }
    }
}

private class AccountFixture {
    private val root = Files.createTempDirectory(File("build/atproto-test-vaults").also { it.mkdirs() }.toPath(), "projection-").toFile()
    val prefs = MemoryPrefs()
    val store = AtProtoAccountStore(JvmVaultPersistence(root), VaultCipher.Plaintext)
    var did = "did:plc:abcdefghijklmnopqrstuvwx"
    private var state = ""
    val posts = mutableListOf<kotlinx.serialization.json.JsonObject>()
    private val client = HttpClient(MockEngine { request ->
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
            path == "/token" -> """{"access_token":"synthetic-access-secret","refresh_token":"synthetic-refresh-secret","token_type":"DPoP","scope":"atproto repo:fm.teal.feed.play?action=create","sub":"$did","expires_in":120}"""
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
    val authority = AtProtoAccountAuthority(store, OAuthClientConfig("https://client.example/metadata.json", "example.client:/callback", "atproto repo:fm.teal.feed.play?action=create"), { AtProtoHttpClientLease(client) {} })
    val accounts = PanoAtProtoAccounts(authority, prefs)
    suspend fun authorize() = authority.run {
        beginAuthorization("alice.example.com")
        completeAuthorization("example.client:/callback?state=$state&code=fixture&iss=${URLEncoder.encode("https://pds.example", "UTF-8")}")
    }
}

class PanoAtProtoAccountsTest {
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
