package com.arn.scrobble.api.atproto

import com.arn.scrobble.api.AccountType
import com.arn.scrobble.api.lastfm.ScrobbleData
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.security.SecureRandom
import java.time.Instant

class AtprotoPlayRejected(message: String) : Exception(message)
class AtprotoReconnect : Exception("Sign in again with the same AT Protocol account")
class AtprotoPermissionMissing : Exception("Authorize this AT Protocol destination to send plays")

/** The queue keeps this address across restarts. It is not derived from the song or its play time. */
object AtprotoEventTid {
    private const val alphabet = "234567abcdefghijklmnopqrstuvwxyz"
    private val clockId = SecureRandom().nextInt(1024)
    private var lastMicros = 0L

    @Synchronized
    fun next(): String {
        val now = Instant.now()
        lastMicros = maxOf(lastMicros + 1, now.epochSecond * 1_000_000 + now.nano / 1000)
        val id = (lastMicros shl 10) or clockId.toLong()
        return CharArray(13) { alphabet[((id ushr (60 - it * 5)) and 31).toInt()] }.concatToString()
    }
}

/** Keep this mapping stable: retries reconstruct the same record from the persisted ScrobbleData. */
fun ScrobbleData.atprotoPlay(type: AccountType): JsonObject {
    val started = Instant.ofEpochMilli(timestamp).toString()
    fun String?.required(field: String, maxBytes: Int): String =
        this?.takeIf { it.isNotBlank() && it.encodeToByteArray().size <= maxBytes }
            ?: throw AtprotoPlayRejected("Missing or invalid $field for ${type.playCollection}")

    return buildJsonObject {
        put("\$type", type.playCollection)
        if (type == AccountType.TEAL) {
            put("trackName", track.required("title", 256))
            put("artists", buildJsonArray { add(buildJsonObject { put("artistName", artist.required("artist", 256)) }) })
            put("playedTime", started)
            album?.takeIf { it.isNotBlank() && it.encodeToByteArray().size <= 256 }?.let { put("releaseName", it) }
            duration?.div(1000)?.takeIf { it > 0 }?.let { put("duration", it) }
        } else {
            put("title", track.required("title", 512))
            put("artist", artist.required("artist", 256))
            put("album", album.required("album", 256))
            put("albumArtist", albumArtist.required("album artist", 256))
            put("duration", duration?.takeIf { it > 0 }
                ?: throw AtprotoPlayRejected("Rocksky requires a known track duration"))
            put("createdAt", started)
            trackNumber?.takeIf { it > 0 }?.let { put("trackNumber", it) }
        }
        extraTags.entries.firstOrNull { it.key.equals("isrc", ignoreCase = true) }
            ?.value?.takeIf { it.isNotBlank() && it.encodeToByteArray().size <= 32 }?.let { put("isrc", it) }
    }
}
