package com.arn.scrobble.atproto.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DiscoveryJsonTest {
    @Test fun rejectsDecodedDuplicateKeysBeforeParsingErasesThem(): Unit = runBlocking {
        for (text in listOf("""{"issuer":"one","issuer":"two"}""", """{"issuer":"one","\u0069ssuer":"two"}""",
            """{"ignored":[{"id":1,"id":2}]}""")) assertFailsWith<IllegalArgumentException> { fetch(text) }
        fetch("""{"a":{"id":1},"b":{"id":2},"escaped":"\"[]{}"}""")
    }
    @Test fun boundsNestingAndRequiresOneObject(): Unit = runBlocking {
        assertFailsWith<IllegalArgumentException> { fetch("""{"nested":${"[".repeat(40)}0${"]".repeat(40)}}""") }
        for (text in listOf("[]", "null", "true", "{}{}", "{\"a\":1,}")) assertFailsWith<IllegalArgumentException>(text) { fetch(text) }
        fetch("""{"nested":${"[".repeat(12)}0${"]".repeat(12)}}""")
    }
    private suspend fun fetch(text: String) {
        HttpClient(MockEngine { respond(text, headers = headersOf(HttpHeaders.ContentType, "application/json")) }) {
            followRedirects = false
        }.use { it.fetchJson("https://discovery.example.com", maxBytes = 4096, json = Json) }
    }
}
