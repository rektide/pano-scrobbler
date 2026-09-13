package com.arn.scrobble.atproto.oauth

import com.arn.scrobble.atproto.encoding.encodeBase64Url
import java.security.MessageDigest

internal data class Pkce(
    val verifier: String,
    val challenge: String,
)

internal fun generatePkce(): Pkce {
    val verifier = randomUrlToken(64)
    return Pkce(
        verifier = verifier,
        challenge = pkceChallenge(verifier),
    )
}

internal fun pkceChallenge(verifier: String): String {
    require(verifier.length in 43..128) { "PKCE verifier must contain 43 to 128 characters" }

    return MessageDigest.getInstance("SHA-256")
        .digest(verifier.toByteArray(Charsets.US_ASCII))
        .encodeBase64Url()
}
