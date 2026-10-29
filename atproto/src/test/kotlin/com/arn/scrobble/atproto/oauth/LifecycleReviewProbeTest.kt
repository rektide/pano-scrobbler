package com.arn.scrobble.atproto.oauth

// Independent review of def70d8a / source04a2cda4, not a new lifecycle implementation.
// Methods named observed... intentionally prove frozen behavior; invert them for fixes.
// Session: opencode://ses_e7b181b8effef7JO12Vhel0XwH.

import com.arn.scrobble.atproto.http.AtProtoHttpClientLease
import com.arn.scrobble.atproto.http.DpopNonceException
import com.arn.scrobble.atproto.storage.AtProtoAccountStore
import com.arn.scrobble.atproto.storage.IncompleteSessionUpdateException
import com.arn.scrobble.atproto.storage.JvmVaultPersistence
import com.arn.scrobble.atproto.storage.StaleAuthorizationException
import com.arn.scrobble.atproto.storage.StaleSessionException
import com.arn.scrobble.atproto.storage.VaultCipher
import com.arn.scrobble.atproto.storage.VaultPersistence
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val REVIEW_DID = "did:plc:abcdefghijklmnopqrstuvwx"
private const val REVIEW_REDIRECT = "com.example.client:/pano-scrobbler/oauth/callback"
private const val REVIEW_SCOPE = "atproto repo:fm.teal.feed.play?action=create"
private const val SECRET_SENTINEL = "SYNTHETIC_PRIVATE_CALLBACK_SENTINEL"

private class ReviewFixture {
    val root = Files.createTempDirectory(File(System.getProperty("pano.test.directory")).also { it.mkdirs() }.toPath(), "review-").toFile()
    var now = 1_700_000_000_000L
    var lastState = ""
    var tokenRequests = 0
    var resourceRequests = 0
    var intercept: (suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData?)? = null
    val config = OAuthClientConfig("https://client.example.com/oauth-client-metadata.json", REVIEW_REDIRECT, REVIEW_SCOPE)
    val files = JvmVaultPersistence(root)
    val client = HttpClient(MockEngine { request ->
        intercept?.invoke(this, request)?.let { return@MockEngine it }
        when {
            request.url.encodedPath == "/xrpc/com.atproto.identity.resolveHandle" -> json("""{"did":"$REVIEW_DID"}""")
            request.url.host == "plc.directory" -> json("""{"id":"$REVIEW_DID","alsoKnownAs":["at://alice.example.com"],"service":[{"id":"#atproto_pds","type":"AtprotoPersonalDataServer","serviceEndpoint":"https://pds.example.com"}]}""")
            request.url.encodedPath == "/.well-known/oauth-protected-resource" -> json("""{"resource":"https://pds.example.com","authorization_servers":["https://auth.example.com"]}""")
            request.url.encodedPath == "/.well-known/oauth-authorization-server" -> json(REVIEW_SERVER)
            request.url.encodedPath == "/oauth/par" -> {
                lastState = formDecode((request.body as TextContent).text).getValue("state")
                json("""{"request_uri":"urn:ietf:params:oauth:request_uri:review","expires_in":300}""", 201)
            }
            request.url.encodedPath == "/oauth/token" -> {
                tokenRequests++
                json(token("review-access-$tokenRequests", "review-refresh-$tokenRequests"))
            }
            request.url.encodedPath.endsWith("createRecord") -> {
                resourceRequests++
                json("""{"uri":"at://$REVIEW_DID/fm.teal.feed.play/review","cid":"review"}""")
            }
            else -> error("Unexpected synthetic endpoint")
        }
    }) { followRedirects = false }

    fun authority(persistence: VaultPersistence = files) = AtProtoAccountAuthority(
        AtProtoAccountStore(persistence, VaultCipher.Plaintext), config,
        { AtProtoHttpClientLease(client) {} }, EpochClock { now },
    )
    fun callback(state: String = lastState) = "$REVIEW_REDIRECT?" + formEncode(mapOf("state" to state, "code" to SECRET_SENTINEL, "iss" to "https://auth.example.com"))
    suspend fun login(): AtProtoAccount {
        authority().beginAuthorization("alice.example.com")
        return authority().completeAuthorization(callback())
    }
    fun token(access: String, refresh: String) = """{"access_token":"$access","refresh_token":"$refresh","token_type":"DPoP","expires_in":120,"scope":"$REVIEW_SCOPE","sub":"$REVIEW_DID"}"""
}

private fun MockRequestHandleScope.json(body: String, status: Int = 200, nonce: String? = "review-nonce") =
    respond(body, HttpStatusCode.fromValue(status), headersOf(*buildList {
        add(HttpHeaders.ContentType to listOf("application/json"))
        if (nonce != null) add("DPoP-Nonce" to listOf(nonce))
    }.toTypedArray()))

private const val REVIEW_SERVER = """{
  "issuer":"https://auth.example.com","authorization_endpoint":"https://auth.example.com/oauth/authorize",
  "token_endpoint":"https://auth.example.com/oauth/token","pushed_authorization_request_endpoint":"https://auth.example.com/oauth/par",
  "response_types_supported":["code"],"grant_types_supported":["authorization_code","refresh_token"],
  "code_challenge_methods_supported":["S256"],"token_endpoint_auth_methods_supported":["none","private_key_jwt"],
  "token_endpoint_auth_signing_alg_values_supported":["ES256"],"scopes_supported":["atproto"],
  "dpop_signing_alg_values_supported":["ES256"],"authorization_response_iss_parameter_supported":true,
  "require_pushed_authorization_requests":true,"client_id_metadata_document_supported":true
}"""

class LifecycleReviewProbeTest {
    @Test
    fun observedMalformedCallbackLeaksCodeThroughUriSyntaxExceptionBeforeAnyExchange(): Unit = runBlocking {
        val f = ReviewFixture()
        f.authority().beginAuthorization("alice.example.com")
        val failure = assertFailsWith<java.net.URISyntaxException> {
            f.authority().completeAuthorization(f.callback() + "&unknown=|")
        }
        assertTrue(SECRET_SENTINEL in failure.stackTraceToString())
        assertEquals(0, f.tokenRequests)
        // Rejected syntax must not consume the original valid pending authorization.
        assertNotNull(f.authority().completeAuthorization(f.callback()))
        f.client.close()
    }

    @Test
    fun observedMalformedHttpErrorWithNonceQuarantinesWhereMissingNonceKeepsCredential(): Unit = runBlocking {
        for (status in listOf(400, 403)) for (hasNonce in listOf(true, false)) {
            val f = ReviewFixture()
            val initial = f.login()
            f.now += 120_000
            f.intercept = { request ->
                if (request.url.encodedPath == "/oauth/token") json("<html>temporary rejection</html>", status, if (hasNonce) "error-nonce" else null) else null
            }
            if (hasNonce) {
                assertFailsWith<OAuthResponseException> { f.authority().refresh(initial.session) }
                assertFailsWith<IncompleteSessionUpdateException> { f.authority().currentAccount() }
            } else {
                assertFailsWith<DpopNonceException> { f.authority().refresh(initial.session) }
                assertEquals(initial, f.authority().currentAccount())
            }
            f.client.close()
        }
    }

    @Test
    fun observedActualRefreshDeadlineAfter503HeadersQuarantinesUnconsumedCredential(): Unit = runBlocking {
        val f = ReviewFixture()
        val initial = f.login()
        f.now += 120_000
        val body = ByteChannel()
        f.intercept = { request ->
            if (request.url.encodedPath == "/oauth/token") respond(body, HttpStatusCode.ServiceUnavailable,
                headersOf("Content-Type" to listOf("application/json"), "DPoP-Nonce" to listOf("timeout-error-nonce"))) else null
        }
        try {
            val failure = withTimeout(45_000) {
                assertFailsWith<TimeoutCancellationException> { f.authority().refresh(initial.session) }
            }
            assertTrue(failure.message.orEmpty().contains("40000"))
            assertFailsWith<IncompleteSessionUpdateException> { f.authority().currentAccount() }
        } finally {
            body.close()
            f.client.close()
        }
    }

    @Test
    fun newerAuthorizationWinsAgainstAnOlderExchangeAndItsDetachedSessionHandle(): Unit = runBlocking {
        val f = ReviewFixture()
        val original = f.login()
        f.authority().beginAuthorization("alice.example.com")
        val oldCallback = f.callback()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var blockOnce = true
        f.intercept = { request ->
            if (request.url.encodedPath == "/oauth/token" && blockOnce) {
                blockOnce = false
                entered.complete(Unit)
                release.await()
            }
            null
        }
        val older = async { runCatching { f.authority().completeAuthorization(oldCallback) } }
        entered.await()
        f.authority().beginAuthorization("alice.example.com")
        val replacement = f.authority().completeAuthorization(f.callback())
        release.complete(Unit)
        assertTrue(older.await().exceptionOrNull() is StaleAuthorizationException)
        assertNotEquals(original.session, replacement.session)
        assertEquals(replacement, f.authority().currentAccount())
        assertFailsWith<StaleSessionException> {
            f.authority().createRecord(original.session, "fm.teal.feed.play", buildJsonObject {})
        }
        assertEquals(0, f.resourceRequests)
        f.client.close()
    }

    @Test
    fun finalWriteErrorAfterAtomicReplacementNeverReplaysOldRotatingCredential(): Unit = runBlocking {
        val f = ReviewFixture()
        val initial = f.login()
        f.now += 120_000
        var writes = 0
        val afterCommitFailure = object : VaultPersistence by f.files {
            override fun write(bytes: ByteArray) {
                f.files.write(bytes)
                if (++writes == 2) throw IOException("synthetic post-rename directory-sync acknowledgement failure")
            }
        }
        assertFailsWith<IOException> { f.authority(afterCommitFailure).refresh(initial.session) }
        // This differs from a pre-replacement failure: either marker or valid new
        // credentials may be observable after a native post-rename failure, never old usable ones.
        assertEquals(initial, f.authority().currentAccount())
        assertEquals(2, f.tokenRequests)
        f.authority().createRecord(initial.session, "fm.teal.feed.play", buildJsonObject {})
        assertEquals(2, f.tokenRequests)
        assertEquals(1, f.resourceRequests)
        f.client.close()
    }
}
