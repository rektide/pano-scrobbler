package com.arn.scrobble.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arn.scrobble.api.AccountType
import com.arn.scrobble.api.UserCached
import com.arn.scrobble.api.atproto.AtprotoSessions
import com.arn.scrobble.api.atproto.signInWithAppPassword
import com.arn.scrobble.work.PendingScrobblesWork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.StringResource
import pano_scrobbler.composeapp.generated.resources.Res
import pano_scrobbler.composeapp.generated.resources.atproto_disconnect_failed
import pano_scrobbler.composeapp.generated.resources.atproto_login_failed

class AtprotoLoginViewModel : ViewModel() {
    data class State(
        val busy: Boolean = false,
        val error: StringResource? = null,
        val signedIn: Boolean = false,
    )

    private val sessions = AtprotoSessions()
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    fun signInWithPassword(handle: String, password: String, destinations: Set<AccountType>) {
        run(Res.string.atproto_login_failed) {
            val normalizedHandle = handle.trim().removePrefix("@").lowercase()
            val session = signInWithAppPassword(normalizedHandle, password)
            sessions.connect(session, UserCached(normalizedHandle, "", "", "", 0), destinations)
            PendingScrobblesWork.schedule(false)
            mutableState.update { it.copy(signedIn = true) }
        }
    }

    fun disconnect() = run(Res.string.atproto_disconnect_failed) {
        sessions.disconnect()
    }

    fun acknowledgeSignIn() = mutableState.update { it.copy(signedIn = false) }

    private fun run(error: StringResource, action: suspend () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            operationLock.withLock {
                try {
                    action()
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
