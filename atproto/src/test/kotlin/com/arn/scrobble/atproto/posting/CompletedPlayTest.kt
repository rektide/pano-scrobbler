package com.arn.scrobble.atproto.posting

import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class CompletedPlayTest {
    @Test fun nativePlayMapsSecondsAndMillisecondsIndependentlyAndHasStableRetryIdentity() {
        val play = CompletedPlay("Track", "Artist", "Album", playedAtMillis = 1_700_000_000_000, durationMillis = 12_345)
        val teal = play.record(AtProtoTarget.TEAL, "pano-fixture")
        val rocksky = play.record(AtProtoTarget.ROCKSKY, "pano-fixture")
        assertEquals("12", teal.getValue("duration").jsonPrimitive.content)
        assertEquals("12345", rocksky.getValue("duration").jsonPrimitive.content)
        assertEquals("fm.teal.feed.play", teal.getValue("\$type").jsonPrimitive.content)
        assertEquals("app.rocksky.scrobble", rocksky.getValue("\$type").jsonPrimitive.content)
        assertEquals(play.id(), play.copy().id())
        assertNotEquals(play.id(), play.copy(playedAtMillis = play.playedAtMillis + 1).id())
    }
}
