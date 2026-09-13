package com.arn.scrobble.atproto.crypto

import com.arn.scrobble.atproto.encoding.decodeBase64Url
import com.arn.scrobble.atproto.encoding.encodeBase64Url
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec

private const val P256_BYTE_SIZE = 32

@Serializable
data class P256PublicJwk(
    @SerialName("kty") val keyType: String,
    @SerialName("crv") val curve: String,
    val x: String,
    val y: String,
) {
    init {
        require(keyType == "EC") { "P-256 JWK key type must be EC" }
        require(curve == "P-256") { "P-256 JWK curve must be P-256" }
        requireCoordinate("x", x)
        requireCoordinate("y", y)
    }

    fun thumbprint(): String {
        val canonical = "{\"crv\":\"P-256\",\"kty\":\"EC\",\"x\":\"$x\",\"y\":\"$y\"}"
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .encodeBase64Url()
    }
}

@Serializable
data class P256PrivateJwk(
    @SerialName("kty") val keyType: String,
    @SerialName("crv") val curve: String,
    val x: String,
    val y: String,
    val d: String,
    @SerialName("alg") val algorithm: String,
) {
    init {
        require(keyType == "EC") { "P-256 JWK key type must be EC" }
        require(curve == "P-256") { "P-256 JWK curve must be P-256" }
        require(algorithm == "ES256") { "P-256 DPoP keys must use ES256" }
        requireCoordinate("x", x)
        requireCoordinate("y", y)
        requireCoordinate("d", d)
    }

    fun publicJwk() = P256PublicJwk(
        keyType = keyType,
        curve = curve,
        x = x,
        y = y,
    )

    override fun toString(): String =
        "P256PrivateJwk(keyType=$keyType, curve=$curve, x=$x, y=$y, d=<redacted>, algorithm=$algorithm)"
}

internal fun generateP256PrivateJwk(random: SecureRandom = SecureRandom()): P256PrivateJwk {
    val keyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"), random)
        generateKeyPair()
    }
    val publicKey = keyPair.public as ECPublicKey
    val privateKey = keyPair.private as ECPrivateKey

    return P256PrivateJwk(
        keyType = "EC",
        curve = "P-256",
        x = publicKey.w.affineX.toFixedUnsignedBytes(P256_BYTE_SIZE).encodeBase64Url(),
        y = publicKey.w.affineY.toFixedUnsignedBytes(P256_BYTE_SIZE).encodeBase64Url(),
        d = privateKey.s.toFixedUnsignedBytes(P256_BYTE_SIZE).encodeBase64Url(),
        algorithm = "ES256",
    )
}

internal fun P256PrivateJwk.toPrivateKey() = KeyFactory.getInstance("EC").generatePrivate(
    ECPrivateKeySpec(BigInteger(1, d.decodeBase64Url()), p256Parameters),
)

internal fun P256PublicJwk.toPublicKey() = KeyFactory.getInstance("EC").generatePublic(
    ECPublicKeySpec(
        ECPoint(
            BigInteger(1, x.decodeBase64Url()),
            BigInteger(1, y.decodeBase64Url()),
        ),
        p256Parameters,
    ),
)

private val p256Parameters: ECParameterSpec by lazy {
    AlgorithmParameters.getInstance("EC").run {
        init(ECGenParameterSpec("secp256r1"))
        getParameterSpec(ECParameterSpec::class.java)
    }
}

private fun requireCoordinate(name: String, value: String) {
    val bytes = runCatching { value.decodeBase64Url() }
        .getOrElse { throw IllegalArgumentException("P-256 JWK $name must be base64url", it) }
    require(bytes.size == P256_BYTE_SIZE) { "P-256 JWK $name must contain 32 bytes" }
}

private fun BigInteger.toFixedUnsignedBytes(size: Int): ByteArray {
    val encoded = toByteArray()
    val unsigned = if (encoded.size > 1 && encoded[0] == 0.toByte()) encoded.copyOfRange(1, encoded.size) else encoded
    require(unsigned.size <= size) { "Integer does not fit in $size bytes" }

    return ByteArray(size).also {
        unsigned.copyInto(it, destinationOffset = size - unsigned.size)
    }
}
