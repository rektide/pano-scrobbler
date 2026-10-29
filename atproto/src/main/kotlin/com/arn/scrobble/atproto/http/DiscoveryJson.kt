package com.arn.scrobble.atproto.http

import kotlinx.serialization.json.Json

/** Preflight bounded discovery JSON before the library parser can erase duplicate keys. */
internal fun requireUnambiguousDiscoveryJson(text: String) {
    DiscoveryJsonScanner(text).check()
}

private class DiscoveryJsonScanner(private val text: String) {
    private var offset = 0

    fun check() {
        value(0)
        whitespace()
        require(offset == text.length) { "Unexpected data after discovery JSON" }
    }

    private fun value(depth: Int) {
        require(depth <= 32) { "Discovery JSON is nested too deeply" }
        whitespace()
        when (peek()) {
            '{' -> {
                offset++
                val keys = hashSetOf<String>()
                whitespace()
                if (consume('}')) return
                do {
                    whitespace()
                    val key = Json.decodeFromString<String>(string())
                    require(keys.add(key)) { "Duplicate discovery JSON object key" }
                    whitespace()
                    require(consume(':')) { "Expected discovery JSON object value" }
                    value(depth + 1)
                    whitespace()
                } while (consume(','))
                require(consume('}')) { "Unclosed discovery JSON object" }
            }
            '[' -> {
                offset++
                whitespace()
                if (consume(']')) return
                do {
                    value(depth + 1)
                    whitespace()
                } while (consume(','))
                require(consume(']')) { "Unclosed discovery JSON array" }
            }
            '"' -> string()
            else -> {
                // The regular JSON parser validates literals/numbers and string
                // escapes. Here we only need their boundaries and bounded depth.
                val start = offset
                while (offset < text.length && text[offset] !in ",]} \t\r\n") offset++
                require(offset > start) { "Missing discovery JSON value" }
            }
        }
    }

    private fun string(): String {
        val start = offset
        require(consume('"')) { "Expected discovery JSON string" }
        while (offset < text.length) {
            when (text[offset++]) {
                '"' -> return text.substring(start, offset)
                '\\' -> {
                    require(offset < text.length) { "Unclosed discovery JSON escape" }
                    offset++
                }
            }
        }
        throw IllegalArgumentException("Unclosed discovery JSON string")
    }

    private fun whitespace() {
        while (offset < text.length && text[offset] in " \t\r\n") offset++
    }

    private fun peek(): Char? = text.getOrNull(offset)
    private fun consume(char: Char): Boolean = (peek() == char).also { if (it) offset++ }
}
