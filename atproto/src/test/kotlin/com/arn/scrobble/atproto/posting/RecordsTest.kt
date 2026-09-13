package com.arn.scrobble.atproto.posting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecordsTest {
    @Test
    fun `maps one play to both published lexicon units`() {
        val play = Play(
            track = "A track",
            artist = "An artist",
            album = "An album",
            albumArtist = "An album artist",
            playedAtMillis = 1_700_000_000_123,
            durationMillis = 243_000,
            trackNumber = 4,
            originUri = "https://music.example/track/1",
            musicServiceUri = "https://music.example/",
            sourceTag = "music.example",
        )

        val teal = play.toTealRecord("com.arn.scrobble/4.23")
        val rocksky = play.toRockskyRecord()

        assertEquals(243, teal.duration)
        assertEquals(243_000, rocksky.duration)
        assertEquals("2023-11-14T22:13:20.123Z", teal.playedTime)
        assertEquals(teal.playedTime, rocksky.createdAt)
        assertEquals("https://music.example/track/1", teal.originUri)
        assertEquals(listOf("music.example"), rocksky.tags)
        assertEquals(4, rocksky.trackNumber)
    }

    @Test
    fun `keeps required Rocksky fields valid when optional source data is absent`() {
        val play = Play(
            track = "Track",
            artist = "Artist",
            playedAtMillis = 1_700_000_000_000,
        )

        val teal = play.toTealRecord(null)
        val rocksky = play.toRockskyRecord()

        assertNull(teal.duration)
        assertEquals("Unknown", rocksky.album)
        assertEquals("Artist", rocksky.albumArtist)
        assertEquals(1, rocksky.duration)
        assertNull(rocksky.tags)
    }

    @Test
    fun `truncates multibyte strings to lexicon byte limits without splitting code points`() {
        val play = Play(
            track = "🎵".repeat(200),
            artist = "é".repeat(200),
            playedAtMillis = 1,
        )

        val teal = play.toTealRecord(null)
        val rocksky = play.toRockskyRecord()

        assertTrue(teal.trackName.toByteArray().size <= 256)
        assertTrue(teal.artists.single().artistName.toByteArray().size <= 256)
        assertTrue(rocksky.title.toByteArray().size <= 512)
    }
}
