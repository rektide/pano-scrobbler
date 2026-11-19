package com.arn.scrobble.atproto.encoding

import kotlin.io.encoding.Base64
import kotlin.io.encoding.Base64.PaddingOption

private val base64Url = Base64.UrlSafe.withPadding(PaddingOption.ABSENT)

internal fun ByteArray.encodeBase64Url(): String = base64Url.encode(this)
internal fun String.decodeBase64Url(): ByteArray = base64Url.decode(this)
