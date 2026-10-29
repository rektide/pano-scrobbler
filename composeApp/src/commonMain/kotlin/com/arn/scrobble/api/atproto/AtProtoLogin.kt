package com.arn.scrobble.api.atproto

import com.arn.scrobble.api.UserAccountSerializable
import com.arn.scrobble.atproto.oauth.AtProtoAccount
import com.arn.scrobble.atproto.oauth.AuthorizationLaunch
import com.arn.scrobble.atproto.storage.SessionHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.MessageDigest

/** Process-local command ownership only. OAuth validation, claims and credentials belong to the vault. */
internal class AtProtoLogin(
    private val accounts: PanoAtProtoAccounts,
    private val scope: CoroutineScope,
    private val now: () -> Long = System::currentTimeMillis,
) {
    data class State(
        val busy: Boolean = false,
        val launch: AuthorizationLaunch? = null,
        val browserPending: Boolean = false,
        val completed: AtProtoAccount? = null,
        val error: String? = null,
        val publication: AtProtoAccount? = null,
    )

    private sealed interface Action {
        class Begin(val identifier: String) : Action
        class Callback(val url: String, val fingerprint: List<Byte>) : Action {
            override fun toString() = "Callback(<redacted>)"
        }
        class Publish(val account: AtProtoAccount) : Action
    }
    private class Work(val action: Action) { lateinit var job: Job }

    private val mutableState = MutableStateFlow(State())
    val state = mutableState.asStateFlow()
    private val monitor = Any()
    private var active: Work? = null
    private val callbacks = ArrayDeque<Action.Callback>()
    private var completedCallback: List<Byte>? = null
    private var loggingOut = 0

    fun begin(identifier: String) {
        val job = synchronized(monitor) {
            if (active != null || loggingOut != 0) return
            mutableState.value = State()
            startLocked(Action.Begin(identifier))
        }
        job.start()
    }

    fun completeCallback(callback: String) {
        // Only an input/queue memory bound here, never a second OAuth validator.
        if (callback.length > 16 * 1024) {
            synchronized(monitor) { mutableState.value = mutableState.value.copy(error = CALLBACK_ERROR) }
            return
        }
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(callback.toByteArray(Charsets.UTF_8)).toList()
        val job = synchronized(monitor) {
            if (loggingOut != 0 || completedCallback == fingerprint ||
                (active?.action as? Action.Callback)?.fingerprint == fingerprint ||
                callbacks.any { it.fingerprint == fingerprint }) return
            val delivery = Action.Callback(callback, fingerprint)
            if (active == null) startLocked(delivery)
            else {
                // Distinct initial/new intents are retained, not dropped behind an unrelated input.
                if (callbacks.size < 4) callbacks.addLast(delivery)
                else mutableState.value = mutableState.value.copy(error =
                    "Too many callback deliveries. Wait, then paste the current callback again.")
                null
            }
        }
        job?.start()
    }

    fun retryPublication() {
        val job = synchronized(monitor) {
            val account = mutableState.value.publication ?: return
            if (active != null || loggingOut != 0) return
            startLocked(Action.Publish(account))
        }
        job.start()
    }

    /** Must be called under monitor; lazy start makes even pre-start cancellation release ownership. */
    private fun startLocked(action: Action): Job {
        val work = Work(action)
        active = work
        mutableState.value = mutableState.value.copy(busy = true, error = null)
        work.job = scope.launch(start = CoroutineStart.LAZY) { run(work) }
        work.job.invokeOnCompletion { cause ->
            val next = synchronized(monitor) {
                if (active !== work) return@invokeOnCompletion
                active = null
                if (!scope.isActive || loggingOut != 0) callbacks.clear()
                val delivery = callbacks.removeFirstOrNull()
                mutableState.value = mutableState.value.copy(busy = delivery != null,
                    error = if (cause is CancellationException) CANCELLED_ERROR else mutableState.value.error)
                delivery?.let(::startLocked)
            }
            next?.start()
        }
        return work.job
    }

    private suspend fun run(work: Work) {
        try {
            when (val action = work.action) {
                is Action.Begin -> {
                    val launch = accounts.authority.beginAuthorization(action.identifier)
                    val current = accounts.authority.isAuthorizationPending(launch)
                    update(work) { if (current) State(busy = true, launch = launch, browserPending = true)
                        else State(busy = true, error = "Authorization was removed or replaced. Start a new login.") }
                }
                is Action.Callback -> {
                    val account = accounts.complete(action.url)
                    update(work, action.fingerprint) { State(busy = true, completed = account) }
                }
                is Action.Publish -> {
                    accounts.publish(action.account.session)
                    update(work) { State(busy = true, completed = action.account) }
                }
            }
        } catch (cause: CancellationException) {
            throw cause // Includes the authority's own timeout; completion cleanup retains its attribution.
        } catch (cause: AtProtoPublicationException) {
            update(work, (work.action as? Action.Callback)?.fingerprint) { State(busy = true,
                publication = cause.account, error = "Authorization was saved, but the account reference could not be published. Retry publication.") }
        } catch (_: Exception) {
            when (work.action) {
                is Action.Begin -> update(work) { State(busy = true,
                    error = "Could not begin authorization. Check the account, network and published client metadata.") }
                is Action.Callback -> {
                    discardStaleLaunch()
                    // An unrelated late delivery cannot erase a launch, completion or publication retry.
                    update(work) { it.copy(error = if (it.completed == null) CALLBACK_ERROR else it.error) }
                }
                is Action.Publish -> update(work) { it.copy(error =
                    "The saved authorization could not be published. Retry, or start a new login if it was removed or replaced.") }
            }
        }
    }

    private fun update(work: Work, fingerprint: List<Byte>? = null, transform: (State) -> State) = synchronized(monitor) {
        if (active === work) {
            if (fingerprint != null) completedCallback = fingerprint
            mutableState.value = transform(mutableState.value)
        }
    }

    /** A previously issued OS browser open cannot be recalled; every new dispatch checks fresh authority. */
    suspend fun consumeBrowserLaunch(launch: AuthorizationLaunch): Boolean = browserLaunch(launch, automatic = true)
    suspend fun reopenBrowserLaunch(launch: AuthorizationLaunch): Boolean = browserLaunch(launch, automatic = false)

    private suspend fun browserLaunch(launch: AuthorizationLaunch, automatic: Boolean): Boolean {
        synchronized(monitor) {
            val current = mutableState.value
            if (current.launch != launch || current.busy || automatic && !current.browserPending) return false
        }
        val valid = pending(launch)
        return synchronized(monitor) {
            val current = mutableState.value
            if (current.launch != launch) return@synchronized false
            if (!valid || now() >= launch.expiresAt) {
                mutableState.value = current.copy(launch = null, browserPending = false)
                false
            } else if (current.busy || automatic && !current.browserPending) false
            else {
                mutableState.value = current.copy(browserPending = false)
                true
            }
        }
    }

    private suspend fun pending(launch: AuthorizationLaunch): Boolean = try {
        now() < launch.expiresAt && accounts.authority.isAuthorizationPending(launch)
    } catch (cause: CancellationException) { throw cause }
    catch (_: Exception) { false } // A failed UI command check never dispatches a retained URL.

    private suspend fun discardStaleLaunch() {
        val launch = synchronized(monitor) { mutableState.value.launch } ?: return
        if (!pending(launch)) synchronized(monitor) {
            val current = mutableState.value
            if (current.launch == launch) mutableState.value = current.copy(launch = null, browserPending = false)
        }
    }

    /** Client-wide logout explicitly supersedes local jobs/queued deliveries as well as native pending state. */
    suspend fun logout() {
        val job = synchronized(monitor) {
            loggingOut++
            callbacks.clear()
            active?.job.also { active = null; mutableState.value = State(busy = true) }
        }
        job?.cancel()
        try { accounts.logout() }
        finally { synchronized(monitor) {
            loggingOut--
            if (loggingOut == 0) mutableState.value = State()
        } }
    }

    /** Stale/other-client reference removal must not discard a newer native pending authorization. */
    suspend fun remove(reference: UserAccountSerializable) {
        try { accounts.remove(reference) }
        finally {
            synchronized(monitor) {
                val current = mutableState.value
                mutableState.value = current.copy(
                    completed = current.completed?.takeUnless { it.session == reference.atProtoSession },
                    publication = current.publication?.takeUnless { it.session == reference.atProtoSession })
            }
            discardStaleLaunch()
        }
    }

    /** Navigation can retain two screens; completion is consumed once, never inferred from account presence. */
    fun consumeCompleted(handle: SessionHandle): Boolean = synchronized(monitor) {
        val current = mutableState.value
        if (current.completed?.session != handle) false
        else { mutableState.value = current.copy(completed = null); true }
    }

    private companion object {
        const val CALLBACK_ERROR = "Authorization could not be completed. Check the callback or start a new login."
        const val CANCELLED_ERROR = "Authorization was interrupted or timed out. Retry publication if offered, or start a new login."
    }
}
