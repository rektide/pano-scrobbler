package com.arn.scrobble.api.atproto

import com.arn.scrobble.api.lastfm.ScrobbleData
import com.arn.scrobble.api.listenbrainz.ListenBrainz.Companion.ISRC
import com.arn.scrobble.api.listenbrainz.ListenBrainz.Companion.MUSICBRAINZ_ALBUMID
import com.arn.scrobble.api.listenbrainz.ListenBrainz.Companion.MUSICBRAINZ_ARTISTID
import com.arn.scrobble.api.listenbrainz.ListenBrainz.Companion.MUSICBRAINZ_RELEASETRACKID
import com.arn.scrobble.api.listenbrainz.ListenBrainz.Companion.MUSICBRAINZ_TRACKID
import kotlinx.serialization.json.*
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.parseOrNull
import java.math.BigInteger
import java.security.SecureRandom
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/** AT Protocol TID = 53-bit microsecond time + 10-bit clock, radix32-sort encoding. */
internal class AtTid(private val clock: () -> Long = System::currentTimeMillis) {
    private val last = AtomicLong()
    val clockId = SecureRandom().nextInt(1024)

    fun next(): String {
        val observed = clock() * 1000
        val micros = last.updateAndGet { maxOf(observed, it + 1) }
        return atTidAt(micros, clockId)
    }
}

internal fun atTidAt(micros: Long, clockId: Int): String {
    require(micros in 1..((1L shl 53) - 1) && clockId in 0..1023)
    val radix = "0123456789abcdefghijklmnopqrstuv"
    val sorted = "234567abcdefghijklmnopqrstuvwxyz"
    return BigInteger.valueOf((micros shl 10) or clockId.toLong()).toString(32)
        .padStart(13, '0').map { sorted[radix.indexOf(it)] }.joinToString("")
}

internal data class AtAddress(val did: String, val collection: String, val key: String) {
    val uri: String get() = "at://$did/$collection/$key"
    fun accepts(raw: String?): Boolean {
        if (raw == null || raw != uri || !key.matches(Regex("[a-zA-Z0-9._~:-]{1,512}")) || key in setOf(".", "..")) return false
        val parts = AtUri(raw).parseOrNull() ?: return false
        return parts.repo.raw == did && parts.collection?.raw == collection && parts.rkey?.raw == key && parts.fragment == null
    }
    fun body(extra: JsonObjectBuilder.() -> Unit = {}) = buildJsonObject {
        put("repo", did); put("collection", collection); put("rkey", key)
        extra()
    }
}

// CIDv1 DAG-CBOR SHA-256 in canonical base32lower (unused low bits are zero).
private val recordCid = Regex("bafyrei[a-z2-7]{51}[aeimquy4]")
internal data class AtRef(val address: AtAddress, val cid: String) {
    companion object {
        fun read(json: JsonObject, expected: AtAddress): AtRef {
            val cid = json.text("cid")
            checkAt(expected.accepts(json.text("uri")) && cid != null && recordCid.matches(cid))
            return AtRef(expected, cid!!)
        }
    }
}

internal fun ScrobbleData.playRecord(target: AtTarget): JsonObject {
    val title = track.atField(if (target == AtTarget.TEAL) 256 else 512) ?: throw AtFailure(AtProblem.INPUT)
    val timestamp = Instant.ofEpochMilli(timestamp).toString()
    val artist = artist.atField(256)
    val album = album.atField(256)
    val recording = mbid(extraTags[MUSICBRAINZ_TRACKID])
    val isrc = atIsrc(extraTags[ISRC])
    val artistNames = extraTags["org.musicbrainz.ARTISTS"]?.split('\u0000', ';')?.mapNotNull { it.atField(256) }
        ?.takeIf { it.isNotEmpty() } ?: listOfNotNull(artist)
    val artistIds = extraTags[MUSICBRAINZ_ARTISTID]?.split(';', ',', ' ', '\u0000')?.mapNotNull(::mbid)
        ?.takeIf { it.size == artistNames.size }
    return buildJsonObject {
        put("\$type", target.play)
        if (isrc != null) put("isrc", isrc)
        when (target) {
            AtTarget.TEAL -> {
                put("trackName", title)
                put("playedTime", timestamp)
                put("artists", artistArray(artistNames, artistIds))
                duration?.takeIf { it > 0 }?.let { put("duration", it / 1000) }
                album?.let { put("releaseName", it) }
                recording?.let { put("recordingMbId", "mbid:$it") }
                mbid(extraTags[MUSICBRAINZ_ALBUMID])?.let { put("releaseMbId", "mbid:$it") }
                mbid(extraTags[MUSICBRAINZ_RELEASETRACKID])?.let { put("trackMbId", "mbid:$it") }
            }
            AtTarget.ROCKSKY -> {
                put("title", title)
                put("artist", artist ?: throw AtFailure(AtProblem.INPUT))
                put("album", album ?: throw AtFailure(AtProblem.INPUT))
                put("albumArtist", albumArtist.atField(256) ?: throw AtFailure(AtProblem.INPUT))
                put("duration", duration?.takeIf { it > 0 } ?: throw AtFailure(AtProblem.INPUT))
                put("createdAt", timestamp)
                recording?.let { put("mbid", it) }
                artistIds?.let { ids -> put("artists", buildJsonArray {
                    artistNames.zip(ids).forEach { (name, id) -> add(buildJsonObject { put("name", name); put("mbid", id) }) }
                }) }
                trackNumber?.takeIf { it > 0 }?.let { put("trackNumber", it) }
            }
        }
    }
}

private fun artistArray(names: List<String>, ids: List<String>?) = buildJsonArray {
    names.forEachIndexed { i, name -> add(buildJsonObject {
        put("artistName", name)
        ids?.get(i)?.let { put("artistMbId", "mbid:$it") }
    }) }
}

internal fun String?.atField(max: Int): String? = this?.trim()?.takeIf { value ->
    value.isNotEmpty() && value.encodeToByteArray().size <= max && !value.any { it == '\u0000' } &&
        value == value.encodeToByteArray().decodeToString()
}

private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
internal fun mbid(raw: String?) = raw?.takeIf(uuid::matches)?.lowercase()
internal fun atIsrc(raw: String?) = raw?.trim()?.uppercase()?.takeIf { it.matches(Regex("[A-Z]{2}[A-Z0-9]{3}[0-9]{7}")) }

internal fun equalRecord(expected: JsonObject, actual: JsonObject): Boolean {
    if (expected.keys != actual.keys) return false
    return expected.all { (key, value) ->
        if (key in setOf("playedTime", "createdAt")) {
            val a = instantMs(expected.text(key)); a != null && a == instantMs(actual.text(key))
        } else value == actual[key]
    }
}

internal fun instantMs(raw: String?): Long? = if (raw == null) null else runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
