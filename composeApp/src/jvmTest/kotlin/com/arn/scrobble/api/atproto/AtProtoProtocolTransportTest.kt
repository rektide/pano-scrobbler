package com.arn.scrobble.api.atproto

import com.arn.scrobble.atproto.http.AtProtoHttpClientLease
import com.arn.scrobble.atproto.oauth.AtProtoAccountAuthority
import com.arn.scrobble.atproto.oauth.OAuthClientConfig
import com.arn.scrobble.atproto.oauth.OAuthResponseException
import com.arn.scrobble.atproto.storage.AtProtoAccountStore
import com.arn.scrobble.atproto.storage.JvmVaultPersistence
import com.arn.scrobble.atproto.storage.VaultCipher
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val DID = "did:plc:abcdefghijklmnopqrstuvwx"

class AtProtoProtocolTransportTest {
    @Test
    fun realProtocolNonceRetryIsANewEngineCallWithFreshProofAndBoundedBudget(): Unit = runBlocking {
        for (challengeAgain in listOf(false, true)) {
            val bodies = CopyOnWriteArrayList<String>()
            val proofs = CopyOnWriteArrayList<String>()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/par") { exchange ->
                bodies += exchange.requestBody.readAllBytes().decodeToString()
                proofs += exchange.requestHeaders.getFirst("DPoP")
                val challenged = bodies.size == 1 || challengeAgain
                val body = if (challenged) """{"error":"use_dpop_nonce"}"""
                    else """{"request_uri":"urn:ietf:params:oauth:request_uri:fixture","expires_in":300}"""
                exchange.responseHeaders.add("DPoP-Nonce", if (bodies.size == 1) "challenge-nonce" else "next-nonce")
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(if (challenged) 400 else 201, body.length.toLong())
                exchange.responseBody.use { it.write(body.toByteArray()) }
                exchange.close()
            }
            server.start()
            val client = HttpClient(OkHttp) {
                install(HttpTimeout) { requestTimeoutMillis = 5_000 }
                atProtoCredentialPolicy()
                engine {
                    // Only discovery is synthetic. Actual PAR POSTs traverse the real engine/socket.
                    // Map its validated HTTPS logical endpoint to loopback; never contact public hosts.
                    addInterceptor { chain ->
                        val request = chain.request()
                        if (request.method == "GET") {
                            val body = when (request.url.toString()) {
                                "https://plc.directory/did%3Aplc%3Aabcdefghijklmnopqrstuvwx" ->
                                    """{"id":"$DID","service":[{"id":"#atproto_pds","type":"AtprotoPersonalDataServer","serviceEndpoint":"https://pds.example"}]}"""
                                "https://pds.example/.well-known/oauth-protected-resource" ->
                                    """{"resource":"https://pds.example","authorization_servers":["https://as.example"]}"""
                                "https://as.example/.well-known/oauth-authorization-server" -> SERVER
                                else -> throw IOException("Unexpected synthetic discovery request")
                            }
                            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                                .header("Content-Type", "application/json")
                                .body(body.toResponseBody("application/json".toMediaType())).build()
                        } else {
                            if (request.method != "POST" || request.url.toString() != "https://as.example/oauth/par")
                                throw IOException("Unexpected synthetic credential request")
                            chain.proceed(request.newBuilder().url("http://127.0.0.1:${server.address.port}/par").build())
                        }
                    }
                }
            }
            val fixtures = File("build/test-fixtures/atproto-transport").also { it.mkdirs() }
            val directory = Files.createTempDirectory(fixtures.toPath(), "nonce-").toFile()
            val store = AtProtoAccountStore(JvmVaultPersistence(directory), VaultCipher.Plaintext)
            val authority = AtProtoAccountAuthority(store,
                OAuthClientConfig("https://client.example/oauth-client-metadata.json", "example.client:/callback", "atproto"),
                { AtProtoHttpClientLease(client) {} })
            try {
                if (challengeAgain) {
                    val failure = assertFailsWith<OAuthResponseException> { authority.beginAuthorization(DID) }
                    assertEquals(400, failure.status)
                    assertEquals("use_dpop_nonce", failure.error)
                } else {
                    assertTrue(authority.beginAuthorization(DID).authorizationUrl.contains("request_uri="))
                }
                assertEquals(2, bodies.size)
                assertEquals(bodies[0], bodies[1], "Explicit retry keeps the same PAR payload")
                assertEquals(2, proofs.size)
                assertNotEquals(proofs[0], proofs[1])
                val payloads = proofs.map { Json.parseToJsonElement(Base64.getUrlDecoder().decode(it.split('.')[1]).decodeToString()).jsonObject }
                assertNotEquals(payloads[0].getValue("jti"), payloads[1].getValue("jti"))
                assertNull(payloads[0]["nonce"])
                assertEquals("challenge-nonce", payloads[1].getValue("nonce").jsonPrimitive.content)
                assertTrue(payloads.all { it.getValue("htu").jsonPrimitive.content == "https://as.example/oauth/par" &&
                    it.getValue("htm").jsonPrimitive.content == "POST" })
                val headers = proofs.map { Json.parseToJsonElement(Base64.getUrlDecoder().decode(it.split('.')[0]).decodeToString()).jsonObject }
                assertEquals(headers[0].getValue("jwk"), headers[1].getValue("jwk"))
            } finally {
                client.close()
                server.stop(0)
            }
        }
    }
}

private const val SERVER = """{
    "issuer":"https://as.example","authorization_endpoint":"https://as.example/oauth/authorize",
    "token_endpoint":"https://as.example/oauth/token","pushed_authorization_request_endpoint":"https://as.example/oauth/par",
    "response_types_supported":["code"],"grant_types_supported":["authorization_code","refresh_token"],
    "code_challenge_methods_supported":["S256"],"token_endpoint_auth_methods_supported":["none","private_key_jwt"],
    "token_endpoint_auth_signing_alg_values_supported":["ES256"],"scopes_supported":["atproto"],
    "dpop_signing_alg_values_supported":["ES256"],"authorization_response_iss_parameter_supported":true,
    "require_pushed_authorization_requests":true,"require_request_uri_registration":true,"client_id_metadata_document_supported":true
}"""
