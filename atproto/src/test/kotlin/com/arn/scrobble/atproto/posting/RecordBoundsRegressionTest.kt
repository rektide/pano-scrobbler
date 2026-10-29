package com.arn.scrobble.atproto.posting

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URISyntaxException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RecordBoundsRegressionTest {
    private fun play() = Play("Track", "Artist", playedAtMillis = 1_700_000_000_123)

    @Test fun rejectsMalformedUtf16InEveryFieldBeforeEncoding() {
        for (bad in listOf("\ud800", "\udfff", "a\ud800b", "😀\ud800")) {
            assertFailsWith<IllegalArgumentException> { play().copy(track = bad) }
            assertFailsWith<IllegalArgumentException> { play().copy(artist = bad) }
            assertFailsWith<IllegalArgumentException> { play().copy(album = bad) }
            assertFailsWith<IllegalArgumentException> { play().copy(albumArtist = bad) }
            assertFailsWith<IllegalArgumentException> { play().copy(sourceTag = bad) }
            assertFailsWith<IllegalArgumentException> { play().copy(originUri = "https://music.example.com/$bad") }
            assertFailsWith<IllegalArgumentException> { play().copy(musicServiceUri = "https://music.example.com/$bad") }
            assertFailsWith<IllegalArgumentException> { play().toTealRecord(bad) }
        }
    }

    @Test fun capsUriBytesWithoutTruncatingMeaning() {
        val prefix = "https://music.example.com/"
        val uri = prefix + "a".repeat(8192 - prefix.length)
        assertEquals(uri, play().copy(originUri = uri).toTealRecord(null).originUri)
        assertFailsWith<IllegalArgumentException> { play().copy(originUri = uri + "a") }
        assertFailsWith<IllegalArgumentException> { play().copy(musicServiceUri = uri + "a") }
        assertFailsWith<IllegalArgumentException> { play().copy(originUri = prefix + "é".repeat(4096)) }
        assertFailsWith<IllegalArgumentException> { play().copy(originUri = "/relative") }
        assertFailsWith<URISyntaxException> { play().copy(originUri = "https://music.example.com/bad%Q0") }
        assertEquals("spotify:track:123", play().copy(originUri = "spotify:track:123").toTealRecord(null).originUri)
    }

    @Test fun rejectsExtendedYearsAndUnsafeIntegers() {
        assertEquals("9999-12-31T23:59:59.999Z", play().copy(playedAtMillis = 253_402_300_799_999L).toTealRecord(null).playedTime)
        for (millis in listOf(0L, -1L, 253_402_300_800_000L, Long.MAX_VALUE))
            assertFailsWith<IllegalArgumentException> { play().copy(playedAtMillis = millis) }
        assertEquals(9_007_199_254_740_991L, play().copy(durationMillis = 9_007_199_254_740_991L).toRockskyRecord().duration)
        for (duration in listOf(0L, -1L, 9_007_199_254_740_992L, Long.MAX_VALUE))
            assertFailsWith<IllegalArgumentException> { play().copy(durationMillis = duration) }
        assertFailsWith<IllegalArgumentException> { play().copy(trackNumber = 0) }
    }

    @Test fun boundsRawMetadataBeforeTrimmingOrScanning() {
        val long = "a".repeat(1_000_000)
        assertFailsWith<IllegalArgumentException> { play().copy(track = long) }
        assertFailsWith<IllegalArgumentException> { play().copy(album = long) }
        assertFailsWith<IllegalArgumentException> { play().toTealRecord(long) }
        assertEquals("a".repeat(256), play().copy(track = "a".repeat(16 * 1024)).toTealRecord(null).trackName)
    }

    @Test fun emittedFieldsRespectCurrentUnitsAndByteLimits() {
        // Teal bb80aca1 seconds; Rocksky 850216da milliseconds.
        val uri = "https://music.example.com/" + "a".repeat(8100)
        val value = play().copy(track = "\u0000".repeat(600), artist = "🎵".repeat(100), album = "漢".repeat(200),
            albumArtist = "é".repeat(200), sourceTag = "\u0000".repeat(600), durationMillis = 243_999,
            trackNumber = Int.MAX_VALUE, originUri = uri, musicServiceUri = uri)
        val teal = value.toTealRecord("\u0000".repeat(600))
        val rocksky = value.toRockskyRecord()
        for ((text, maximum) in listOf(teal.trackName to 256, teal.artists.single().artistName to 256,
            teal.releaseName!! to 256, teal.submissionClientAgent!! to 256, rocksky.title to 512, rocksky.artist to 256,
            rocksky.artists.single().name to 256, rocksky.album to 256, rocksky.albumArtist to 256, rocksky.tags!!.single() to 256))
            assertTrue(text.toByteArray(Charsets.UTF_8).size in 1..maximum)
        assertEquals(243, teal.duration)
        assertEquals(243_999, rocksky.duration)
        assertTrue(Json.encodeToString(teal).toByteArray(Charsets.UTF_8).size < 32 * 1024)
        assertTrue(Json.encodeToString(rocksky).toByteArray(Charsets.UTF_8).size < 32 * 1024)
    }

    @Test fun truncationIsMaximalCompleteCodepointPrefix() {
        for (text in listOf("abc", "é".repeat(140), "漢".repeat(100), "🎵".repeat(100), "abé漢🎵e\u0301".repeat(70))) {
            for (budget in 0..512) {
                val truncated = text.truncateUtf8(budget)
                truncated.requireRecordText("fixture")
                assertTrue(text.startsWith(truncated))
                assertTrue(truncated.toByteArray(Charsets.UTF_8).size <= budget)
                if (truncated.length < text.length) {
                    val nextEnd = truncated.length + Character.charCount(text.codePointAt(truncated.length))
                    assertTrue(text.substring(0, nextEnd).toByteArray(Charsets.UTF_8).size > budget)
                }
            }
        }
    }
}
