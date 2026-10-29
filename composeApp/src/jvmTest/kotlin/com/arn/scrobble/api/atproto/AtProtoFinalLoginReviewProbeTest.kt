package com.arn.scrobble.api.atproto

// Review-only observations on 97db5e70. Invert before adopting as regressions.
// Session: opencode://ses_e7af14a6cffeDevLvL0joxsto6.

import androidx.datastore.core.DataStore
import com.arn.scrobble.atproto.http.AtProtoHttpClientLease
import com.arn.scrobble.atproto.oauth.AtProtoAccountAuthority
import com.arn.scrobble.atproto.oauth.OAuthClientConfig
import com.arn.scrobble.atproto.storage.AtProtoAccountStore
import com.arn.scrobble.atproto.storage.JvmVaultPersistence
import com.arn.scrobble.atproto.storage.VaultCipher
import com.arn.scrobble.pref.MainPrefs
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.URLDecoder
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

private const val DID = "did:plc:abcdefghijklmnopqrstuvwx"

private class LoginFixture : AutoCloseable {
    val prefs = object : DataStore<MainPrefs> {
        override val data = MutableStateFlow(MainPrefs())
        override suspend fun updateData(transform: suspend (MainPrefs) -> MainPrefs): MainPrefs =
            transform(data.value).also { data.value = it }
    }
    var parEntered: CompletableDeferred<Unit>? = null
    var parRelease: CompletableDeferred<Unit>? = null
    var state = ""
    var tokens = 0
    val client = HttpClient(MockEngine { request ->
        val path = request.url.encodedPath
        val body = when {
            request.url.host == "plc.directory" -> """{"id":"$DID","service":[{"id":"#atproto_pds","type":"AtprotoPersonalDataServer","serviceEndpoint":"https://pds.example"}]}"""
            path.endsWith("oauth-protected-resource") -> """{"resource":"https://pds.example","authorization_servers":["https://pds.example"]}"""
            path.endsWith("oauth-authorization-server") -> SERVER
            path == "/par" -> {
                state = (request.body as TextContent).text.split('&').associate {
                    it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), "UTF-8")
                }.getValue("state")
                parEntered?.complete(Unit)
                parRelease?.await()
                """{"request_uri":"urn:fixture:request","expires_in":300}"""
            }
            path == "/token" -> {
                tokens++
                """{"access_token":"synthetic-access","refresh_token":"synthetic-refresh","token_type":"DPoP","scope":"atproto","sub":"$DID","expires_in":120}"""
            }
            else -> error("Unexpected synthetic fixture request")
        }
        respond(body, if (path == "/par") HttpStatusCode.Created else HttpStatusCode.OK,
            headersOf("Content-Type" to listOf("application/json"), "DPoP-Nonce" to listOf("n")))
    }) { followRedirects = false }
    private val directory = Files.createTempDirectory(File("build/final-review-vaults").also { it.mkdirs() }.toPath(), "login-").toFile()
    val authority = AtProtoAccountAuthority(AtProtoAccountStore(JvmVaultPersistence(directory), VaultCipher.Plaintext),
        OAuthClientConfig("https://client.example/metadata.json", "example.client:/callback", "atproto"),
        { AtProtoHttpClientLease(client) {} })
    val accounts = PanoAtProtoAccounts(authority, prefs)
    fun callback() = "example.client:/callback?state=$state&code=synthetic&iss=https%3A%2F%2Fpds.example"
    override fun close() = client.close()
}

class AtProtoFinalLoginReviewProbeTest {
    @Test
    fun observedStaleCallbackSupersedesAndHidesANewerPendingBegin(): Unit = runBlocking {
        LoginFixture().use { f ->
            val jobs = SupervisorJob(coroutineContext[Job])
            val login = AtProtoLogin(f.accounts, CoroutineScope(coroutineContext + jobs))
            try {
            f.parEntered = CompletableDeferred()
            f.parRelease = CompletableDeferred()
            login.begin(DID)
            withTimeout(5_000) { f.parEntered!!.await() }
            login.completeCallback("example.client:/callback?state=stale&code=old&iss=https%3A%2F%2Fpds.example")
            withTimeout(5_000) { login.state.first { !it.busy && it.error != null } }
            f.parRelease!!.complete(Unit)
            withTimeout(5_000) { jobs.children.toList().forEach { it.join() } }
            assertNull(login.state.value.launch)
            assertNotNull(login.state.value.error)
            assertEquals(0, f.tokens)
            // The newer authorization DID finish durably; only its actual UI launch was discarded.
            assertNotNull(f.authority.completeAuthorization(f.callback()))
            assertEquals(1, f.tokens)
            jobs.complete()
            } finally { f.parRelease?.complete(Unit); jobs.cancel() }
        }
    }

    @Test
    fun observedDistinctValidCallbackIsDroppedWhileMalformedCallbackCompletes(): Unit = runBlocking {
        LoginFixture().use { f ->
            f.authority.beginAuthorization(DID)
            val jobs = SupervisorJob(coroutineContext[Job])
            val login = AtProtoLogin(f.accounts, CoroutineScope(coroutineContext + jobs))
            try {
            login.completeCallback("not-a-callback")
            login.completeCallback(f.callback())
            withTimeout(5_000) { jobs.children.toList().forEach { it.join() } }
            assertNotNull(login.state.value.error)
            assertNull(login.state.value.completed)
            assertEquals(0, f.tokens)
            // Only an explicit second delivery recovers the already received valid callback.
            login.completeCallback(f.callback())
            withTimeout(5_000) { login.state.first { it.completed != null } }
            assertEquals(1, f.tokens)
            jobs.complete()
            } finally { jobs.cancel() }
        }
    }

    @Test
    fun observedExplicitLogoutDoesNotClearRetainedBrowserLaunch(): Unit = runBlocking {
        LoginFixture().use { f ->
            val jobs = SupervisorJob(coroutineContext[Job])
            val login = AtProtoLogin(f.accounts, CoroutineScope(coroutineContext + jobs))
            try {
            login.begin(DID)
            val launch = withTimeout(5_000) { login.state.first { it.launch != null } }.launch!!
            f.accounts.logout()
            assertNull(f.authority.currentAccount())
            assertEquals(launch, login.state.value.launch)
            jobs.complete()
            } finally { jobs.cancel() }
        }
    }
}

private const val SERVER = """{
    "issuer":"https://pds.example","authorization_endpoint":"https://pds.example/authorize",
    "token_endpoint":"https://pds.example/token","pushed_authorization_request_endpoint":"https://pds.example/par",
    "response_types_supported":["code"],"grant_types_supported":["authorization_code","refresh_token"],
    "code_challenge_methods_supported":["S256"],"token_endpoint_auth_methods_supported":["none","private_key_jwt"],
    "token_endpoint_auth_signing_alg_values_supported":["ES256"],"scopes_supported":["atproto"],
    "dpop_signing_alg_values_supported":["ES256"],"authorization_response_iss_parameter_supported":true,
    "require_pushed_authorization_requests":true,"client_id_metadata_document_supported":true
}"""
