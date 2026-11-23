package com.arn.scrobble.onboarding

import com.arn.scrobble.api.atproto.ATPROTO_REDIRECT_URI
import kotlinx.coroutines.channels.Channel

/** Hands Android intents to the login screen without putting credentials in a navigation key. */
internal object AtprotoOAuthCallback {
    val inbox = Channel<String>(Channel.CONFLATED)

    fun deliver(url: String): Boolean {
        if (url.length > 16 * 1024 || !url.startsWith("$ATPROTO_REDIRECT_URI?")) return false
        return inbox.trySend(url).isSuccess
    }
}
