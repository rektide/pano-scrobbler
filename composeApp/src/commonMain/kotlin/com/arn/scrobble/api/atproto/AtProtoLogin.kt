package com.arn.scrobble.api.atproto

import com.arn.scrobble.atproto.oauth.AtProtoAccount
import com.arn.scrobble.atproto.oauth.AuthorizationLaunch
import com.arn.scrobble.atproto.storage.SessionHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.MessageDigest

/** Process-local UI coordination only. Pending OAuth state and credentials belong to the vault. */
internal class AtProtoLogin(private val accounts: PanoAtProtoAccounts, private val scope: CoroutineScope) {
    data class State(
        val busy: Boolean = false,
        val launch: AuthorizationLaunch? = null,
        val browserPending: Boolean = false,
        val completed: AtProtoAccount? = null,
        val error: String? = null,
        val publication: AtProtoAccount? = null,
    )

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private val monitor = Any()
    private var operation = 0L
    private var completing = false
    private var completedCallback: List<Byte>? = null

    fun begin(identifier: String) {
        val id = synchronized(monitor) {
            if (mutableState.value.busy) return
            mutableState.value = State(busy = true)
            ++operation
        }
        scope.launch {
            try { finish(id, State(launch = accounts.authority.beginAuthorization(identifier), browserPending = true)) }
            catch (cause: CancellationException) { throw cause }
            catch (_: Exception) { finish(id, State(error = "Could not begin authorization. Check the account, network and published client metadata.")) }
        }
    }

    fun completeCallback(callback: String) {
        val fingerprint = if (callback.length <= 16 * 1024)
            MessageDigest.getInstance("SHA-256").digest(callback.toByteArray(Charsets.UTF_8)).toList() else null
        val id = synchronized(monitor) {
            if (completing || fingerprint != null && completedCallback == fingerprint) return
            completing = true
            mutableState.value = State(busy = true)
            ++operation
        }
        scope.launch {
            try { finish(id, State(completed = accounts.complete(callback)), fingerprint) }
            catch (cause: CancellationException) { throw cause }
            catch (cause: AtProtoPublicationException) { finish(id, State(publication = cause.account,
                error = "Authorization was saved, but the account reference could not be published. Retry publication."), fingerprint) }
            catch (_: Exception) { finish(id, State(error = "Authorization could not be completed. Check the callback or start a new login.")) }
        }
    }

    fun retryPublication() {
        val (id, account) = synchronized(monitor) {
            val account = mutableState.value.publication ?: return
            if (mutableState.value.busy) return
            mutableState.value = State(busy = true, publication = account)
            ++operation to account
        }
        scope.launch {
            try { accounts.publish(account.session); finish(id, State(completed = account)) }
            catch (cause: CancellationException) { throw cause }
            catch (_: Exception) { finish(id, State(publication = account,
                error = "The saved authorization could not be published. Retry, or start a new login if it was removed or replaced.")) }
        }
    }

    /** Retain the URL for explicit reopening, but never automatically reopen it on screen reentry. */
    fun consumeBrowserLaunch(launch: AuthorizationLaunch): Boolean = synchronized(monitor) {
        val current = mutableState.value
        current.launch == launch && current.browserPending &&
            mutableState.compareAndSet(current, current.copy(browserPending = false))
    }

    /** Navigation can briefly retain two screens; completion is consumed once, never inferred from account presence. */
    fun consumeCompleted(handle: SessionHandle): Boolean = synchronized(monitor) {
        val current = mutableState.value
        current.completed?.session == handle && mutableState.compareAndSet(current, State())
    }

    private fun finish(id: Long, state: State, fingerprint: List<Byte>? = null) = synchronized(monitor) {
        if (operation == id) {
            completing = false
            if (fingerprint != null) completedCallback = fingerprint
            mutableState.value = state
        }
    }
}
