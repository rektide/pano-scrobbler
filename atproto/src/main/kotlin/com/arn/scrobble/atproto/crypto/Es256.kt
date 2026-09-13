package com.arn.scrobble.atproto.crypto

import java.security.Signature

private const val P256_COMPONENT_SIZE = 32

internal fun signEs256(
    signingInput: ByteArray,
    key: P256PrivateJwk,
): ByteArray {
    val derSignature = Signature.getInstance("SHA256withECDSA").run {
        initSign(key.toPrivateKey())
        update(signingInput)
        sign()
    }

    return derToP1363(derSignature)
}

internal fun verifyEs256(
    signingInput: ByteArray,
    signature: ByteArray,
    key: P256PublicJwk,
): Boolean = Signature.getInstance("SHA256withECDSA").run {
    initVerify(key.toPublicKey())
    update(signingInput)
    verify(p1363ToDer(signature))
}

internal fun derToP1363(signature: ByteArray): ByteArray {
    val reader = DerReader(signature)
    reader.requireTag(0x30)
    val sequenceLength = reader.readLength()
    require(sequenceLength == reader.remaining) { "Invalid DER ECDSA sequence length" }

    val r = reader.readPositiveInteger(P256_COMPONENT_SIZE)
    val s = reader.readPositiveInteger(P256_COMPONENT_SIZE)
    require(reader.remaining == 0) { "Unexpected data after DER ECDSA signature" }

    return ByteArray(P256_COMPONENT_SIZE * 2).also {
        r.copyInto(it, destinationOffset = P256_COMPONENT_SIZE - r.size)
        s.copyInto(it, destinationOffset = P256_COMPONENT_SIZE * 2 - s.size)
    }
}

internal fun p1363ToDer(signature: ByteArray): ByteArray {
    require(signature.size == P256_COMPONENT_SIZE * 2) {
        "ES256 signatures must contain 64 bytes"
    }

    val r = encodeDerInteger(signature.copyOfRange(0, P256_COMPONENT_SIZE))
    val s = encodeDerInteger(signature.copyOfRange(P256_COMPONENT_SIZE, signature.size))
    val bodyLength = r.size + s.size

    return byteArrayOf(0x30, bodyLength.toByte()) + r + s
}

private fun encodeDerInteger(component: ByteArray): ByteArray {
    val firstValueByte = component.indexOfFirst { it != 0.toByte() }
    require(firstValueByte >= 0) { "ECDSA signature components must be positive" }

    val value = component.copyOfRange(firstValueByte, component.size)
    val positiveValue = if (value[0].toInt() and 0x80 != 0) byteArrayOf(0) + value else value
    return byteArrayOf(0x02, positiveValue.size.toByte()) + positiveValue
}

private class DerReader(private val bytes: ByteArray) {
    private var position = 0

    val remaining: Int
        get() = bytes.size - position

    fun requireTag(expected: Int) {
        require(readUnsignedByte() == expected) { "Invalid DER ECDSA signature" }
    }

    fun readLength(): Int {
        val first = readUnsignedByte()
        if (first and 0x80 == 0) return first

        val byteCount = first and 0x7f
        require(byteCount in 1..2 && byteCount <= remaining) { "Invalid DER length" }

        var length = 0
        repeat(byteCount) { index ->
            val next = readUnsignedByte()
            require(index != 0 || next != 0) { "Non-canonical DER length" }
            length = (length shl 8) or next
        }
        require(length >= 128) { "Non-canonical DER length" }
        return length
    }

    fun readPositiveInteger(maxSize: Int): ByteArray {
        requireTag(0x02)
        val length = readLength()
        require(length in 1..remaining) { "Invalid DER integer length" }

        val value = bytes.copyOfRange(position, position + length)
        position += length

        require(value[0].toInt() and 0x80 == 0) { "DER integers must be positive" }
        require(value.size == 1 || value[0] != 0.toByte() || value[1].toInt() and 0x80 != 0) {
            "Non-canonical DER integer"
        }

        val unsigned = if (value.size > 1 && value[0] == 0.toByte()) value.copyOfRange(1, value.size) else value
        require(unsigned.size <= maxSize) { "DER integer is too large" }
        require(unsigned.any { it != 0.toByte() }) { "ECDSA signature components must be positive" }
        return unsigned
    }

    private fun readUnsignedByte(): Int {
        require(remaining > 0) { "Unexpected end of DER signature" }
        return bytes[position++].toInt() and 0xff
    }
}
