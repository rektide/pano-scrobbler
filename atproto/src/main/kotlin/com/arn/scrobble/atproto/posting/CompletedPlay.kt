package com.arn.scrobble.atproto.posting

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.MessageDigest
import kotlin.time.Instant

@Serializable
enum class AtProtoTarget(val collection: String) {
    TEAL("fm.teal.feed.play"), ROCKSKY("app.rocksky.scrobble");

    companion object {
        // Conservative literal subset until the independently hardened permission parser is adopted.
        fun grantedBy(scope: Set<String>): Set<AtProtoTarget> = entries.filterTo(linkedSetOf()) {
            "repo:${it.collection}?action=create" in scope
        }
    }
}

@Serializable
data class CompletedPlay(
    val track: String,
    val artist: String,
    val album: String? = null,
    val albumArtist: String? = null,
    val playedAtMillis: Long,
    val durationMillis: Long? = null,
    val trackNumber: Int? = null,
    val sourceTag: String? = null,
) {
    init { require(track.isNotBlank() && artist.isNotBlank() && playedAtMillis > 0) { "Invalid completed play" } }

    fun id(): String = MessageDigest.getInstance("SHA-256").digest(Json.encodeToString(this).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    fun record(target: AtProtoTarget, clientAgent: String): JsonObject = buildJsonObject {
        put("\$type", target.collection)
        val artist = artist.trim().bounded(256)
        val time = Instant.fromEpochMilliseconds(playedAtMillis).toString()
        when (target) {
            AtProtoTarget.TEAL -> {
                put("trackName", track.trim().bounded(256))
                put("artists", buildJsonArray { add(buildJsonObject { put("artistName", artist) }) })
                album?.trim()?.takeIf { it.isNotEmpty() }?.let { put("releaseName", it.bounded(256)) }
                durationMillis?.takeIf { it > 0 }?.let { put("duration", (it / 1000).coerceAtLeast(1)) }
                put("submissionClientAgent", clientAgent.bounded(256))
                put("playedTime", time)
            }
            AtProtoTarget.ROCKSKY -> {
                put("title", track.trim().bounded(512)); put("artist", artist)
                put("artists", buildJsonArray { add(buildJsonObject { put("name", artist) }) })
                put("album", album?.trim()?.takeIf { it.isNotEmpty() }?.bounded(256) ?: "Unknown")
                put("albumArtist", albumArtist?.trim()?.takeIf { it.isNotEmpty() }?.bounded(256) ?: artist)
                put("duration", durationMillis?.takeIf { it > 0 } ?: 1)
                trackNumber?.takeIf { it > 0 }?.let { put("trackNumber", it) }
                sourceTag?.takeIf { it.isNotBlank() }?.let { tag -> put("tags", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(tag.bounded(256))) }) }
                put("createdAt", time)
            }
        }
    }
}

private fun String.bounded(maxBytes: Int): String {
    var end = 0
    var size = 0
    while (end < length) {
        val point = codePointAt(end)
        require(point !in 0xd800..0xdfff) { "Invalid play Unicode" }
        val bytes = when { point < 0x80 -> 1; point < 0x800 -> 2; point < 0x10000 -> 3; else -> 4 }
        if (size + bytes > maxBytes) break
        size += bytes
        end += Character.charCount(point)
    }
    return substring(0, end)
}
