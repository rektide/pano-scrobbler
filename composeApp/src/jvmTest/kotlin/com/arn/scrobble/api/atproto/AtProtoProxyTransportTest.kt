package com.arn.scrobble.api.atproto

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtProtoProxyTransportTest {
    @Test
    fun httpsConnectAuthenticationStillPrecedesTheSingleOriginPost(): Unit = runBlocking {
        val credential = Credentials.basic("synthetic-user", "synthetic-password")
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5_000
            val executor = Executors.newSingleThreadExecutor()
            val connect = executor.submit<Pair<List<String>, Int>> {
                server.accept().use { socket ->
                    socket.soTimeout = 5_000
                    val input = socket.getInputStream()
                    val output = socket.getOutputStream()
                    val requests = mutableListOf(input.readConnectHeaders())
                    output.write(("HTTP/1.1 407 Proxy Authentication Required\r\n" +
                        "Proxy-Authenticate: Basic realm=\"synthetic\"\r\nContent-Length: 0\r\n\r\n").toByteArray(Charsets.US_ASCII))
                    output.flush()
                    requests += input.readConnectHeaders()
                    output.write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray(Charsets.US_ASCII))
                    output.flush()
                    // Deliberately stop at TLS initialization: this fixture is not a TLS trust test.
                    requests to input.read()
                }
            }
            val client = HttpClient(OkHttp) {
                install(HttpTimeout) { requestTimeoutMillis = 5_000 }
                engine {
                    proxy = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", server.localPort))
                    config {
                        // Same public proxy-authenticator shape as Pano's base construction.
                        proxyAuthenticator { _, response ->
                            response.request.newBuilder().header("Proxy-Authorization", credential).build()
                        }
                    }
                }
                atProtoCredentialPolicy()
            }
            try {
                assertFailsWith<IOException> {
                    client.post("https://origin.example/token") {
                        header("DPoP", "synthetic-origin-proof")
                        header("Authorization", "DPoP synthetic-origin-token")
                        setBody("synthetic-origin-refresh")
                    }
                }
                val (requests, firstTlsByte) = connect.get(10, TimeUnit.SECONDS)
                assertEquals(2, requests.size)
                assertEquals(22, firstTlsByte, "TLS handshake starts only after CONNECT authentication")
                for (request in requests) {
                    assertTrue(request.startsWith("CONNECT origin.example:443 HTTP/1.1\r\n"))
                    assertTrue(request.contains("Proxy-Authorization: $credential\r\n", ignoreCase = true))
                    assertFalse(request.contains("synthetic-origin"))
                    assertFalse(request.contains("\r\nDPoP:", ignoreCase = true))
                    assertFalse(request.contains("\r\nAuthorization:", ignoreCase = true))
                }
            } finally {
                client.close()
                executor.shutdownNow()
            }
        }
    }
}

private fun InputStream.readConnectHeaders(): String = buildString {
    while (!endsWith("\r\n\r\n")) {
        val byte = read()
        if (byte == -1) throw IOException("Proxy fixture received an incomplete CONNECT")
        if (length >= 16 * 1024) throw IOException("Proxy fixture CONNECT headers too large")
        append(byte.toChar())
    }
}
