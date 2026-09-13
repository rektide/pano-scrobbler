package com.arn.scrobble.atproto.identity

import java.util.Locale

internal fun normalizeHandle(value: String): String {
    val handle = value.trim().removePrefix("@").lowercase(Locale.ROOT)
    require(handle.length in 3..253) { "Invalid AT Protocol handle" }

    val labels = handle.split('.')
    require(labels.size >= 2) { "Invalid AT Protocol handle" }
    require(labels.all(::isValidHandleLabel)) { "Invalid AT Protocol handle" }
    require(labels.last().first().isAsciiLetter()) { "Invalid AT Protocol handle" }

    return handle
}

private fun isValidHandleLabel(label: String): Boolean =
    label.length in 1..63 &&
        label.first().isAsciiLetterOrDigit() &&
        label.last().isAsciiLetterOrDigit() &&
        label.all { it.isAsciiLetterOrDigit() || it == '-' }

private fun Char.isAsciiLetterOrDigit() = isAsciiLetter() || this in '0'..'9'

private fun Char.isAsciiLetter() = this in 'A'..'Z' || this in 'a'..'z'
