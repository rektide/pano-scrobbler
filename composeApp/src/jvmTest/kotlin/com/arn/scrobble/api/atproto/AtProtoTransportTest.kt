package com.arn.scrobble.api.atproto

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals

class AtProtoTransportTest {
    @Test fun realOkHttpNeverFollowsCredentialBearingRedirects(): Unit = runBlocking {
        val followed = AtomicInteger()
        val received = AtomicInteger()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/token") { exchange ->
            assertEquals("POST", exchange.requestMethod)
            assertEquals("DPoP synthetic-token", exchange.requestHeaders.getFirst("Authorization"))
            assertEquals("synthetic-code", exchange.requestBody.readAllBytes().decodeToString())
            received.incrementAndGet()
            val status = exchange.requestURI.rawQuery.toInt()
            exchange.responseHeaders.add("Location", "/redirect-target")
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
        }
        server.createContext("/redirect-target") { exchange ->
            followed.incrementAndGet()
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }
        server.start()
        val client = HttpClient(OkHttp) { atProtoCredentialPolicy() }
        try {
            for (status in listOf(301, 302, 303, 307, 308)) {
                val response = client.post("http://127.0.0.1:${server.address.port}/token?$status") {
                    header("Authorization", "DPoP synthetic-token")
                    header("DPoP", "synthetic-proof")
                    setBody("synthetic-code")
                }
                assertEquals(status, response.status.value)
                response.bodyAsText()
            }
            assertEquals(5, received.get())
            assertEquals(0, followed.get())
        } finally {
            client.close()
            server.stop(0)
        }
    }
}
