package com.arn.scrobble.api.atproto

// Frozen def70d8a review observation, not a conformance test to adopt unchanged.
// Session: opencode://ses_e7b181b8effef7JO12Vhel0XwH.

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals

class AtProtoEngineReviewProbeTest {
    @Test
    fun observed503RetryAfterZeroAutomaticallyReplaysCredentialBodyAndDpopProof(): Unit = runBlocking {
        val bodies = CopyOnWriteArrayList<String>()
        val proofs = CopyOnWriteArrayList<String>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/token") { exchange ->
            bodies += exchange.requestBody.readAllBytes().decodeToString()
            proofs += exchange.requestHeaders.getFirst("DPoP")
            if (bodies.size == 1) {
                exchange.responseHeaders.add("Retry-After", "0")
                exchange.sendResponseHeaders(503, -1)
            } else exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        val client = HttpClient(OkHttp) { atProtoCredentialPolicy() }
        try {
            val response = client.post("http://127.0.0.1:${server.address.port}/token") {
                header("DPoP", "synthetic-single-request-proof")
                setBody("grant_type=refresh_token&refresh_token=synthetic-single-use-token")
            }
            response.bodyAsText()
            assertEquals(200, response.status.value)
            assertEquals(List(2) { "grant_type=refresh_token&refresh_token=synthetic-single-use-token" }, bodies.toList())
            assertEquals(List(2) { "synthetic-single-request-proof" }, proofs.toList())
        } finally {
            client.close()
            server.stop(0)
        }
    }
}
