package com.arn.scrobble.atproto.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.request.url
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StreamingBoundaryTest {
    @Test fun oversizedStreamingBodyFailsBeforeEofAndUnsafeClientsNeverSend(): Unit = runBlocking {
        val channel = ByteChannel(autoFlush = true)
        val writer = launch(Dispatchers.IO) {
            channel.writeStringUtf8("x".repeat(10_000))
            awaitCancellation() // No EOF; the reader must not wait for it to enforce the cap.
        }
        val client = HttpClient(MockEngine { respond(channel) }) { followRedirects = false }
        try {
            withContext(Dispatchers.IO) {
                withTimeout(5_000) {
                    val failure = assertFailsWith<HttpResponseBodyException> {
                        client.boundedRequest(1024) { url("https://fixture.example") }
                    }
                    assertTrue(failure.failure is IllegalArgumentException)
                }
            }
        } finally { writer.cancelAndJoin(); client.close() }
        for (retry in listOf(false, true)) {
            val unsafe = HttpClient(MockEngine { error("Must reject before sending") }) {
                followRedirects = !retry
                if (retry) install(HttpRequestRetry)
            }
            try { assertFailsWith<IllegalArgumentException> { unsafe.boundedRequest(1024) { url("https://fixture.example") } } }
            finally { unsafe.close() }
        }
    }
}
