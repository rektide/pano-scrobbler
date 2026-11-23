package com.arn.scrobble.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arn.scrobble.api.AccountType
import com.arn.scrobble.api.UserCached
import com.arn.scrobble.api.atproto.AtprotoSession
import com.arn.scrobble.api.atproto.AtprotoSessions
import com.arn.scrobble.api.atproto.signInWithAppPassword
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import pano_scrobbler.composeapp.generated.resources.Res
import pano_scrobbler.composeapp.generated.resources.atproto_disconnect_failed
import pano_scrobbler.composeapp.generated.resources.atproto_login_failed

internal class AtprotoLoginViewModel : ViewModel() {
    data class State(val busy: Boolean = false, val error: StringResource? = null, val signedIn: Boolean = false)

    private val sessions = AtprotoSessions()
    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()

    fun signInWithPassword(handle: String, password: String, destinations: Set<AccountType>) {
        run(Res.string.atproto_login_failed) {
            val normalizedHandle = handle.trim().removePrefix("@").lowercase()
            val session = signInWithAppPassword(normalizedHandle, password)
            connect(session, normalizedHandle, destinations)
        }
    }

    fun disconnect() = run(Res.string.atproto_disconnect_failed) { sessions.disconnect() }

    fun acknowledgeSignIn() = mutableState.update { it.copy(signedIn = false) }

    private suspend fun connect(session: AtprotoSession, handle: String, destinations: Set<AccountType>) {
        sessions.connect(session, UserCached(handle, "", "", "", 0), destinations)
        mutableState.update { it.copy(signedIn = true) }
    }

    private fun run(error: StringResource, action: suspend () -> Unit) {
        if (mutableState.value.busy) return
        mutableState.value = State(busy = true)
        viewModelScope.launch {
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
