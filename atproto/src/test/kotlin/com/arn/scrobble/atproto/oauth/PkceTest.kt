package com.arn.scrobble.atproto.oauth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PkceTest {
    @Test fun rfc7636Challenge() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", pkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test fun enforcesLength() {
        assertFailsWith<IllegalArgumentException> { pkceChallenge("short") }
        assertFailsWith<IllegalArgumentException> { pkceChallenge("a".repeat(129)) }
    }

    @Test fun rejectsEveryNonUnreservedAsciiCharacterAndLossyUnicode() {
        val unreserved = ('a'..'z') + ('A'..'Z') + ('0'..'9') + "-._~".toList()
        for (code in 0..127) {
            val value = "a".repeat(42) + code.toChar()
            if (code.toChar() in unreserved) pkceChallenge(value)
            else assertFailsWith<IllegalArgumentException>("ASCII $code") { pkceChallenge(value) }
        }
        for (value in listOf("é", "\ud800", "\udfff", "😀"))
            assertFailsWith<IllegalArgumentException> { pkceChallenge("a".repeat(43) + value) }
        pkceChallenge("a".repeat(128))
    }
}
