package com.arn.scrobble.onboarding

import com.arn.scrobble.api.atproto.isNativeAtprotoCallback
import kotlinx.coroutines.channels.Channel

/** Hands native returns to the login screen without putting credentials in a navigation key. */
internal object AtprotoOAuthCallback {
    val inbox = Channel<String>(Channel.CONFLATED)

    fun deliver(url: String): Boolean {
        if (!url.isNativeAtprotoCallback()) return false
        return inbox.trySend(url).isSuccess
    }
}
