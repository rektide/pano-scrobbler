package com.arn.scrobble.atproto.oauth

import com.arn.scrobble.atproto.crypto.P256PrivateJwk
import com.arn.scrobble.atproto.crypto.toPrivateKey
import com.arn.scrobble.atproto.encoding.encodeBase64Url
import com.arn.scrobble.atproto.http.normalizeHtu
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import java.security.MessageDigest
import java.util.Date
import java.util.Locale

internal class DpopProofFactory(
    key: P256PrivateJwk,
    private val clock: EpochClock = EpochClock.System,
    private val nextJti: () -> String = { randomUrlToken(24) },
) {
    private val header = JWSHeader.Builder(JWSAlgorithm.ES256)
        .type(JOSEObjectType("dpop+jwt"))
        .jwk(ECKey.Builder(Curve.P_256, Base64URL(key.x), Base64URL(key.y)).build())
        .build()
    private val signer = ECDSASigner(key.toPrivateKey(), Curve.P_256)

    fun create(method: String, url: String, nonce: String? = null, accessToken: String? = null): String {
        val claims = JWTClaimsSet.Builder()
            .jwtID(nextJti())
            .claim("htm", method.uppercase(Locale.ROOT))
            .claim("htu", normalizeHtu(url))
            .issueTime(Date(clock.millis()))
        if (nonce != null) claims.claim("nonce", nonce)
        if (accessToken != null) claims.claim("ath", hashAccessToken(accessToken))

        return SignedJWT(header, claims.build()).apply { sign(signer) }.serialize()
    }
}

internal fun hashAccessToken(accessToken: String): String = MessageDigest.getInstance("SHA-256")
    .digest(accessToken.toByteArray(Charsets.UTF_8)).encodeBase64Url()
