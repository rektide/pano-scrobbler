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
    require(verifier.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "-._~" }) {
        "PKCE verifier must contain only RFC 7636 unreserved ASCII characters"
    }

    return MessageDigest.getInstance("SHA-256")
        .digest(verifier.toByteArray(Charsets.US_ASCII))
        .encodeBase64Url()
}
