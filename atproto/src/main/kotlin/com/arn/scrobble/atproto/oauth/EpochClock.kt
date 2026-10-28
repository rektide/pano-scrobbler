package com.arn.scrobble.atproto.oauth

internal fun interface EpochClock {
    fun millis(): Long

    object System : EpochClock {
        override fun millis(): Long = java.lang.System.currentTimeMillis()
    }
}
