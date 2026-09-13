package com.arn.scrobble.atproto.oauth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PkceTest {
    @Test
    fun `creates the RFC 7636 S256 challenge`() {
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            pkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun `rejects a verifier outside the RFC length range`() {
        assertFailsWith<IllegalArgumentException> { pkceChallenge("short") }
    }
}
