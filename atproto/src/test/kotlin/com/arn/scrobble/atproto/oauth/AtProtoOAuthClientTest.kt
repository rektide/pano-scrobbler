package com.arn.scrobble.atproto.oauth

import com.arn.scrobble.atproto.crypto.generateP256PrivateJwk
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AtProtoOAuthClientTest {
    @Test
    fun `begins authorization with a parameterized client id and form encoded PAR`() = runTest {
        val fixture = OAuthFixture()

        val launch = fixture.client.beginAuthorization("alice.bsky.social")

        val request = assertNotNull(fixture.parRequest)
        val form = formDecode(request.bodyText())
        assertEquals(fixture.config.clientId, form["client_id"])
        assertEquals(fixture.config.redirectUri, form["redirect_uri"])
        assertEquals("S256", form["code_challenge_method"])
        assertEquals("fragment", form["response_mode"])
        assertEquals(
            "application/x-www-form-urlencoded",
            request.body.contentType?.withoutParameters()?.toString(),
        )
        assertNotNull(request.headers["DPoP"])

        val authorizationQuery = formDecode(launch.authorizationUrl.substringAfter('?'))
        assertEquals(setOf("client_id", "request_uri"), authorizationQuery.keys)
        assertEquals(fixture.config.clientId, authorizationQuery["client_id"])
        assertNotNull(fixture.states.peek(fixture.state))
    }

    @Test
    fun `completes authorization verifies identity and persists the session`() = runTest {
        val fixture = OAuthFixture()
        fixture.client.beginAuthorization("alice.bsky.social")

        val session = fixture.client.completeAuthorization(
            OAuthCallback(
                state = fixture.state,
                code = "authorization-code",
                issuer = fixture.issuer,
                error = null,
                errorDescription = null,
            ),
        )

        assertEquals(fixture.did, session.did)
        assertEquals("alice.bsky.social", session.handle)
        assertEquals("access-1", session.token.accessToken)
        assertEquals("refresh-1", session.token.refreshToken)
        assertEquals(session, fixture.sessions.get(fixture.did))
        assertEquals(null, fixture.states.peek(fixture.state))

        val tokenForm = formDecode(assertNotNull(fixture.authorizationCodeRequest).bodyText())
        assertEquals("authorization_code", tokenForm["grant_type"])
        assertEquals("authorization-code", tokenForm["code"])
        assertEquals(fixture.config.clientId, tokenForm["client_id"])
        assertTrue(tokenForm["code_verifier"]?.length in 43..128)
    }

    @Test
    fun `callback state is consumed before an issuer mismatch is rejected`() = runTest {
        val fixture = OAuthFixture()
        fixture.client.beginAuthorization("alice.bsky.social")

        assertFailsWith<IllegalArgumentException> {
            fixture.client.completeAuthorization(
                OAuthCallback(fixture.state, "code", "https://evil.example", null, null),
            )
        }

        assertEquals(null, fixture.states.peek(fixture.state))
        assertFailsWith<AtProtoOAuthException> {
            fixture.client.completeAuthorization(
                OAuthCallback(fixture.state, "code", fixture.issuer, null, null),
            )
        }
    }

    @Test
    fun `concurrent expiry checks perform one refresh and atomically rotate tokens`() = runTest {
        val fixture = OAuthFixture()
        fixture.client.beginAuthorization("alice.bsky.social")
        fixture.client.completeAuthorization(
            OAuthCallback(fixture.state, "authorization-code", fixture.issuer, null, null),
        )
        fixture.clock.advanceSeconds(3_550)

        val results = coroutineScope {
            List(5) { async { fixture.client.getSession(fixture.did) } }.awaitAll()
        }

        assertEquals(1, fixture.refreshRequests)
        assertTrue(results.all { it.token.accessToken == "access-2" })
        assertTrue(results.all { it.token.refreshToken == "refresh-1" })
        assertEquals("access-2", fixture.sessions.get(fixture.did)?.token?.accessToken)
    }

    @Test
    fun `revocation is best effort and always removes the local session`() = runTest {
        val fixture = OAuthFixture()
        fixture.client.beginAuthorization("alice.bsky.social")
        fixture.client.completeAuthorization(
            OAuthCallback(fixture.state, "authorization-code", fixture.issuer, null, null),
        )

        fixture.client.revokeSession(fixture.did)

        assertEquals("refresh-1", formDecode(assertNotNull(fixture.revocationRequest).bodyText())["token"])
        assertEquals(null, fixture.sessions.get(fixture.did))
    }
}

private class OAuthFixture {
    val did = "did:plc:abcdefghijklmnopqrstuvwx"
    val issuer = "https://auth.example.com"
    val state = "fixed-state-value-123456"
    val config = OAuthClientConfig(
        clientId = "https://app.example.com/pano-client-metadata.json",
        redirectUri = "com.example.app:/oauth/callback",
        scope = "atproto repo:fm.teal.feed.play?action=create repo:app.rocksky.scrobble?action=create",
        responseMode = OAuthResponseMode.FRAGMENT,
    )
    val states = MemoryAuthorizationStateStore()
    val sessions = MemoryAtProtoSessionStore()
    val clock = MutableClock(Instant.ofEpochSecond(1_800_000_000))
    var parRequest: HttpRequestData? = null
    var authorizationCodeRequest: HttpRequestData? = null
    var revocationRequest: HttpRequestData? = null
    var refreshRequests = 0

    private val http = HttpClient(MockEngine { request ->
        when {
            request.url.host == "public.api.bsky.app" -> json("{\"did\":\"$did\"}")
            request.url.host == "plc.directory" -> json(didDocument())
            request.url.host == "pds.example.com" -> json(
                "{\"resource\":\"https://pds.example.com\",\"authorization_servers\":[\"$issuer\"]}",
            )
            request.url.encodedPath == "/.well-known/oauth-authorization-server" -> json(serverMetadata())
            request.url.encodedPath == "/oauth/par" -> {
                parRequest = request
                json(
                    "{\"request_uri\":\"urn:ietf:params:oauth:request_uri:request-1\",\"expires_in\":300}",
                    status = HttpStatusCode.Created,
                    nonce = "as-nonce-1",
                )
            }
            request.url.encodedPath == "/oauth/token" -> {
                val form = formDecode(request.bodyText())
                if (form["grant_type"] == "refresh_token") {
                    refreshRequests += 1
                    json(tokenResponse("access-2", refresh = null), nonce = "as-nonce-3")
                } else {
                    authorizationCodeRequest = request
                    json(tokenResponse("access-1", refresh = "refresh-1"), nonce = "as-nonce-2")
                }
            }
            request.url.encodedPath == "/oauth/revoke" -> {
                revocationRequest = request
                json("{}", nonce = "as-nonce-4")
            }
            else -> error("Unexpected request ${request.url}")
        }
    })

    val client = AtProtoOAuthClient(
        httpClient = http,
        config = config,
        authorizationStates = states,
        sessions = sessions,
        discovery = OAuthDiscovery(http),
        clock = clock,
        nextState = { state },
        generateKey = ::generateP256PrivateJwk,
        json = Json { ignoreUnknownKeys = true },
    )

    private fun didDocument() = """
        {
          "id":"$did",
          "alsoKnownAs":["at://alice.bsky.social"],
          "service":[{
            "id":"#atproto_pds",
            "type":"AtprotoPersonalDataServer",
            "serviceEndpoint":"https://pds.example.com"
          }]
        }
    """.trimIndent()

    private fun serverMetadata() = """
        {
          "issuer":"$issuer",
          "authorization_endpoint":"$issuer/oauth/authorize",
          "token_endpoint":"$issuer/oauth/token",
          "pushed_authorization_request_endpoint":"$issuer/oauth/par",
          "revocation_endpoint":"$issuer/oauth/revoke",
          "protected_resources":["https://pds.example.com"],
          "response_types_supported":["code"],
          "grant_types_supported":["authorization_code","refresh_token"],
          "code_challenge_methods_supported":["S256"],
          "token_endpoint_auth_methods_supported":["none","private_key_jwt"],
          "token_endpoint_auth_signing_alg_values_supported":["ES256"],
          "scopes_supported":["atproto"],
          "dpop_signing_alg_values_supported":["ES256"],
          "authorization_response_iss_parameter_supported":true,
          "require_pushed_authorization_requests":true,
          "client_id_metadata_document_supported":true
        }
    """.trimIndent()

    private fun tokenResponse(access: String, refresh: String?) = """
        {
          "access_token":"$access",
          ${if (refresh == null) "" else "\"refresh_token\":\"$refresh\","}
          "token_type":"DPoP",
          "expires_in":3600,
          "scope":"${config.scope}",
          "sub":"$did"
        }
    """.trimIndent()

    private fun MockRequestHandleScope.json(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        nonce: String? = null,
    ) = respond(
        content = body,
        status = status,
        headers = headersOf(
            *buildList {
                add(HttpHeaders.ContentType to listOf("application/json"))
                if (nonce != null) add("DPoP-Nonce" to listOf(nonce))
            }.toTypedArray(),
        ),
    )
}

private class MemoryAuthorizationStateStore : AuthorizationStateStore {
    private val values = mutableMapOf<String, PendingAuthorization>()

    override suspend fun put(authorization: PendingAuthorization) {
        check(values.putIfAbsent(authorization.state, authorization) == null)
    }

    override suspend fun take(state: String): PendingAuthorization? = values.remove(state)

    fun peek(state: String) = values[state]
}

private class MemoryAtProtoSessionStore : AtProtoSessionStore {
    private val values = mutableMapOf<String, AtProtoSession>()

    override suspend fun get(did: String): AtProtoSession? = values[did]

    override suspend fun put(session: AtProtoSession) {
        values[session.did] = session
    }

    override suspend fun delete(did: String) {
        values.remove(did)
    }
}

private class MutableClock(private var now: Instant) : Clock() {
    override fun getZone(): ZoneId = ZoneId.of("UTC")

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = now

    fun advanceSeconds(seconds: Long) {
        now = now.plusSeconds(seconds)
    }
}

private fun HttpRequestData.bodyText(): String = when (val content = body) {
    is OutgoingContent.ByteArrayContent -> content.bytes().toString(Charsets.UTF_8)
    else -> error("Unexpected request body ${content::class}")
}
