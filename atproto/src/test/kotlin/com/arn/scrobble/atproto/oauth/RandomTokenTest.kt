package com.arn.scrobble.atproto.oauth

import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RandomTokenTest {
    @Test
    fun `uses the URL-safe alphabet at the requested length`() {
        val random = Random(42)
        val token = randomUrlToken(64, random::nextInt)

        assertEquals(64, token.length)
        assertTrue(token.all { it.isLetterOrDigit() || it == '-' || it == '_' })
        assertEquals(token, randomUrlToken(64, Random(42)::nextInt))
    }
}
