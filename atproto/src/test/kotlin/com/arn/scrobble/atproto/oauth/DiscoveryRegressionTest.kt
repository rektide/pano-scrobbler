package com.arn.scrobble.atproto.oauth

import com.arn.scrobble.atproto.identity.ResolvedIdentity
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DiscoveryRegressionTest {
    private val identity = ResolvedIdentity("did:plc:abcdefghijklmnopqrstuvwx", null, "https://pds.example.com")
    private val issuer = "https://auth.example.com"
    private val server = Json.parseToJsonElement("""{
        "issuer":"$issuer", "authorization_endpoint":"https://authorize.example.com/authorize?language=en",
        "token_endpoint":"https://tokens.example.com:8443/token", "pushed_authorization_request_endpoint":"https://par.example.com/par",
        "response_types_supported":["code"], "grant_types_supported":["authorization_code","refresh_token"],
        "code_challenge_methods_supported":["S256"], "token_endpoint_auth_methods_supported":["none","private_key_jwt"],
        "token_endpoint_auth_signing_alg_values_supported":["ES256"], "scopes_supported":["atproto"],
        "dpop_signing_alg_values_supported":["ES256"], "authorization_response_iss_parameter_supported":true,
        "require_pushed_authorization_requests":true, "client_id_metadata_document_supported":true
    }""").jsonObject

    @Test fun allowsDistinctHttpsEndpointOriginsAndOmittedResourceList(): Unit = runBlocking {
        val result = discovery(server).resolve(identity)
        assertEquals("https://tokens.example.com:8443/token", result.authorizationServer.tokenEndpoint)
        assertEquals("https://authorize.example.com/authorize?language=en", result.authorizationServer.authorizationEndpoint)
    }

    @Test fun rejectsEveryMissingMandatoryCapability(): Unit = runBlocking {
        for (field in listOf("response_types_supported", "grant_types_supported", "code_challenge_methods_supported",
            "token_endpoint_auth_methods_supported", "token_endpoint_auth_signing_alg_values_supported", "scopes_supported",
            "dpop_signing_alg_values_supported", "authorization_response_iss_parameter_supported", "require_pushed_authorization_requests",
            "client_id_metadata_document_supported", "authorization_endpoint", "token_endpoint", "pushed_authorization_request_endpoint", "issuer"))
            assertFailsWith<IllegalArgumentException>(field) { discovery(JsonObject(server - field)).resolve(identity) }
        for ((field, values) in mapOf("response_types_supported" to listOf("token"), "grant_types_supported" to listOf("authorization_code"),
            "code_challenge_methods_supported" to listOf("plain"), "token_endpoint_auth_methods_supported" to listOf("none"),
            "token_endpoint_auth_signing_alg_values_supported" to listOf("ES256", "none"), "scopes_supported" to listOf("transition:generic"),
            "dpop_signing_alg_values_supported" to listOf("ES256", "none")))
            assertFailsWith<IllegalArgumentException>(field) { discovery(JsonObject(server + (field to JsonArray(values.map(::JsonPrimitive))))).resolve(identity) }
        for (field in listOf("authorization_response_iss_parameter_supported", "require_pushed_authorization_requests", "client_id_metadata_document_supported", "require_request_uri_registration"))
            assertFailsWith<IllegalArgumentException>(field) { discovery(JsonObject(server + (field to JsonPrimitive(false)))).resolve(identity) }
    }

    @Test fun bindsPdsResourceAndIssuerExactly(): Unit = runBlocking {
        for (servers in listOf(emptyList(), listOf(issuer, issuer), listOf("$issuer/"), listOf("$issuer:443"), listOf("$issuer/path"), listOf("$issuer?x=y")))
            assertFailsWith<IllegalArgumentException>(servers.toString()) { discovery(server, issuers = servers).resolve(identity) }
        assertFailsWith<IllegalArgumentException> { discovery(server, resource = "https://pds.example.com/").resolve(identity) }
        assertFailsWith<IllegalArgumentException> { discovery(JsonObject(server + ("issuer" to JsonPrimitive("https://other.example.com")))).resolve(identity) }
        assertFailsWith<IllegalArgumentException> { discovery(JsonObject(server + ("protected_resources" to JsonArray(listOf(JsonPrimitive("https://other.example.com")))))).resolve(identity) }
    }

    @Test fun rejectsAmbiguousEndpointUrlsAndNon200Discovery(): Unit = runBlocking {
        for (endpoint in listOf("http://tokens.example.com/token", "https://user@tokens.example.com/token", "https://tokens.example.com/token#x", "https://tokens.example.com:/token", "https://tokens.example.com:65536/token"))
            assertFailsWith<IllegalArgumentException>(endpoint) { discovery(JsonObject(server + ("token_endpoint" to JsonPrimitive(endpoint)))).resolve(identity) }
        for (status in listOf(HttpStatusCode.Created, HttpStatusCode.NoContent, HttpStatusCode.Found))
            assertFailsWith<IllegalArgumentException>(status.toString()) { discovery(server, status = status).resolve(identity) }
    }

    private fun discovery(server: JsonObject, issuers: List<String> = listOf(issuer), resource: String = identity.pds, status: HttpStatusCode = HttpStatusCode.OK): OAuthDiscovery {
        val client = HttpClient(MockEngine { request ->
            val body = when (request.url.host) {
                "pds.example.com" -> JsonObject(mapOf("resource" to JsonPrimitive(resource), "authorization_servers" to JsonArray(issuers.map(::JsonPrimitive)))).toString()
                "auth.example.com" -> server.toString()
                else -> error("Unexpected discovery URL")
            }
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        }) { followRedirects = false }
        return OAuthDiscovery(client)
    }
}
