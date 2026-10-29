package com.arn.scrobble.api.atproto

// New composition probe on97db5e70, not a repetition of the frozen engine suite.
// Session: opencode://ses_e7af14a6cffeDevLvL0joxsto6.

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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

private const val DID = "did:plc:abcdefghijklmnopqrstuvwx"

class AtProtoFinalEngineCompositionProbeTest {
    @Test
    fun resourceNonceThenInvalidTokenRefresh503IsSingleAttemptAndLaterNarrowedGrantCannotResend(): Unit = runBlocking {
        data class Wire(val path: String, val body: String, val proof: String, val authorization: String?)
        val wire = CopyOnWriteArrayList<Wire>()
        var state = ""
        var tokenCalls = 0
        var resourceCalls = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            val path = exchange.requestURI.path
            val body = exchange.requestBody.readAllBytes().decodeToString()
            wire += Wire(path, body, exchange.requestHeaders.getFirst("DPoP"), exchange.requestHeaders.getFirst("Authorization"))
            var status = 200
            var nonce = "par-nonce"
            val response = when (path) {
                "/par" -> {
                    state = form(body).getValue("state")
                    status = 201
                    """{"request_uri":"urn:fixture:request","expires_in":300}"""
                }
                "/token" -> {
                    tokenCalls++
                    nonce = "as-$tokenCalls"
                    if (tokenCalls == 2) {
                        status = 503
                        exchange.responseHeaders.add("Retry-After", "0")
                        """{"error":"temporarily_unavailable"}"""
                    } else {
                        // Omitted expiry keeps the first resource path from proactively refreshing.
                        val scope = if (tokenCalls == 1) "atproto repo:fm.teal.feed.play?action=create" else "atproto"
                        """{"access_token":"access-$tokenCalls","refresh_token":"refresh-$tokenCalls","token_type":"DPoP","scope":"$scope","sub":"$DID"}"""
                    }
                }
                "/xrpc/com.atproto.repo.createRecord" -> {
                    resourceCalls++
                    nonce = "pds-$resourceCalls"
                    status = 401
                    exchange.responseHeaders.add("WWW-Authenticate", "DPoP error=\"${if (resourceCalls == 1) "use_dpop_nonce" else "invalid_token"}\"")
                    "{}"
                }
                else -> error("Unexpected synthetic wire request")
            }
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.responseHeaders.add("DPoP-Nonce", nonce)
            exchange.sendResponseHeaders(status, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
            exchange.close()
        }
        server.start()
        val client = HttpClient(OkHttp) {
            install(HttpTimeout) { requestTimeoutMillis = 5_000 }
            atProtoCredentialPolicy()
            engine {
                addInterceptor { chain ->
                    val request = chain.request()
                    if (request.method == "GET") {
                        val body = when (request.url.toString()) {
                            "https://plc.directory/did%3Aplc%3Aabcdefghijklmnopqrstuvwx" ->
                                """{"id":"$DID","service":[{"id":"#atproto_pds","type":"AtprotoPersonalDataServer","serviceEndpoint":"https://pds.example"}]}"""
                            "https://pds.example/.well-known/oauth-protected-resource" ->
                                """{"resource":"https://pds.example","authorization_servers":["https://pds.example"]}"""
                            "https://pds.example/.well-known/oauth-authorization-server" -> SERVER
                            else -> throw IOException("Unexpected synthetic discovery request")
                        }
                        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                            .header("Content-Type", "application/json").body(body.toResponseBody("application/json".toMediaType())).build()
                    } else {
                        if (request.method != "POST" || request.url.host != "pds.example") throw IOException("Unexpected synthetic credential request")
                        chain.proceed(request.newBuilder().url("http://127.0.0.1:${server.address.port}${request.url.encodedPath}").build())
                    }
                }
            }
        }
        val directory = Files.createTempDirectory(File("build/final-review-vaults").also { it.mkdirs() }.toPath(), "engine-").toFile()
        val authority = AtProtoAccountAuthority(AtProtoAccountStore(JvmVaultPersistence(directory), VaultCipher.Plaintext),
            OAuthClientConfig("https://client.example/metadata.json", "example.client:/callback", "atproto repo:fm.teal.feed.play?action=create"),
            { AtProtoHttpClientLease(client) {} })
        try {
            authority.beginAuthorization(DID)
            val account = authority.completeAuthorization("example.client:/callback?state=$state&code=synthetic&iss=https%3A%2F%2Fpds.example")
            val record = buildJsonObject { put("\$type", "fm.teal.feed.play") }
            val failure = assertFailsWith<OAuthResponseException> { authority.createRecord(account.session, "fm.teal.feed.play", record) }
            assertEquals(503, failure.status)
            assertEquals(2, tokenCalls, "One initial exchange and one failed forced refresh, no engine replay")
            assertEquals(2, resourceCalls, "One protocol nonce retry, no resend after failed refresh")
            assertNotNull(authority.currentAccount(), "Known503 must not quarantine the unconsumed refresh")
            assertFailsWith<IllegalArgumentException> { authority.createRecord(account.session, "fm.teal.feed.play", record) }
            assertEquals(3, tokenCalls)
            assertEquals(3, resourceCalls, "Fresh narrowed grant stops a second resource attempt")
            val tokens = wire.filter { it.path == "/token" }
            assertEquals(listOf("refresh-1", "refresh-1"), tokens.drop(1).map { form(it.body)["refresh_token"] })
            assertEquals("as-2", payload(tokens.last().proof).getValue("nonce").jsonPrimitive.content)
            val resources = wire.filter { it.path.endsWith("createRecord") }
            assertEquals(listOf(null, "pds-1", "pds-2"), resources.map { payload(it.proof)["nonce"]?.jsonPrimitive?.content })
            assertEquals(List(3) { "DPoP access-1" }, resources.map { it.authorization })
            assertEquals(wire.size, wire.map { payload(it.proof).getValue("jti") }.distinct().size, "Every actual call has a fresh proof")
        } finally {
            client.close()
            server.stop(0)
        }
    }
}

private fun form(body: String) = body.split('&').associate {
    URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
}
private fun payload(proof: String) = Json.parseToJsonElement(Base64.getUrlDecoder().decode(proof.split('.')[1]).decodeToString()).jsonObject

private const val SERVER = """{
    "issuer":"https://pds.example","authorization_endpoint":"https://pds.example/authorize",
    "token_endpoint":"https://pds.example/token","pushed_authorization_request_endpoint":"https://pds.example/par",
    "response_types_supported":["code"],"grant_types_supported":["authorization_code","refresh_token"],
    "code_challenge_methods_supported":["S256"],"token_endpoint_auth_methods_supported":["none","private_key_jwt"],
    "token_endpoint_auth_signing_alg_values_supported":["ES256"],"scopes_supported":["atproto"],
    "dpop_signing_alg_values_supported":["ES256"],"authorization_response_iss_parameter_supported":true,
    "require_pushed_authorization_requests":true,"client_id_metadata_document_supported":true
}"""
