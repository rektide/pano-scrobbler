package com.arn.scrobble.atproto.oauth

import com.arn.scrobble.atproto.identity.IdentityResolver
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DiscoveryTest {
    @Test
    fun `resolves the identity PDS and conformant authorization server`() = runTest {
        val client = discoveryClient(validServerMetadata())

        val result = OAuthDiscovery(client, IdentityResolver(client)).resolve("alice.bsky.social")

        assertEquals("https://pds.example.com", result.identity.pds)
        assertEquals("https://auth.example.com", result.authorizationServer.issuer)
        assertEquals("https://auth.example.com/oauth/token", result.authorizationServer.tokenEndpoint)
    }

    @Test
    fun `rejects authorization metadata without mandatory PAR`() = runTest {
        val invalid = validServerMetadata().replace(
            "\"require_pushed_authorization_requests\":true",
            "\"require_pushed_authorization_requests\":false",
        )
        val discovery = OAuthDiscovery(discoveryClient(invalid))

        assertFailsWith<IllegalArgumentException> { discovery.resolve("alice.bsky.social") }
    }

    @Test
    fun `rejects a protected-resource mismatch`() = runTest {
        val client = discoveryClient(
            validServerMetadata(),
            protectedResource = "https://other.example.com",
        )

        assertFailsWith<IllegalArgumentException> { OAuthDiscovery(client).resolve("alice.bsky.social") }
    }

    private fun discoveryClient(
        serverMetadata: String,
        protectedResource: String = "https://pds.example.com",
    ) = HttpClient(MockEngine { request ->
        val body = when {
            request.url.host == "public.api.bsky.app" -> "{\"did\":\"did:plc:abcdefghijklmnopqrstuvwx\"}"
            request.url.host == "plc.directory" -> """
                {
                  "id":"did:plc:abcdefghijklmnopqrstuvwx",
                  "alsoKnownAs":["at://alice.bsky.social"],
                  "service":[{
                    "id":"#atproto_pds",
                    "type":"AtprotoPersonalDataServer",
                    "serviceEndpoint":"https://pds.example.com"
                  }]
                }
            """.trimIndent()
            request.url.host == "pds.example.com" -> """
                {"resource":"$protectedResource","authorization_servers":["https://auth.example.com"]}
            """.trimIndent()
            request.url.host == "auth.example.com" -> serverMetadata
            else -> error("Unexpected URL ${request.url}")
        }
        respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }) { followRedirects = false }

    private fun validServerMetadata() = """
        {
          "issuer":"https://auth.example.com",
          "authorization_endpoint":"https://auth.example.com/oauth/authorize",
          "token_endpoint":"https://auth.example.com/oauth/token",
          "pushed_authorization_request_endpoint":"https://auth.example.com/oauth/par",
          "revocation_endpoint":"https://auth.example.com/oauth/revoke",
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
}
