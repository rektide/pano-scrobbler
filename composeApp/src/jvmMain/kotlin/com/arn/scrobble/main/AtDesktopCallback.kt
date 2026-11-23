package com.arn.scrobble.main

import com.arn.scrobble.api.atproto.AT_CALLBACK
import com.arn.scrobble.api.atproto.atAuthority
import com.arn.scrobble.api.atproto.parseAtCallback
import com.arn.scrobble.utils.DesktopStuff
import com.arn.scrobble.utils.Stuff
import kotlinx.coroutines.runBlocking

private const val CALLBACK_ARG = "--atproto-callback"
private val callbackScheme = AT_CALLBACK.substringBefore(':') + ":"

/** Consume the entire activation before generic argv handling, even for malformed callbacks. */
internal fun handleDesktopAtCallback(
    args: Array<String>,
    complete: (String) -> Unit,
    diagnostic: (String) -> Unit = System.err::println,
): Boolean {
    if (args.none { it.startsWith(CALLBACK_ARG) || it.contains(callbackScheme, ignoreCase = true) }) return false
    val uri = when {
        args.size == 1 && args[0].startsWith(callbackScheme) -> args[0]
        args.size == 2 && args[0] == CALLBACK_ARG -> args[1]
        else -> null
    }
    try {
        if (uri == null) {
            diagnostic("AT Protocol callback rejected.")
        } else {
            parseAtCallback(uri)
            complete(uri)
        }
    } catch (_: Exception) {
        // No throwable, argv, URI, or OAuth response may reach stderr or native IPC logs.
        diagnostic("AT Protocol callback rejected.")
    }
    return true
}

internal fun completeDesktopAtCallback(uri: String) {
    // Native activation addresses only the default profile. Never accept profile/automation flags
    // from the browser. Custom data directories finish in their own UI by pasting the callback.
    DesktopStuff.parseCmdlineArgs(emptyArray())
    DesktopStuff.setSystemProperties()
    runBlocking {
        Stuff.initializeMainPrefsCache()
        // This invocation never launches a second GUI, tray, native IPC server, or media listener.
        // The application's profile vault lock serializes this with the running app and other callbacks.
        atAuthority.finishBrowser(uri, wakeWorker = false)
    }
}
