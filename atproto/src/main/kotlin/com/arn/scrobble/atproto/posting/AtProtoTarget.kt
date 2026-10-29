package com.arn.scrobble.atproto.posting

import kotlinx.serialization.Serializable

@Serializable
enum class AtProtoTarget(val collection: String) {
    TEAL("fm.teal.feed.play"), ROCKSKY("app.rocksky.scrobble");

    companion object {
        fun grantedBy(scope: Set<String>, selectedTargets: Set<AtProtoTarget> = entries.toSet()): Set<AtProtoTarget> =
            grantedBy(scope.joinToString(" "), selectedTargets)

        fun grantedBy(scope: String, selectedTargets: Set<AtProtoTarget> = entries.toSet()): Set<AtProtoTarget> {
            // RFC 6749 scope tokens are space-separated, case-sensitive ASCII.
            if (scope.any { it != ' ' && it !in '\u0021'..'\u007e' || it == '"' || it == '\\' }) return emptySet()
            val values = scope.split(' ')
            if (values.any { it.isEmpty() } || "atproto" !in values) return emptySet()
            if ("transition:generic" in values) return entries.filterTo(linkedSetOf()) { it in selectedTargets }
            val permissions = values.mapNotNull(::parseRepoCreateGrant)
            return entries.filterTo(linkedSetOf()) { target ->
                target in selectedTargets && permissions.any { "*" in it || target.collection in it }
            }
        }
    }
}
