package com.arn.scrobble.api.atproto

import com.arn.scrobble.BuildKonfig
import com.arn.scrobble.api.AccountType
import com.arn.scrobble.api.Requesters
import com.arn.scrobble.atproto.oauth.AtProtoAccount
import com.arn.scrobble.atproto.oauth.AtProtoAccountAuthority
import com.arn.scrobble.atproto.oauth.AuthorizationLaunch
import com.arn.scrobble.atproto.oauth.OAuthClientConfig
import com.arn.scrobble.atproto.posting.AtProtoTarget
import com.arn.scrobble.atproto.storage.SessionHandle
import com.arn.scrobble.utils.PlatformStuff
import com.arn.scrobble.utils.Stuff
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal object PanoAtProto {
    val authority by lazy { AtProtoAccountAuthority(PanoAtProtoVault.accounts, OAuthClientConfig(
        BuildKonfig.ATPROTO_CLIENT_ID, BuildKonfig.ATPROTO_REDIRECT_URI,
        "atproto repo:fm.teal.feed.play?action=create repo:app.rocksky.scrobble?action=create",
    ), Requesters.atProtoHttpClients) }
    val accounts by lazy { PanoAtProtoAccounts(authority, PlatformStuff.mainPrefs) }

    data class LoginState(
        val busy: Boolean = false,
        val launch: AuthorizationLaunch? = null,
        val completed: AtProtoAccount? = null,
        val error: String? = null,
        val publication: AtProtoAccount? = null,
    )
    private val mutableLogin = MutableStateFlow(LoginState())
    val login = mutableLogin.asStateFlow()

    fun begin(identifier: String) {
        mutableLogin.value = LoginState(busy = true)
        Stuff.appScope.launch {
            try { mutableLogin.value = LoginState(launch = authority.beginAuthorization(identifier)) }
            catch (cause: CancellationException) { throw cause }
            catch (_: Exception) { mutableLogin.value = LoginState(error = "Could not begin authorization. Check the account, network and published client metadata.") }
        }
    }

    fun completeCallback(callback: String) {
        mutableLogin.value = LoginState(busy = true)
        Stuff.appScope.launch {
            try { mutableLogin.value = LoginState(completed = accounts.complete(callback)) }
            catch (cause: CancellationException) { throw cause }
            catch (cause: AtProtoPublicationException) { mutableLogin.value = LoginState(
                publication = cause.account, error = "Authorization was saved, but the account reference could not be published. Retry publication.") }
            catch (_: Exception) { mutableLogin.value = LoginState(error = "Authorization could not be completed or published. You can retry publishing an already authorized account, or start a new login.") }
        }
    }

    fun retryPublication() {
        val account = mutableLogin.value.publication ?: return
        mutableLogin.value = LoginState(busy = true)
        Stuff.appScope.launch {
            try {
                accounts.publish(account.session)
                mutableLogin.value = LoginState(completed = account)
            } catch (cause: CancellationException) { throw cause }
            catch (_: Exception) { mutableLogin.value = LoginState(error = "No publishable authorization. Start a new login.") }
        }
    }

    /** Navigation transitions may briefly retain two screens; only one consumes this completion. */
    fun consumeCompleted(handle: SessionHandle): Boolean {
        val state = mutableLogin.value
        return state.completed?.session == handle && mutableLogin.compareAndSet(state, LoginState())
    }

    suspend fun select(handle: SessionHandle, target: AtProtoTarget, enabled: Boolean) = authority.publishAccount(handle) { current ->
        require(!enabled || target in AtProtoTarget.grantedBy(current.grantedScopes)) { "Target not granted" }
        PlatformStuff.mainPrefs.updateData { prefs -> prefs.copy(scrobbleAccounts = prefs.scrobbleAccounts.map {
            if (it.type == AccountType.ATPROTO && it.atProtoSession == handle)
                it.copy(atProtoTargets = if (enabled) it.atProtoTargets + target else it.atProtoTargets - target) else it
        }) }
    }
}
