package com.arn.scrobble.atproto.identity

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class HandleClaimTest {
    @Test
    fun `claims do not inherit UI trimming prefixes or Unicode case folding`() {
        for (claim in listOf("at:// alice.bsky.social", "at://alice.bsky.social ", "at://@alice.bsky.social", "at://K.example.com")) {
            assertNull(DidDocument("did:plc:abcdefghijklmnopqrstuvwx", listOf(claim)).claimedHandle(), claim)
        }
        assertFailsWith<IllegalArgumentException> { normalizeHandle("K.example.com") }
    }
}
