package com.arn.scrobble.api.atproto

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import okhttp3.HttpUrl
import java.security.MessageDigest
import java.util.Date
import java.util.UUID

/** Signs one OAuth request; only the public part of the session key goes on the wire. */
fun ECKey.atprotoDpop(method: String, url: HttpUrl, nonce: String?, accessToken: String? = null): String {
    val header = JWSHeader.Builder(JWSAlgorithm.ES256)
        .type(JOSEObjectType("dpop+jwt")).jwk(toPublicJWK()).build()
    val claims = JWTClaimsSet.Builder().jwtID(UUID.randomUUID().toString()).issueTime(Date())
        .claim("htm", method).claim("htu", url.newBuilder().query(null).fragment(null).build().toString())
    if (nonce != null) claims.claim("nonce", nonce)
    if (accessToken != null) {
        val digest = MessageDigest.getInstance("SHA-256").digest(accessToken.toByteArray(Charsets.US_ASCII))
        claims.claim("ath", Base64URL.encode(digest).toString())
    }
    return SignedJWT(header, claims.build()).apply { sign(ECDSASigner(this@atprotoDpop)) }.serialize()
}
