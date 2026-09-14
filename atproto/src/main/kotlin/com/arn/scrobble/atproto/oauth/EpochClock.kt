package com.arn.scrobble.atproto.oauth

internal fun interface EpochClock {
    fun millis(): Long

    companion object {
        val System = EpochClock(java.lang.System::currentTimeMillis)
    }
}
