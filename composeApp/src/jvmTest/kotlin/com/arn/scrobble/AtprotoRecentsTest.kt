package com.arn.scrobble

import com.arn.scrobble.api.AccountType
import com.arn.scrobble.api.UserAccountSerializable
import com.arn.scrobble.api.UserCached
import com.arn.scrobble.api.atproto.AtprotoScrobblable
import com.arn.scrobble.api.atproto.playCollection
import com.arn.scrobble.api.lastfm.ApiException
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AtprotoRecentsTest {
    @Test
    fun readsTwoFullPagesAndTailForEachSourceWithoutCredentials() = bothSources { type ->
        fixture(type, { q ->
            val start = q["cursor"]?.substringAfter("+/")?.toInt() ?: 0
            val end = minOf(start + q.getValue("limit").toInt(), 237)
            records((start until end).map { row(type, it) }, ("+/" + end).takeIf { end < 237 })
        }) { scrobbler, requests ->
            assertTrue(scrobbler.getRecents(1, cached = true).isFailure)
            assertTrue(requests.isEmpty())
            val pages = (1..3).map { scrobbler.getRecents(it, limit = 100, includeNowPlaying = true).getOrThrow() }
            assertEquals(listOf(100, 100, 37), pages.map { it.entries.size })
            assertEquals(listOf(2, 3, 3), pages.map { it.attr.totalPages })
            assertTrue(pages.all { it.attr.total == null })
            assertEquals((0..236).map { "Track $it" }, pages.flatMap { it.entries }.map { it.name })
            val first = pages.first().entries.first()
            assertEquals(125_000L, first.duration)
            assertEquals(startMillis, first.date)
            assertEquals("First, Second", first.artist.name)
            assertEquals("Release", first.album?.name)
            assertFalse(first.isNowPlaying)
            assertEquals(pages.first(), scrobbler.getRecents(1, cached = true, limit = 100).getOrThrow())
            assertEquals(listOf(null, "+/100", "+/200"), requests.map { it["cursor"] })
            assertTrue(scrobbler.getRecents(2, username = "other.example", limit = 100).isFailure)
            assertTrue(scrobbler.getRecents(2, limit = 99).isFailure)
            assertTrue(scrobbler.getRecents(2, limit = 100, to = startMillis).isFailure)
            assertEquals(3, requests.size)
            assertEquals(pages.first(), scrobbler.getRecents(1, limit = 100).getOrThrow())
            assertNull(requests.last()["cursor"])
            assertTrue(scrobbler.getRecents(2, limit = 100, cached = true).isFailure)
        }
    }

    @Test
    fun walksPastFilteredAndMalformedRecordsUsingActualPlayTime() = bothSources { type ->
        fixture(type, { q ->
            when (q["cursor"]) {
                null -> records(listOf(row(type, 0), row(type, 7, time = "bad")), "filtered")
                "filtered" -> records(listOf(row(type, 5, legacy = true), row(type, 0)), "same-track")
                "same-track" -> records(listOf(row(type, 5, legacy = true)), "older-key")
                "older-key" -> records(listOf(row(type, 6)), "terminal")
                else -> records(emptyList())
            }
        }) { scrobbler, requests ->
            val from = startMillis - 6000
            val to = startMillis - 5000
            val first = scrobbler.getRecents(1, from = from, to = to, limit = 2).getOrThrow()
            assertEquals(listOf("Track 5", "Track 6"), first.entries.map { it.name })
            assertEquals("Legacy", first.entries.first().artist.name)
            assertEquals(to, first.entries.first().date)
            val last = scrobbler.getRecents(2, from = from, to = to, limit = 2).getOrThrow()
            assertEquals(emptyList(), last.entries)
            assertEquals(2, last.attr.totalPages)
            assertEquals(listOf(null, "filtered", "same-track", "older-key", "terminal"), requests.map { it["cursor"] })
        }
    }

    @Test
    fun surfacesHttpErrorsAndRepeatedCursorsWithoutPoisoningRetry() = bothSources { type ->
        val failing = AtomicBoolean(true)
        fixture(type, { q ->
            when (q["cursor"]) {
                null -> records(listOf(row(type, 0), row(type, 1)), "next")
                "next" -> if (failing.getAndSet(false)) {
                    503 to """{"error":"Unavailable"}"""
                } else {
                    records(listOf(row(type, 2)), "cycle")
                }
                "cycle" -> records(listOf(row(type, 2)), "next")
                else -> error("Unexpected cursor")
            }
        }) { scrobbler, requests ->
            val first = scrobbler.getRecents(1, limit = 2).getOrThrow()
            assertEquals(503, assertIs<ApiException>(scrobbler.getRecents(2, limit = 2).exceptionOrNull()).code)
            assertTrue(scrobbler.getRecents(2, limit = 2).exceptionOrNull()?.message.orEmpty().contains("repeated"))
            assertEquals(first, scrobbler.getRecents(1, limit = 2, cached = true).getOrThrow())
            assertTrue(scrobbler.getRecents(2, limit = 2, cached = true).isFailure)
            assertEquals(listOf(null, "next", "next", "cycle"), requests.map { it["cursor"] })
        }
    }

    private fun bothSources(block: suspend (AccountType) -> Unit) = runBlocking {
        for (type in listOf(AccountType.TEAL, AccountType.ROCKSKY)) block(type)
    }

    private suspend fun fixture(
        type: AccountType,
        respond: (Map<String, String>) -> Pair<Int, String>,
        block: suspend (AtprotoScrobblable, Collection<Map<String, String>>) -> Unit,
    ) {
        val requests = ConcurrentLinkedQueue<Map<String, String>>()
        val badRequest = AtomicBoolean(false)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/xrpc/com.atproto.repo.listRecords") { exchange ->
            val q = exchange.requestURI.rawQuery.split('&').associate {
                val (key, value) = it.split('=', limit = 2)
                key to URLDecoder.decode(value, Charsets.UTF_8)
            }
            requests += q
            if (exchange.requestMethod != "GET" || q["repo"] != did || q["collection"] != type.playCollection ||
                listOf("Authorization", "DPoP", "Cookie").any { exchange.requestHeaders.containsKey(it) }) {
                badRequest.set(true)
                exchange.respond(400 to "{}")
            } else exchange.respond(respond(q))
        }
        server.start()
        try {
            HttpClient(OkHttp).use { client ->
                val user = UserCached("fixture.example", "https://fixture.example", "", "", 0)
                val account = UserAccountSerializable(type, user, did, "http://127.0.0.1:${server.address.port}")
                block(AtprotoScrobblable(account) { client }, requests)
                assertFalse(badRequest.get())
            }
        } finally {
            server.stop(0)
        }
    }

    private fun HttpExchange.respond(response: Pair<Int, String>) {
        val body = response.second.encodeToByteArray()
        responseHeaders.set("Content-Type", "application/json")
        sendResponseHeaders(response.first, body.size.toLong())
        responseBody.use { it.write(body) }
    }

    private fun records(rows: List<String>, cursor: String? = null) = 200 to
        """{"records":[${rows.joinToString(",")}]${cursor?.let { ",\"cursor\":\"$it\"" }.orEmpty()}}"""

    private fun row(type: AccountType, i: Int, legacy: Boolean = false, time: String = Instant.ofEpochMilli(startMillis - i * 1000).toString()): String {
        val teal = type == AccountType.TEAL
        val artist = when {
            teal && legacy -> """"artistNames":["Legacy"]"""
            teal -> """"artists":[{"artistName":"First"},{"artistName":"Second"}]"""
            legacy -> "\"artist\":\"Legacy\""
            else -> """"artists":[{"name":"First"},{"name":"Second"}]"""
        }
        val track = if (teal)
            """"trackName":"Track $i","releaseName":"Release","playedTime":"$time","duration":125"""
        else
            """"title":"Track $i","album":"Release","createdAt":"$time","duration":125000"""
        return """{"uri":"at://$did/${type.playCollection}/key-$i","value":{"${'$'}type":"${type.playCollection}",$artist,$track}}"""
    }

    private val did = "did:plc:abcdefghijklmnopqrstuvwx"
    private val startMillis = Instant.parse("2025-04-01T00:00:00Z").toEpochMilli()
}
