package com.arn.scrobble.api.atproto

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Positive regression for the frozen R3 real-wire observation, not a mock engine. */
class AtProtoTransportReplayTest {
    @Test
    fun oneShotWrapperDelegatesMetadataAndWritesDirectlyWithoutBuffering() {
        for (duplex in listOf(false, true)) {
            for (length in listOf(-1L, 4L)) {
                val destination = Buffer()
                val mediaType = "application/octet-stream".toMediaType()
                var writes = 0
                val delegate = object : RequestBody() {
                    override fun contentType() = mediaType
                    override fun contentLength() = length
                    override fun isDuplex() = duplex
                    override fun writeTo(sink: BufferedSink) {
                        assertSame(destination, sink)
                        writes++
                        sink.write(byteArrayOf(0, 1, -1, 127))
                    }
                }
                val body = AtProtoOneShotRequestBody(delegate)
                assertEquals(0, writes)
                assertSame(mediaType, body.contentType())
                assertEquals(length, body.contentLength())
                assertEquals(duplex, body.isDuplex())
                assertTrue(body.isOneShot())
                assertEquals(0, writes, "Metadata access must not consume a streaming body")
                body.writeTo(destination)
                assertEquals(1, writes)
                assertEquals(listOf<Byte>(0, 1, -1, 127), destination.readByteArray().toList())
            }
        }
    }

    @Test
    fun realOkHttpReturnsInitial503WithoutReplayingRefreshCredentials(): Unit = runBlocking {
        val bodies = CopyOnWriteArrayList<String>()
        val proofs = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val initialBody = """{"error":"temporarily_unavailable"}"""
        server.createContext("/token") { exchange ->
            bodies += exchange.requestBody.readAllBytes().decodeToString()
            proofs += exchange.requestHeaders.getFirst("DPoP")
            exchange.responseHeaders.add("DPoP-Nonce", "original-server-nonce")
            exchange.responseHeaders.add("Retry-After", "0")
            val body = if (bodies.size == 1) initialBody else "unexpected replay"
            exchange.sendResponseHeaders(if (bodies.size == 1) 503 else 200, body.length.toLong())
            exchange.responseBody.use { it.write(body.toByteArray()) }
            exchange.close()
        }
        server.start()
        val client = credentialClient()
        val refresh = "grant_type=refresh_token&refresh_token=synthetic-single-use-token"
        try {
            val response = client.post("http://127.0.0.1:${server.address.port}/token") {
                header("DPoP", "synthetic-single-request-proof")
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(refresh)
            }
            assertEquals(503, response.status.value)
            assertEquals("0", response.headers["Retry-After"])
            assertEquals("original-server-nonce", response.headers["DPoP-Nonce"])
            assertEquals(initialBody, response.bodyAsText())
            assertEquals(listOf(refresh), bodies.toList())
            assertEquals(listOf("synthetic-single-request-proof"), proofs.toList())
        } finally {
            client.close()
            server.stop(0)
        }
    }

    @Test
    fun realOkHttpPolicyCoversCurrentCredentialPostShapesAndEmptyPost(): Unit = runBlocking {
        data class Sent(val path: String, val body: String, val type: ContentType?)
        data class Received(val path: String, val body: String, val type: String?, val length: String?, val proof: String?)
        val sent = listOf(
            Sent("/par", "client_id=synthetic&code_challenge=synthetic", ContentType.Application.FormUrlEncoded),
            Sent("/token", "grant_type=authorization_code&code=synthetic&code_verifier=synthetic", ContentType.Application.FormUrlEncoded),
            Sent("/token", "grant_type=refresh_token&refresh_token=synthetic", ContentType.Application.FormUrlEncoded),
            Sent("/revoke", "token=synthetic&token_type_hint=refresh_token", ContentType.Application.FormUrlEncoded),
            Sent("/xrpc/com.atproto.repo.createRecord", """{"record":{"title":"Café 🎵"}}""", ContentType.Application.Json),
            // Ktor's NoContent POST becomes an empty (but non-null) OkHttp RequestBody.
            Sent("/empty", "", null),
        )
        for (status in listOf(503, 408)) {
            val received = CopyOnWriteArrayList<Received>()
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/") { exchange ->
                received += Received(exchange.requestURI.path, exchange.requestBody.readAllBytes().decodeToString(),
                    exchange.requestHeaders.getFirst("Content-Type"), exchange.requestHeaders.getFirst("Content-Length"),
                    exchange.requestHeaders.getFirst("DPoP"))
                exchange.responseHeaders.add("Retry-After", "0")
                exchange.responseHeaders.add("DPoP-Nonce", "received-error-nonce")
                exchange.sendResponseHeaders(status, -1)
                exchange.close()
            }
            server.start()
            val client = credentialClient()
            try {
                sent.forEachIndexed { index, request ->
                    val response = client.post("http://127.0.0.1:${server.address.port}${request.path}") {
                        header("DPoP", "synthetic-proof-$index")
                        request.type?.let { contentType(it) }
                        if (request.body.isNotEmpty()) setBody(request.body)
                    }
                    assertEquals(status, response.status.value)
                    assertEquals("0", response.headers["Retry-After"])
                    assertEquals("received-error-nonce", response.headers["DPoP-Nonce"])
                    response.bodyAsText()
                }
                assertEquals(sent.size, received.size, "One wire POST per explicit call for HTTP $status")
                sent.zip(received).forEachIndexed { index, (request, actual) ->
                    assertEquals(request.path, actual.path)
                    assertEquals(request.body, actual.body)
                    assertEquals(request.body.toByteArray().size.toString(), actual.length)
                    assertEquals(request.type?.toString(), actual.type?.substringBefore(';'))
                    assertEquals("synthetic-proof-$index", actual.proof)
                }
            } finally {
                client.close()
                server.stop(0)
            }
        }
    }
}

private fun credentialClient() = HttpClient(OkHttp) {
    install(HttpTimeout) { requestTimeoutMillis = 5_000 }
    atProtoCredentialPolicy()
}
