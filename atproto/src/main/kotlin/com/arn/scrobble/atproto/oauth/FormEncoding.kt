package com.arn.scrobble.atproto.oauth

import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal fun formEncode(values: Map<String, String?>): String = values.entries.asSequence()
    .filter { it.value != null }
    .joinToString("&") { (name, value) -> "${encodeFormPart(name)}=${encodeFormPart(requireNotNull(value))}" }

internal fun formDecode(value: String): Map<String, String> = value.split('&').filter { it.isNotEmpty() }
    .associate { field ->
        val separator = field.indexOf('=')
        val name = if (separator >= 0) field.substring(0, separator) else field
        val content = if (separator >= 0) field.substring(separator + 1) else ""
        decodeFormPart(name) to decodeFormPart(content)
    }

private fun encodeFormPart(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
private fun decodeFormPart(value: String): String = URLDecoder.decode(value, StandardCharsets.UTF_8.name())
