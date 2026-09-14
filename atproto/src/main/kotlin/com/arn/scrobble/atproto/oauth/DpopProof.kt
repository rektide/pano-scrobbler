package com.arn.scrobble.atproto.oauth

import com.arn.scrobble.atproto.crypto.P256PrivateJwk
import com.arn.scrobble.atproto.crypto.P256PublicJwk
import com.arn.scrobble.atproto.crypto.signEs256
import com.arn.scrobble.atproto.encoding.encodeBase64Url
import com.arn.scrobble.atproto.http.normalizeHtu
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.util.Locale

internal class DpopProofFactory(
    private val key: P256PrivateJwk,
    private val clock: EpochClock = EpochClock.System,
    private val nextJti: () -> String = { randomUrlToken(24) },
) {
    fun create(
        method: String,
        url: String,
        nonce: String? = null,
        accessToken: String? = null,
    ): String {
        val header = DpopHeader(
            type = "dpop+jwt",
            algorithm = "ES256",
            jwk = key.publicJwk(),
        )
        val claims = DpopClaims(
            jti = nextJti(),
            method = method.uppercase(Locale.ROOT),
            url = normalizeHtu(url),
            issuedAt = clock.millis() / 1_000,
            nonce = nonce,
            accessTokenHash = accessToken?.let(::hashAccessToken),
        )
        val headerSegment = Json.encodeToString(header).toByteArray().encodeBase64Url()
        val claimsSegment = Json.encodeToString(claims).toByteArray().encodeBase64Url()
        val signingInput = "$headerSegment.$claimsSegment"
        val signature = signEs256(signingInput.toByteArray(Charsets.US_ASCII), key).encodeBase64Url()

        return "$signingInput.$signature"
    }
}

internal fun hashAccessToken(accessToken: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(accessToken.toByteArray(Charsets.UTF_8))
        .encodeBase64Url()

@Serializable
private data class DpopHeader(
    @kotlinx.serialization.SerialName("typ") val type: String,
    @kotlinx.serialization.SerialName("alg") val algorithm: String,
    val jwk: P256PublicJwk,
)

@Serializable
private data class DpopClaims(
    val jti: String,
    @kotlinx.serialization.SerialName("htm") val method: String,
    @kotlinx.serialization.SerialName("htu") val url: String,
    @kotlinx.serialization.SerialName("iat") val issuedAt: Long,
    val nonce: String? = null,
    @kotlinx.serialization.SerialName("ath") val accessTokenHash: String? = null,
)
