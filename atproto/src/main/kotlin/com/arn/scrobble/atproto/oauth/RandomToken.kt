package com.arn.scrobble.atproto.oauth

import java.security.SecureRandom

private const val URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
private val secureRandom = SecureRandom()

internal fun randomUrlToken(
    length: Int,
    nextInt: (bound: Int) -> Int = secureRandom::nextInt,
): String {
    require(length > 0) { "Token length must be positive" }

    return buildString(length) {
        repeat(length) {
            append(URL_ALPHABET[nextInt(URL_ALPHABET.length)])
        }
    }
}
