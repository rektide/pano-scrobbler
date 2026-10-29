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
        track.requireRecordText("track")
        artist.requireRecordText("artist")
        album?.requireRecordText("album")
        albumArtist?.requireRecordText("album artist")
        sourceTag?.requireRecordText("source tag")
        require(track.isNotBlank()) { "A play must have a track name" }
        require(artist.isNotBlank()) { "A play must have an artist name" }
        // Lexicon datetime uses a four-digit nonnegative year, not Instant's
        // extended signed-year notation. Retain Pano's post-epoch input policy.
        require(playedAtMillis in 1..253_402_300_799_999L) { "A play timestamp must be between the epoch and year 9999" }
        // Interoperate losslessly with JS-based PDSes/consumers (data-model best practice).
        require(durationMillis == null || durationMillis in 1..9_007_199_254_740_991L) {
            "A play duration must be a positive safely representable integer"
        }
        require(trackNumber == null || trackNumber > 0) { "A track number must be positive" }
        originUri?.let { requireAbsoluteUri("origin URI", it) }
        musicServiceUri?.let { requireAbsoluteUri("music service URI", it) }
    }
}

private fun requireAbsoluteUri(label: String, value: String) {
    value.requireRecordText(label)
    require(value.length <= 8192 && value.toByteArray(Charsets.UTF_8).size <= 8192) {
        "Play $label must not exceed the Lexicon 8 KiB URI limit"
    }
    val uri = URI(value)
    require(uri.isAbsolute) { "Play $label must be an absolute URI" }
}
