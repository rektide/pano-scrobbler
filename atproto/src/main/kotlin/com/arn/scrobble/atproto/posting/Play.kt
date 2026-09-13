package com.arn.scrobble.atproto.posting

import java.net.URI

data class Play(
    val track: String,
    val artist: String,
    val album: String? = null,
    val albumArtist: String? = null,
    val playedAtMillis: Long,
    val durationMillis: Long? = null,
    val trackNumber: Int? = null,
    val originUri: String? = null,
    val musicServiceUri: String? = null,
    val sourceTag: String? = null,
) {
    init {
        require(track.isNotBlank()) { "A play must have a track name" }
        require(artist.isNotBlank()) { "A play must have an artist name" }
        require(playedAtMillis > 0) { "A play must have a positive timestamp" }
        require(durationMillis == null || durationMillis > 0) { "A play duration must be positive" }
        require(trackNumber == null || trackNumber > 0) { "A track number must be positive" }
        originUri?.let { requireAbsoluteUri("origin URI", it) }
        musicServiceUri?.let { requireAbsoluteUri("music service URI", it) }
    }
}

private fun requireAbsoluteUri(label: String, value: String) {
    val uri = URI(value)
    require(uri.isAbsolute) { "Play $label must be an absolute URI" }
}
