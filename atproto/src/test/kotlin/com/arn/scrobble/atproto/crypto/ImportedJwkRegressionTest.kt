package com.arn.scrobble.atproto.crypto

import com.arn.scrobble.atproto.encoding.encodeBase64Url
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ImportedJwkRegressionTest {
    private val x = "axfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpY"
    private val y = "T-NC4v4af5uO5-tKfA-eFivOM1drMV7Oy7ZAaDe_UfU"
    private val one = ByteArray(32).also { it[31] = 1 }.encodeBase64Url()
    private val zero = ByteArray(32).encodeBase64Url()

    @Test
    fun `imports a known generator point and corresponding scalar`() {
        val key = privateKey()
        assertEquals(key, Json.decodeFromString<P256PrivateJwk>(Json.encodeToString(key)))
        assertEquals(publicKey(), key.publicJwk())
    }

    @Test
    fun `rejects off curve points and coordinates outside the field`() {
        assertFailsWith<IllegalArgumentException> { P256PublicJwk("EC", "P-256", zero, zero) }
        val prime = hex("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff")
        assertFailsWith<IllegalArgumentException> { P256PublicJwk("EC", "P-256", prime, y) }
        assertFailsWith<IllegalArgumentException> { P256PublicJwk("EC", "P-256", x, prime) }
        assertFailsWith<IllegalArgumentException> { publicKey().copy(y = zero) }
    }

    @Test
    fun `rejects zero out of range and mismatched private scalars`() {
        assertFailsWith<IllegalArgumentException> { privateKey().copy(d = zero) }
        val order = hex("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551")
        assertFailsWith<IllegalArgumentException> { privateKey().copy(d = order) }
        assertFailsWith<IllegalArgumentException> { privateKey().copy(d = ByteArray(32) { -1 }.encodeBase64Url()) }
        val two = ByteArray(32).also { it[31] = 2 }.encodeBase64Url()
        assertFailsWith<IllegalArgumentException> { privateKey().copy(d = two) }
    }

    @Test
    fun `rejects noncanonical base64url and incorrect widths in all components`() {
        val invalidX = listOf(x + "=", x.dropLast(1), x + "A", x.dropLast(1) + "Z", "+" + x.drop(1), " " + x.drop(1))
        for (value in invalidX) assertFailsWith<IllegalArgumentException>(value) { publicKey().copy(x = value) }
        assertFailsWith<IllegalArgumentException> { publicKey().copy(y = y.dropLast(1) + "V") }
        assertFailsWith<IllegalArgumentException> { privateKey().copy(d = one.dropLast(1) + "F") }
    }

    @Test
    fun `deserialization cannot bypass imported key validation`() {
        val encoded = Json.encodeToString(privateKey()).replace("\"d\":\"$one\"", "\"d\":\"$zero\"")
        assertFailsWith<IllegalArgumentException> { Json.decodeFromString<P256PrivateJwk>(encoded) }
    }

    private fun publicKey() = P256PublicJwk("EC", "P-256", x, y)
    private fun privateKey() = P256PrivateJwk("EC", "P-256", x, y, one, "ES256")
    private fun hex(value: String) = BigInteger(value, 16).toByteArray().takeLast(32).toByteArray().encodeBase64Url()
}
