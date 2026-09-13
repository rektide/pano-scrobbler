package com.arn.scrobble.atproto.identity

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HandleTest {
    @Test
    fun `normalizes a valid handle`() {
        assertEquals("alice.bsky.social", normalizeHandle(" @Alice.BSKY.Social "))
        assertEquals("xn--bcher-kva.tld", normalizeHandle("xn--bcher-kva.tld"))
    }

    @Test
    fun `rejects malformed handles`() {
        listOf("localhost", "john-.test", "john.0", "127.0.0.1", "jo_hn.test").forEach {
            assertFailsWith<IllegalArgumentException>(it) { normalizeHandle(it) }
        }
    }
}
