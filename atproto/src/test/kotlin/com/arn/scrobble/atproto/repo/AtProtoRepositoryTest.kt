package com.arn.scrobble.atproto.repo

import com.arn.scrobble.atproto.crypto.generateP256PrivateJwk
import com.arn.scrobble.atproto.oauth.AtProtoSession
import com.arn.scrobble.atproto.oauth.AtProtoSessionClient
import com.arn.scrobble.atproto.oauth.AtProtoSessionStore
import com.arn.scrobble.atproto.oauth.AtProtoToken
import com.arn.scrobble.atproto.oauth.AuthorizationServerMetadata
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AtProtoRepositoryTest {
    @Test
    fun `creates a record without optional repository controls`() = runTest {
        val fixture = RepositoryFixture()

        val created = fixture.repository.createRecord(
            did = fixture.did,
            collection = "fm.teal.feed.play",
            record = buildJsonObject {
                put("\$type", "fm.teal.feed.play")
                put("trackName", "A track")
            },
        )

        assertEquals("at://${fixture.did}/fm.teal.feed.play/3kexample", created.uri)
        val request = assertNotNull(fixture.lastCreateRequest)
        val body = Json.parseToJsonElement(request.bodyText()).jsonObject
        assertEquals(fixture.did, body["repo"]?.jsonPrimitive?.content)
        assertEquals("fm.teal.feed.play", body["collection"]?.jsonPrimitive?.content)
        assertFalse("rkey" in body)
        assertFalse("validate" in body)
        assertFalse("swapCommit" in body)
        assertEquals("DPoP access-1", request.headers[HttpHeaders.Authorization])
        assertNotNull(request.headers["DPoP"])
    }

    @Test
    fun `refreshes once and retries a rejected access token`() = runTest {
        val fixture = RepositoryFixture(rejectFirstAccessToken = true)

        fixture.repository.createRecord(
            did = fixture.did,
            collection = "app.rocksky.scrobble",
            record = buildJsonObject {
                put("\$type", "app.rocksky.scrobble")
                put("title", "A track")
            },
        )

        assertEquals(2, fixture.createRequests)
        assertEquals(1, fixture.refreshRequests)
        assertEquals(listOf<String?>("DPoP access-1", "DPoP access-2"), fixture.createAuthorizations)
        assertEquals("access-2", fixture.sessions.get(fixture.did)?.token?.accessToken)
    }
}

private class RepositoryFixture(private val rejectFirstAccessToken: Boolean = false) {
    val did = "did:plc:abcdefghijklmnopqrstuvwx"
    private val issuer = "https://auth.example.com"
    private val server = AuthorizationServerMetadata(
        issuer = issuer,
        authorizationEndpoint = "$issuer/oauth/authorize",
        tokenEndpoint = "$issuer/oauth/token",
        pushedAuthorizationRequestEndpoint = "$issuer/oauth/par",
    )
    val sessions = RepositorySessionStore(
        AtProtoSession(
            did = did,
            clientId = "https://app.example.com/client.json",
            handle = "alice.bsky.social",
            pds = "https://pds.example.com",
            authorizationServer = server,
            dpopKey = generateP256PrivateJwk(),
            token = AtProtoToken(
                accessToken = "access-1",
                refreshToken = "refresh-1",
                scope = "atproto repo:fm.teal.feed.play?action=create repo:app.rocksky.scrobble?action=create",
                expiresAt = Long.MAX_VALUE,
            ),
        ),
    )
    var lastCreateRequest: HttpRequestData? = null
    var createRequests = 0
    var refreshRequests = 0
    val createAuthorizations = mutableListOf<String?>()

    private val http = HttpClient(MockEngine { request ->
        when {
            request.url.encodedPath == "/xrpc/com.atproto.repo.createRecord" -> {
                createRequests += 1
                lastCreateRequest = request
                createAuthorizations += request.headers[HttpHeaders.Authorization]
                if (rejectFirstAccessToken && createRequests == 1) {
                    respond(
                        content = "{}",
                        status = HttpStatusCode.Unauthorized,
                        headers = headersOf(
                            HttpHeaders.WWWAuthenticate to listOf("DPoP error=\"invalid_token\""),
                            "DPoP-Nonce" to listOf("pds-nonce-1"),
                            HttpHeaders.ContentType to listOf("application/json"),
                        ),
                    )
                } else {
                    json(
                        "{\"uri\":\"at://$did/${requestRecordCollection(request)}/3kexample\",\"cid\":\"bafyexample\"}",
                        nonce = "pds-nonce-2",
                    )
                }
            }
            request.url.host == "public.api.bsky.app" -> json("{\"did\":\"$did\"}")
            request.url.host == "plc.directory" -> json(didDocument())
            request.url.host == "pds.example.com" -> json(
                "{\"resource\":\"https://pds.example.com\",\"authorization_servers\":[\"$issuer\"]}",
            )
            request.url.encodedPath == "/.well-known/oauth-authorization-server" -> json(serverMetadata())
            request.url.encodedPath == "/oauth/token" -> {
                refreshRequests += 1
                json(
                    """
                        {
                          "access_token":"access-2",
                          "refresh_token":"refresh-2",
                          "token_type":"DPoP",
                          "expires_in":3600,
                          "scope":"atproto repo:fm.teal.feed.play?action=create repo:app.rocksky.scrobble?action=create",
                          "sub":"$did"
                        }
                    """.trimIndent(),
                    nonce = "as-nonce-1",
                )
            }
            else -> error("Unexpected request ${request.url}")
        }
    })
    private val sessionClient = AtProtoSessionClient(http, sessions)
    val repository = AtProtoRepository(http, sessionClient)

    private fun requestRecordCollection(request: HttpRequestData): String =
        Json.parseToJsonElement(request.bodyText()).jsonObject["collection"]!!.jsonPrimitive.content

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

    private fun MockRequestHandleScope.json(body: String, nonce: String? = null) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(
            *buildList {
                add(HttpHeaders.ContentType to listOf("application/json"))
                if (nonce != null) add("DPoP-Nonce" to listOf(nonce))
            }.toTypedArray(),
        ),
    )
}

private class RepositorySessionStore(initial: AtProtoSession) : AtProtoSessionStore {
    private var session: AtProtoSession? = initial

    override suspend fun get(did: String): AtProtoSession? = session?.takeIf { it.did == did }

    override suspend fun put(session: AtProtoSession) {
        this.session = session
    }

    override suspend fun delete(did: String) {
        if (session?.did == did) session = null
    }
}

private fun HttpRequestData.bodyText(): String = when (val content = body) {
    is OutgoingContent.ByteArrayContent -> content.bytes().toString(Charsets.UTF_8)
    else -> error("Unexpected request body ${content::class}")
}
