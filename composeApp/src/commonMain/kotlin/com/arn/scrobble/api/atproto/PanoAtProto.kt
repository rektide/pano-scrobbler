package com.arn.scrobble.api.atproto

import com.arn.scrobble.BuildKonfig
import com.arn.scrobble.api.AccountType
import com.arn.scrobble.api.Requesters
import com.arn.scrobble.atproto.oauth.AtProtoAccountAuthority
import com.arn.scrobble.atproto.oauth.AuthorizationLaunch
import com.arn.scrobble.atproto.oauth.OAuthClientConfig
import com.arn.scrobble.atproto.posting.AtProtoTarget
import com.arn.scrobble.atproto.storage.SessionHandle
import com.arn.scrobble.utils.PlatformStuff
import com.arn.scrobble.utils.Stuff

internal object PanoAtProto {
    val authority by lazy { AtProtoAccountAuthority(PanoAtProtoVault.accounts, OAuthClientConfig(
        BuildKonfig.ATPROTO_CLIENT_ID, BuildKonfig.ATPROTO_REDIRECT_URI,
        "atproto repo:fm.teal.feed.play?action=create repo:app.rocksky.scrobble?action=create",
    ), Requesters.atProtoHttpClients) }
    val accounts by lazy { PanoAtProtoAccounts(authority, PlatformStuff.mainPrefs) }

    private val loginController by lazy { AtProtoLogin(accounts, Stuff.appScope) }
    val login get() = loginController.state
    fun begin(identifier: String) = loginController.begin(identifier)
    fun completeCallback(callback: String) = loginController.completeCallback(callback)
    fun retryPublication() = loginController.retryPublication()
    fun consumeBrowserLaunch(launch: AuthorizationLaunch) = loginController.consumeBrowserLaunch(launch)
    fun consumeCompleted(handle: SessionHandle) = loginController.consumeCompleted(handle)

    suspend fun select(handle: SessionHandle, target: AtProtoTarget, enabled: Boolean) = authority.publishAccount(handle) { current ->
        require(!enabled || target in AtProtoTarget.grantedBy(current.grantedScopes)) { "Target not granted" }
        PlatformStuff.mainPrefs.updateData { prefs -> prefs.copy(scrobbleAccounts = prefs.scrobbleAccounts.map {
            if (it.type == AccountType.ATPROTO && it.atProtoSession == handle)
                it.copy(atProtoTargets = if (enabled) it.atProtoTargets + target else it.atProtoTargets - target) else it
        }) }
    }
}
