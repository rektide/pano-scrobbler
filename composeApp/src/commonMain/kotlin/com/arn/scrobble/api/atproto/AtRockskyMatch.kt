package com.arn.scrobble.api.atproto

import com.arn.scrobble.api.lastfm.ScrobbleData
import com.arn.scrobble.api.listenbrainz.ListenBrainz.Companion.ISRC
import com.arn.scrobble.api.listenbrainz.ListenBrainz.Companion.MUSICBRAINZ_TRACKID
import kotlinx.serialization.json.JsonObject

private val ScrobbleData.recording get() = mbid(extraTags[MUSICBRAINZ_TRACKID])
private val ScrobbleData.isrc get() = atIsrc(extraTags[ISRC])
internal val ScrobbleData.canMatchRocksky get() = (recording != null || isrc != null) && track.atField(512) != null && artist.atField(256) != null

internal suspend fun AtWire.matchRocksky(data: ScrobbleData): JsonObject {
    checkAt(data.canMatchRocksky, AtProblem.INPUT)
    val url = secureUrl(ROCKSKY_VIEW).xrpc("app.rocksky.song.matchSong")
        .addQueryParameter("title", data.track).addQueryParameter("artist", data.artist).apply {
            data.album.atField(256)?.let { addQueryParameter("album", it) }
            data.recording?.let { addQueryParameter("mbId", it) }
            data.isrc?.let { addQueryParameter("isrc", it) }
        }.build()
    val reply = get(url)
    checkAt(reply.ok, if (reply.transient) AtProblem.NETWORK else AtProblem.INPUT)
    return verifiedRockskyMatch(data, reply.json).playRecord(AtTarget.ROCKSKY)
}

/** Accept only an echoed requested identity, no conflicting ID or known album, and no name-only matches. */
internal fun verifiedRockskyMatch(data: ScrobbleData, hit: JsonObject): ScrobbleData {
    val recording = setOfNotNull(mbid(hit.text("mbId")), mbid(hit.text("mbid")))
    val isrc = atIsrc(hit.text("isrc"))
    checkAt(recording.size <= 1 && (data.recording == null || recording.isEmpty() || data.recording in recording) &&
        (data.isrc == null || isrc == null || isrc == data.isrc) &&
        (data.recording in recording || (data.isrc != null && data.isrc == isrc)), AtProblem.INPUT)
    val knownAlbum = data.album.atField(256)
    val matchedAlbum = hit.text("album").atField(256)
    checkAt(knownAlbum == null || matchedAlbum?.equals(knownAlbum, ignoreCase = true) == true, AtProblem.INPUT)
    return data.copy(album = knownAlbum ?: matchedAlbum,
        albumArtist = data.albumArtist.atField(256) ?: hit.text("albumArtist").atField(256),
        duration = data.duration?.takeIf { it > 0 } ?: hit.number("duration")?.takeIf { it > 0 })
}
