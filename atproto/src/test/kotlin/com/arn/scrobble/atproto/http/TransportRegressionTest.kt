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
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith

class TransportRegressionTest {
    @Test
    fun `discovery rejects a redirect-capable client before any network request`() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine { request ->
            calls++
            if (request.url.host == "pds.example.com") {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://other.example.com/metadata"))
            } else {
                respond("{}", headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        })
        try {
            assertFailsWith<IllegalArgumentException> {
                client.fetchJson("https://pds.example.com/metadata", maxBytes = 64, json = Json)
            }
            assertEquals(0, calls, "Rejecting the final URL after a redirect is too late")
        } finally {
            client.close()
        }
    }

    @Test
    fun `DPoP rejects a redirect-capable client before sending credentials`() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine { request ->
            calls++
            if (request.url.host == "pds.example.com") {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://other.example.com/record"))
            } else {
                respond("{}", headers = headersOf("DPoP-Nonce", "nonce"))
            }
        })
        try {
            assertFailsWith<IllegalArgumentException> {
                DpopHttp(client, proof()).request(
                    "https://pds.example.com/record", HttpMethod.Get, DpopServer.RESOURCE, "access-token",
                )
            }
            assertEquals(0, calls)
        } finally {
            client.close()
        }
    }

    @Test
    fun `discovery stops at the byte limit without waiting for end of stream`() = runTest {
        val channel = ByteChannel(autoFlush = true)
        backgroundScope.launch { channel.writeFully(ByteArray(9) { ' '.code.toByte() }) }
        val client = HttpClient(MockEngine {
            respond(channel, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { followRedirects = false }
        try {
            assertFailsWith<IllegalArgumentException> {
                withContext(Dispatchers.Default) {
                    withTimeout(5_000) {
                        client.fetchJson("https://pds.example.com/metadata", maxBytes = 8, json = Json)
                    }
                }
            }
        } finally {
            channel.cancel(null)
            client.close()
        }
    }

    @Test
    fun `discovery rejects invalid UTF8 instead of silently replacing it`() = runTest {
        val bytes = "{\"name\":\"".toByteArray() + byteArrayOf(0xc3.toByte(), 0x28) + "\"}".toByteArray()
        val client = HttpClient(MockEngine {
            respond(ByteReadChannel(bytes), headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { followRedirects = false }
        try {
            assertFails { client.fetchJson("https://pds.example.com/metadata", maxBytes = 64, json = Json) }
        } finally {
            client.close()
        }
    }

    @Test
    fun `DPoP requires one valid nonce even on success`() = runTest {
        for (values in listOf(emptyList(), listOf(""), listOf("first", "second"), listOf("has space"))) {
            val client = HttpClient(MockEngine {
                respond("{}", headers = headersOf("DPoP-Nonce", values))
            }) { followRedirects = false }
            try {
                assertFailsWith<IllegalArgumentException>("nonce headers: $values") {
                    DpopHttp(client, proof()).request("https://auth.example.com/token", HttpMethod.Post, DpopServer.AUTHORIZATION)
                }
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `a repeated nonce value still permits one fresh-proof retry`() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            respond(
                if (calls == 2) "{\"error\":\"use_dpop_nonce\"}" else "{}",
                if (calls == 2) HttpStatusCode.BadRequest else HttpStatusCode.OK,
                headersOf("DPoP-Nonce", "stable-nonce"),
            )
        }) { followRedirects = false }
        try {
            val dpop = DpopHttp(client, proof())
            dpop.request("https://auth.example.com/token", HttpMethod.Post, DpopServer.AUTHORIZATION)
            val response = dpop.request("https://auth.example.com/token", HttpMethod.Post, DpopServer.AUTHORIZATION)
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(3, calls)
        } finally {
            client.close()
        }
    }

    @Test
    fun `nonce challenge parsing recognizes separate schemes and token parameter syntax`() = runTest {
        var calls = 0
        val client = HttpClient(MockEngine {
            calls++
            respond(
                "{}",
                if (calls == 1) HttpStatusCode.Unauthorized else HttpStatusCode.OK,
                headersOf(
                    "DPoP-Nonce" to listOf("nonce-$calls"),
                    HttpHeaders.WWWAuthenticate to listOf("Bearer realm=\"other\", DPoP realm=\"pds, music\", error = use_dpop_nonce"),
                ),
            )
        }) { followRedirects = false }
        try {
            val response = DpopHttp(client, proof()).request("https://pds.example.com/record", HttpMethod.Post, DpopServer.RESOURCE, "token")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(2, calls)
        } finally {
            client.close()
        }
    }

    @Test
    fun `a lookalike auth scheme or error description must not trigger a write retry`() = runTest {
        for (challenge in listOf(
            "DPoPish error=\"use_dpop_nonce\"",
            "DPoP error_description=\"example error=\\\"use_dpop_nonce\\\"\"",
            "DPoP error=\"USE_DPOP_NONCE\"",
        )) {
            var calls = 0
            val client = HttpClient(MockEngine {
                calls++
                respond("{}", HttpStatusCode.Unauthorized, headersOf(
                    "DPoP-Nonce" to listOf("nonce-$calls"),
                    HttpHeaders.WWWAuthenticate to listOf(challenge),
                ))
            }) { followRedirects = false }
            try {
                DpopHttp(client, proof()).request("https://pds.example.com/record", HttpMethod.Post, DpopServer.RESOURCE, "token")
                assertEquals(1, calls, challenge)
            } finally {
                client.close()
            }
        }
    }

    @Test
    fun `AS and resource nonces at the same origin are distinct contexts`() = runTest {
        val seen = mutableListOf<String?>()
        val client = HttpClient(MockEngine { request ->
            seen += Json.parseToJsonElement(request.headers["DPoP"]!!.split('.')[1].decodeBase64Url().decodeToString())
                .jsonObject["nonce"]?.jsonPrimitive?.content
            respond("{}", headers = headersOf("DPoP-Nonce", "nonce-${seen.size}"))
        }) { followRedirects = false }
        try {
            val dpop = DpopHttp(client, proof())
            dpop.request("https://same.example.com/token", HttpMethod.Post, DpopServer.AUTHORIZATION)
            dpop.request("https://same.example.com/record", HttpMethod.Post, DpopServer.RESOURCE, "token")
            dpop.request("https://same.example.com/token", HttpMethod.Post, DpopServer.AUTHORIZATION)
            assertEquals(listOf(null, null, "nonce-1"), seen)
        } finally {
            client.close()
        }
    }

    private fun proof() = DpopProofFactory(generateP256PrivateJwk())
}
