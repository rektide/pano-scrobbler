package com.arn.scrobble.atproto.http

import com.arn.scrobble.atproto.crypto.generateP256PrivateJwk
import com.arn.scrobble.atproto.encoding.decodeBase64Url
import com.arn.scrobble.atproto.oauth.DpopProofFactory
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class DpopHttpTest {
    @Test
    fun `authorization server nonce challenge is retried once with a fresh proof`() = runTest {
        val proofs = mutableListOf<String>()
        var callCount = 0
        val client = HttpClient(MockEngine { request ->
            proofs += requireNotNull(request.headers["DPoP"])
            callCount += 1
            if (callCount == 1) {
                respond(
                    content = "{\"error\":\"use_dpop_nonce\"}",
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf("DPoP-Nonce", "authorization-nonce"),
                )
            } else {
                respond("{}", HttpStatusCode.OK)
            }
        })
        var jti = 0
        val dpop = DpopHttp(
            client,
            DpopProofFactory(generateP256PrivateJwk(), nextJti = { "jti-${++jti}" }),
        )

        val response = dpop.request(
            url = "https://auth.example.com/oauth/par",
            method = HttpMethod.Post,
            server = DpopServer.AUTHORIZATION,
        )

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(2, callCount)
        assertNotEquals(proofs[0], proofs[1])
        assertEquals(null, proofClaims(proofs[0])["nonce"])
        assertEquals("authorization-nonce", proofClaims(proofs[1])["nonce"]?.jsonPrimitive?.content)
    }

    @Test
    fun `resource nonce challenge retains DPoP authorization and ath`() = runTest {
        val proofs = mutableListOf<String>()
        val authorizations = mutableListOf<String?>()
        val client = HttpClient(MockEngine { request ->
            proofs += requireNotNull(request.headers["DPoP"])
            authorizations += request.headers[HttpHeaders.Authorization]
            if (proofs.size == 1) {
                respond(
                    content = "{}",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(
                        HttpHeaders.WWWAuthenticate to listOf("DPoP error=\"use_dpop_nonce\""),
                        "DPoP-Nonce" to listOf("resource-nonce"),
                    ),
                )
            } else {
                respond("{}", HttpStatusCode.OK)
            }
        })
        val dpop = DpopHttp(client, DpopProofFactory(generateP256PrivateJwk()))

        dpop.request(
            url = "https://pds.example.com/xrpc/com.atproto.repo.createRecord",
            method = HttpMethod.Post,
            server = DpopServer.RESOURCE,
            accessToken = "access-token",
        )

        assertEquals(listOf<String?>("DPoP access-token", "DPoP access-token"), authorizations)
        assertEquals("resource-nonce", proofClaims(proofs[1])["nonce"]?.jsonPrimitive?.content)
        assertEquals(
            proofClaims(proofs[0])["ath"]?.jsonPrimitive?.content,
            proofClaims(proofs[1])["ath"]?.jsonPrimitive?.content,
        )
    }

    @Test
    fun `a repeated nonce challenge cannot exceed the retry budget`() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls += 1
            respond(
                content = "{\"error\":\"use_dpop_nonce\"}",
                status = HttpStatusCode.BadRequest,
                headers = headersOf("DPoP-Nonce", "nonce-$calls"),
            )
        })
        val dpop = DpopHttp(client, DpopProofFactory(generateP256PrivateJwk()))

        val response = dpop.request(
            url = "https://auth.example.com/oauth/token",
            method = HttpMethod.Post,
            server = DpopServer.AUTHORIZATION,
        )

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(2, calls)
    }

    private fun proofClaims(proof: String) = Json.parseToJsonElement(
        proof.split('.')[1].decodeBase64Url().toString(Charsets.UTF_8),
    ).jsonObject

    private fun runTest(block: suspend () -> Unit) = kotlinx.coroutines.test.runTest { block() }
}
