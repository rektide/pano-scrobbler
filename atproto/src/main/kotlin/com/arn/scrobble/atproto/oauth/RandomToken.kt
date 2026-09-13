package com.arn.scrobble.atproto.oauth

import java.security.SecureRandom
import java.util.random.RandomGenerator

private const val URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
private val secureRandom = SecureRandom()

internal fun randomUrlToken(
    length: Int,
    random: RandomGenerator = secureRandom,
): String {
    require(length > 0) { "Token length must be positive" }

    return buildString(length) {
        repeat(length) {
            append(URL_ALPHABET[random.nextInt(URL_ALPHABET.length)])
        }
    }
}
