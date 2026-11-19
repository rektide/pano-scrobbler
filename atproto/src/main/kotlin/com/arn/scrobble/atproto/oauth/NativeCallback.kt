package com.arn.scrobble.atproto.oauth

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URISyntaxException

internal class NativeCallback private constructor(
    val state: String,
    val issuer: String?,
    val code: String?,
    val error: String?,
) {
    override fun toString() = "NativeCallback(<redacted>)"

    companion object {
        fun parse(url: String, expectedRedirect: String): NativeCallback {
            require(url.length <= 16 * 1024 && url.none { it.isWhitespace() || it.code < 0x20 }) { "Invalid OAuth callback URL" }
            val uri = try { URI(url) } catch (_: URISyntaxException) {
                // URI's exception message contains its entire input, including code/state.
                // Do not retain that exception as a cause or suppressed exception.
                throw IllegalArgumentException("Invalid OAuth callback URL")
            }
            require(uri.rawFragment == null && url.substringBefore('?') == expectedRedirect) { "OAuth callback URL does not match the native redirect" }
            val values = linkedMapOf<String, String>()
            val query = requireNotNull(uri.rawQuery) { "OAuth callback is missing query parameters" }
            for (field in query.split('&')) {
                val equals = field.indexOf('=')
                require(equals > 0) { "Invalid OAuth callback parameter" }
                val name = decode(field.substring(0, equals))
                require(name !in values) { "Duplicate OAuth callback parameter" }
                values[name] = decode(field.substring(equals + 1))
            }
            val state = requireNotNull(values["state"]?.takeIf { it.isNotBlank() }) { "OAuth callback is missing state" }
            require((values["code"] != null) xor (values["error"] != null)) { "OAuth callback must have either code or error" }
            require(values["code"]?.isBlank() != true && values["error"]?.isBlank() != true) { "Empty OAuth callback result" }
            return NativeCallback(state, values["iss"], values["code"], values["error"])
        }

        private fun decode(value: String): String {
            val bytes = ByteArrayOutputStream()
            var index = 0
            while (index < value.length) {
                val char = value[index++]
                when (char) {
                    '%' -> {
                        require(index + 2 <= value.length) { "Invalid callback escaping" }
                        val octet = value.substring(index, index + 2).toIntOrNull(16)
                        require(octet != null && value.substring(index, index + 2).all { it in "0123456789abcdefABCDEF" }) { "Invalid callback escaping" }
                        bytes.write(octet)
                        index += 2
                    }
                    '+' -> bytes.write(0x20)
                    else -> {
                        require(char.code in 0x21..0x7e) { "Invalid callback encoding" }
                        bytes.write(char.code)
                    }
                }
            }
            return bytes.toByteArray().decodeToString(throwOnInvalidSequence = true).also {
                require(it.none { char -> char.code < 0x20 || char.code == 0x7f }) { "Invalid callback control character" }
            }
        }
    }
}
