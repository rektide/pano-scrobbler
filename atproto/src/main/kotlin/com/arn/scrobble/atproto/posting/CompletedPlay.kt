package com.arn.scrobble.atproto.posting

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.security.MessageDigest

/** Native pending-play identity plus a narrow adapter to the audited pure mappings. */
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
    init { asPlay() }

    private fun asPlay() = Play(track, artist, album, albumArtist, playedAtMillis, durationMillis, trackNumber, sourceTag = sourceTag)

    fun id(): String = MessageDigest.getInstance("SHA-256").digest(Json.encodeToString(this).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    fun record(target: AtProtoTarget, clientAgent: String): JsonObject = when (target) {
        AtProtoTarget.TEAL -> recordJson.encodeToJsonElement(asPlay().toTealRecord(clientAgent)).jsonObject
        AtProtoTarget.ROCKSKY -> recordJson.encodeToJsonElement(asPlay().toRockskyRecord()).jsonObject
    }
}

private val recordJson = Json { explicitNulls = false }
