package com.arn.scrobble.atproto.posting

// Local metadata ingestion ceiling, not a Lexicon string limit. Check length
// before scanning/copying player metadata so hostile inputs have bounded cost.
private const val MAX_SOURCE_TEXT_CHARS = 16 * 1024

internal fun String.requireRecordText(label: String) {
    require(length <= MAX_SOURCE_TEXT_CHARS) { "Play $label metadata is too long" }
    var index = 0
    while (index < length) {
        val char = this[index++]
        when {
            Character.isHighSurrogate(char) -> {
                require(index < length && Character.isLowSurrogate(this[index])) { "Play $label contains malformed Unicode" }
                index++
            }
            Character.isLowSurrogate(char) -> throw IllegalArgumentException("Play $label contains malformed Unicode")
        }
    }
}

internal fun String?.cleanOptional(maxBytes: Int): String? {
    if (this == null) return null
    requireRecordText("optional text")
    return trim().takeIf { it.isNotEmpty() }?.truncateUtf8(maxBytes)
}

/** Single forward pass; never encode successively shorter copies of a large input. */
internal fun String.truncateUtf8(maxBytes: Int): String {
    require(maxBytes >= 0)
    var end = 0
    var bytes = 0
    while (end < length) {
        val point = codePointAt(end)
        val width = when {
            point <= 0x7f -> 1
            point <= 0x7ff -> 2
            point <= 0xffff -> 3
            else -> 4
        }
        if (bytes + width > maxBytes) break
        bytes += width
        end += Character.charCount(point)
    }
    return if (end == length) this else substring(0, end)
}
