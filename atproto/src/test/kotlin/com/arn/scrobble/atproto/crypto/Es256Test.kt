package com.arn.scrobble.atproto.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Es256Test {
    @Test
    fun `signs and verifies a JOSE formatted ES256 signature`() {
        val key = generateP256PrivateJwk()
        val input = "header.payload".toByteArray()

        val signature = signEs256(input, key)

        assertEquals(64, signature.size)
        assertTrue(verifyEs256(input, signature, key.publicJwk()))
        assertFalse(verifyEs256("different".toByteArray(), signature, key.publicJwk()))
    }

    @Test
    fun `converts DER integers to fixed-width P1363 and back`() {
        val der = byteArrayOf(
            0x30, 0x07,
            0x02, 0x01, 0x01,
            0x02, 0x02, 0x00, 0x80.toByte(),
        )

        val p1363 = derToP1363(der)

        assertEquals(64, p1363.size)
        assertEquals(1, p1363[31])
        assertEquals(0x80.toByte(), p1363[63])
        assertContentEquals(der, p1363ToDer(p1363))
    }

    @Test
    fun `rejects malformed DER signatures`() {
        assertFailsWith<IllegalArgumentException> {
            derToP1363(byteArrayOf(0x30, 0x06, 0x02, 0x01, 0x80.toByte(), 0x02, 0x01, 0x01))
        }
        assertFailsWith<IllegalArgumentException> { p1363ToDer(ByteArray(63)) }
    }
}
