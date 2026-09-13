package com.arn.scrobble.atproto.encoding

import java.util.Base64

internal fun ByteArray.encodeBase64Url(): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(this)

internal fun String.decodeBase64Url(): ByteArray =
    Base64.getUrlDecoder().decode(this)
