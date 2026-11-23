package com.arn.scrobble.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arn.scrobble.api.AccountType
import com.arn.scrobble.api.UserCached
import com.arn.scrobble.api.atproto.AtprotoOAuth
import com.arn.scrobble.api.atproto.AtprotoOAuthAttempts
import com.arn.scrobble.api.atproto.AtprotoOAuthPermission
import com.arn.scrobble.api.atproto.AtprotoSession
import com.arn.scrobble.api.atproto.AtprotoSessions
import com.arn.scrobble.api.atproto.signInWithAppPassword
import com.arn.scrobble.utils.PlatformStuff
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.StringResource
import pano_scrobbler.composeapp.generated.resources.Res
import pano_scrobbler.composeapp.generated.resources.atproto_browser_failed
import pano_scrobbler.composeapp.generated.resources.atproto_callback_expired
import pano_scrobbler.composeapp.generated.resources.atproto_callback_failed
import pano_scrobbler.composeapp.generated.resources.atproto_disconnect_failed
import pano_scrobbler.composeapp.generated.resources.atproto_login_failed

internal class AtprotoLoginViewModel : ViewModel() {
    data class State(
        val busy: Boolean = false,
        val error: StringResource? = null,
        val signedIn: Boolean = false,
        val awaitingBrowser: Boolean = false,
    )

    private val sessions = AtprotoSessions()
    private val oauth = AtprotoOAuth()
    private val attempts = AtprotoOAuthAttempts.shared
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                operationLock.withLock { checkPending() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                mutableState.update { it.copy(error = Res.string.atproto_browser_failed) }
            }
        }
    }

    fun signInWithPassword(handle: String, password: String, destinations: Set<AccountType>) {
        run(Res.string.atproto_login_failed) {
            attempts.clear()
            val normalizedHandle = handle.trim().removePrefix("@").lowercase()
            val session = signInWithAppPassword(normalizedHandle, password)
            connect(session, normalizedHandle, destinations)
        }
    }

    fun startBrowserSignIn(handle: String, destinations: Set<AccountType>) = run(Res.string.atproto_browser_failed) {
        attempts.clear()
        val permissions = buildSet {
            if (AccountType.TEAL in destinations) add(AtprotoOAuthPermission.TEAL_SCROBBLES)
            if (AccountType.ROCKSKY in destinations) add(AtprotoOAuthPermission.ROCKSKY_SCROBBLES)
        }
        require(permissions.isNotEmpty())
        val signIn = oauth.start(handle, permissions)
        attempts.save(signIn.attempt)
        mutableState.update { it.copy(awaitingBrowser = true) }
        PlatformStuff.openInBrowser(signIn.authorizationUrl)
    }

    fun finishBrowserSignIn(callback: String, fromBrowser: Boolean = false) = run(Res.string.atproto_callback_failed, queue = fromBrowser) {
        val attempt = attempts.load()
        if (attempt == null) {
            mutableState.update { it.copy(error = Res.string.atproto_callback_expired, awaitingBrowser = false) }
            return@run
        }
        try {
            val session = oauth.resume(attempt).complete(callback.trim()) { attempts.consume(attempt.state) }
            val destinations = buildSet {
                if (AtprotoOAuthPermission.TEAL_SCROBBLES in attempt.permissions) add(AccountType.TEAL)
                if (AtprotoOAuthPermission.ROCKSKY_SCROBBLES in attempt.permissions) add(AccountType.ROCKSKY)
            }
            connect(session, attempt.handle, destinations)
        } finally {
            checkPending()
        }
    }

    fun cancelBrowserSignIn() = run(Res.string.atproto_browser_failed) { attempts.clear() }

    fun disconnect() = run(Res.string.atproto_disconnect_failed) {
        attempts.clear()
        sessions.disconnect()
    }

    private suspend fun checkPending() {
        val awaiting = attempts.load() != null
        mutableState.update { it.copy(awaitingBrowser = awaiting) }
    }

    fun acknowledgeSignIn() = mutableState.update { it.copy(signedIn = false) }

    private suspend fun connect(session: AtprotoSession, handle: String, destinations: Set<AccountType>) {
        sessions.connect(session, UserCached(handle, "", "", "", 0), destinations)
        mutableState.update { it.copy(signedIn = true) }
    }

    private fun run(error: StringResource, queue: Boolean = false, action: suspend () -> Unit) {
        if (mutableState.value.busy && !queue) return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            operationLock.withLock {
                try {
                    action()
                    checkPending()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Provider responses/exceptions can contain credentials. Surface a fixed message.
                    mutableState.update { it.copy(error = error) }
                } finally {
                    mutableState.update { it.copy(busy = false) }
                }
            }
        }
    }

    companion object {
        private val operationLock = Mutex()
    }
}
