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
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECFieldFp
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
        requirePoint(x, y)
    }

    fun thumbprint(): String {
        val canonical = "{\"crv\":\"P-256\",\"kty\":\"EC\",\"x\":\"$x\",\"y\":\"$y\"}"
        return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8)).encodeBase64Url()
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
        val point = requirePoint(x, y)
        val scalar = requireCoordinate("d", d)
        require(scalar.signum() > 0 && scalar < p256Parameters.order) {
            "P-256 JWK private scalar is out of range"
        }

        // Establish correspondence with JCA rather than secret-dependent scalar multiplication.
        val factory = KeyFactory.getInstance("EC")
        val privateKey = factory.generatePrivate(ECPrivateKeySpec(scalar, p256Parameters))
        val publicKey = factory.generatePublic(ECPublicKeySpec(point, p256Parameters))
        val message = "Pano P-256 key-pair validation".toByteArray(Charsets.US_ASCII)
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(message)
            sign()
        }
        require(Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(message)
            verify(signature)
        }) { "P-256 JWK private and public keys do not match" }
    }

    fun publicJwk() = P256PublicJwk(keyType, curve, x, y)

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
        "EC", "P-256",
        publicKey.w.affineX.toFixedUnsignedBytes(P256_BYTE_SIZE).encodeBase64Url(),
        publicKey.w.affineY.toFixedUnsignedBytes(P256_BYTE_SIZE).encodeBase64Url(),
        privateKey.s.toFixedUnsignedBytes(P256_BYTE_SIZE).encodeBase64Url(), "ES256",
    )
}

internal fun P256PrivateJwk.toPrivateKey() = KeyFactory.getInstance("EC").generatePrivate(
    ECPrivateKeySpec(BigInteger(1, d.decodeBase64Url()), p256Parameters),
)

internal fun P256PublicJwk.toPublicKey() = KeyFactory.getInstance("EC").generatePublic(
    ECPublicKeySpec(ECPoint(BigInteger(1, x.decodeBase64Url()), BigInteger(1, y.decodeBase64Url())), p256Parameters),
)

private val p256Parameters: ECParameterSpec by lazy {
    AlgorithmParameters.getInstance("EC").run {
        init(ECGenParameterSpec("secp256r1"))
        getParameterSpec(ECParameterSpec::class.java)
    }
}

private fun requireCoordinate(name: String, value: String): BigInteger {
    require(value.length == 43) { "P-256 JWK $name must be an unpadded 32-byte base64url value" }
    val bytes = runCatching { value.decodeBase64Url() }
        .getOrElse { throw IllegalArgumentException("P-256 JWK $name must be base64url", it) }
    require(bytes.size == P256_BYTE_SIZE) { "P-256 JWK $name must contain 32 bytes" }
    require(bytes.encodeBase64Url() == value) { "P-256 JWK $name must use canonical base64url encoding" }
    return BigInteger(1, bytes)
}

private fun requirePoint(x: String, y: String): ECPoint {
    val px = requireCoordinate("x", x)
    val py = requireCoordinate("y", y)
    val curve = p256Parameters.curve
    val prime = (curve.field as ECFieldFp).p
    require(px < prime && py < prime) { "P-256 JWK coordinates are out of range" }
    require(py.multiply(py).mod(prime) == px.multiply(px).multiply(px)
        .add(curve.a.multiply(px)).add(curve.b).mod(prime)) {
        "P-256 JWK point is not on the curve"
    }
    // P-256 has cofactor 1; any finite point on this curve is in its prime-order subgroup.
    return ECPoint(px, py)
}

private fun BigInteger.toFixedUnsignedBytes(size: Int): ByteArray {
    val encoded = toByteArray()
    val unsigned = if (encoded.size > 1 && encoded[0] == 0.toByte()) encoded.copyOfRange(1, encoded.size) else encoded
    require(unsigned.size <= size) { "Integer does not fit in $size bytes" }
    return ByteArray(size).also { unsigned.copyInto(it, destinationOffset = size - unsigned.size) }
}
