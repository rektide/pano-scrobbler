package com.arn.scrobble.atproto.posting

import java.net.URLDecoder

/** The currently understood repo permission, not a general permission-set resolver. */
internal fun parseRepoCreateGrant(scope: String): Set<String>? {
    val head = scope.substringBefore('?')
    if (head != "repo" && !head.startsWith("repo:")) return null
    val collections = mutableListOf<String>()
    val actions = mutableListOf<String>()
    val positional = head.startsWith("repo:")
    if (positional) collections += decodeScopePart(head.removePrefix("repo:")) ?: return null

    val query = scope.substringAfter('?', "")
    if (query.isNotEmpty()) {
        for (parameter in query.split('&')) {
            if ('=' !in parameter) return null
            val name = decodeScopePart(parameter.substringBefore('=')) ?: return null
            val value = decodeScopePart(parameter.substringAfter('=')) ?: return null
            when (name) {
                "collection" -> {
                    if (positional) return null
                    collections += value
                }
                "action" -> actions += value
                // An unknown parameter might attenuate access. Never ignore it.
                else -> return null
            }
        }
    }
    if (collections.isEmpty() || collections.any { it != "*" && !isNsid(it) }) return null
    if (actions.any { it !in setOf("create", "update", "delete") } || actions.distinct().size != actions.size) return null
    if (actions.isNotEmpty() && "create" !in actions) return null
    return collections.toSet()
}

private fun decodeScopePart(value: String): String? = try {
    URLDecoder.decode(value, "UTF-8")
} catch (_: IllegalArgumentException) {
    null
}

private fun isNsid(value: String): Boolean {
    if (value.length > 317) return false
    val labels = value.split('.')
    return labels.size >= 3 && value.substringBeforeLast('.').length <= 253 && labels.dropLast(1).all { label ->
        label.length in 1..63 && label.first().isAsciiLetterOrDigit() && label.last().isAsciiLetterOrDigit() &&
            label.all { it.isAsciiLetterOrDigit() || it == '-' }
    } && labels.first().first().isAsciiLetter() &&
        labels.last().let { name -> name.length in 1..63 && name.first().isAsciiLetter() && name.all { it.isAsciiLetterOrDigit() } }
}

private fun Char.isAsciiLetter() = this in 'a'..'z' || this in 'A'..'Z'
private fun Char.isAsciiLetterOrDigit() = isAsciiLetter() || this in '0'..'9'
