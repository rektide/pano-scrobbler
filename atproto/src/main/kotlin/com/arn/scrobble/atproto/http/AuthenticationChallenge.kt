package com.arn.scrobble.atproto.http

import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import java.util.Locale

private val token = Regex("[!#$%&'*+.^_`|~0-9A-Za-z-]+")
private val parameter = Regex("^([!#$%&'*+.^_`|~0-9A-Za-z-]+)[ \\t]*=[ \\t]*(.+)$")
private val challenge = Regex("^([!#$%&'*+.^_`|~0-9A-Za-z-]+)(?:[ \\t]+(.+))?$")

/** Parse challenge boundaries, not substrings in a realm/error_description. */
internal fun Headers.hasDpopError(error: String): Boolean = getAll(HttpHeaders.WWWAuthenticate).orEmpty().any { value ->
    val parts = splitChallenges(value) ?: return@any false
    var scheme: String? = null
    var valid = true
    var parameters = mutableMapOf<String, String>()

    fun matches() = valid && scheme.equals("DPoP", ignoreCase = true) && parameters["error"] == error
    fun addParameter(part: String) {
        val match = parameter.matchEntire(part)
        val name = match?.groupValues?.get(1)?.lowercase(Locale.ROOT)
        val parsed = match?.groupValues?.get(2)?.let(::parseParameterValue)
        if (name == null || parsed == null || parameters.put(name, parsed) != null) valid = false
    }

    for (part in parts) {
        if (parameter.matches(part)) {
            addParameter(part)
        } else {
            if (matches()) return@any true
            val next = challenge.matchEntire(part)
            scheme = next?.groupValues?.get(1)
            parameters = mutableMapOf()
            valid = next != null
            next?.groupValues?.get(2)?.takeIf { it.isNotEmpty() }?.let(::addParameter)
        }
    }
    matches()
}

private fun splitChallenges(value: String): List<String>? {
    val parts = mutableListOf<String>()
    var start = 0
    var quoted = false
    var escaped = false
    for ((index, char) in value.withIndex()) {
        if (char == '\r' || char == '\n') return null
        when {
            escaped -> escaped = false
            quoted && char == '\\' -> escaped = true
            char == '"' -> quoted = !quoted
            !quoted && char == ',' -> {
                value.substring(start, index).trim().takeIf { it.isNotEmpty() }?.let(parts::add)
                start = index + 1
            }
        }
    }
    if (quoted || escaped) return null
    value.substring(start).trim().takeIf { it.isNotEmpty() }?.let(parts::add)
    return parts
}

private fun parseParameterValue(value: String): String? {
    if (token.matches(value)) return value
    if (value.length < 2 || value.first() != '"' || value.last() != '"') return null
    val result = StringBuilder()
    var escaped = false
    for (char in value.substring(1, value.lastIndex)) {
        when {
            escaped -> { result.append(char); escaped = false }
            char == '\\' -> escaped = true
            char == '"' || char.code < 0x20 -> return null
            else -> result.append(char)
        }
    }
    return if (escaped) null else result.toString()
}
