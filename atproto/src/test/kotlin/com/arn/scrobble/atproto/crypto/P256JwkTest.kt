package com.arn.scrobble.atproto.crypto

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class P256JwkTest {
    @Test
    fun `generates fixed-width P-256 key material`() {
        val key = generateP256PrivateJwk()

        assertEquals(32, key.x.decodeUrl().size)
        assertEquals(32, key.y.decodeUrl().size)
        assertEquals(32, key.d.decodeUrl().size)
        assertEquals(43, key.publicJwk().thumbprint().length)
    }

    @Test
    fun `computes the RFC 7638 thumbprint input canonically`() {
        val key = P256PublicJwk(
            keyType = "EC",
            curve = "P-256",
            // secp256r1's generator; the previous arbitrary pair was off-curve.
            x = "axfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpY",
            y = "T-NC4v4af5uO5-tKfA-eFivOM1drMV7Oy7ZAaDe_UfU",
        )

        assertEquals("xx0BcA-wMohw8atYDJOe6peGModklG2wRHBlXHMvl0M", key.thumbprint())
    }

    @Test
    fun `public serialization cannot expose the private scalar`() {
        val key = generateP256PrivateJwk()

        val privateJson = Json.encodeToString(key)
        val publicJson = Json.encodeToString(key.publicJwk())

        assertTrue("\"d\"" in privateJson)
        assertFalse("\"d\"" in publicJson)
        assertFalse(key.d in key.toString())
    }

    private fun String.decodeUrl() = java.util.Base64.getUrlDecoder().decode(this)
}
