package com.arn.scrobble.atproto.posting

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
internal data class TealPlayRecord(
    @SerialName("\$type") val type: String,
    val trackName: String,
    val artists: List<TealArtist>,
    val releaseName: String? = null,
    val duration: Long? = null,
    val originUri: String? = null,
    val musicServiceUri: String? = null,
    val submissionClientAgent: String? = null,
    val playedTime: String,
)

@Serializable
internal data class TealArtist(
    val artistName: String,
)

@Serializable
internal data class RockskyScrobbleRecord(
    @SerialName("\$type") val type: String,
    val title: String,
    val artist: String,
    val artists: List<RockskyArtist>,
    val album: String,
    val albumArtist: String,
    val duration: Long,
    val tags: List<String>? = null,
    val trackNumber: Int? = null,
    val createdAt: String,
)

@Serializable
internal data class RockskyArtist(
    val name: String,
)

internal fun Play.toTealRecord(submissionClientAgent: String?): TealPlayRecord {
    val artistName = artist.trim().truncateUtf8(256)
    return TealPlayRecord(
        type = AtProtoTarget.TEAL.collection,
        trackName = track.trim().truncateUtf8(256),
        artists = listOf(TealArtist(artistName)),
        releaseName = album.cleanOptional(256),
        duration = durationMillis?.div(1_000)?.coerceAtLeast(1),
        originUri = originUri,
        musicServiceUri = musicServiceUri,
        submissionClientAgent = submissionClientAgent.cleanOptional(256),
        playedTime = Instant.fromEpochMilliseconds(playedAtMillis).toString(),
    )
}

internal fun Play.toRockskyRecord(): RockskyScrobbleRecord {
    val artistName = artist.trim().truncateUtf8(256)
    return RockskyScrobbleRecord(
        type = AtProtoTarget.ROCKSKY.collection,
        title = track.trim().truncateUtf8(512),
        artist = artistName,
        artists = listOf(RockskyArtist(artistName)),
        album = album.cleanOptional(256) ?: "Unknown",
        albumArtist = albumArtist.cleanOptional(256) ?: artistName,
        duration = durationMillis ?: 1,
        tags = sourceTag.cleanOptional(256)?.let(::listOf),
        trackNumber = trackNumber,
        createdAt = Instant.fromEpochMilliseconds(playedAtMillis).toString(),
    )
}
