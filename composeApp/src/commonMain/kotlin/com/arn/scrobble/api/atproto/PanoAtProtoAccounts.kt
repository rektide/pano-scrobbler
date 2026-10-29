package com.arn.scrobble.api.atproto

import androidx.datastore.core.DataStore
import com.arn.scrobble.api.AccountType
import com.arn.scrobble.api.UserAccountSerializable
import com.arn.scrobble.api.UserCached
import com.arn.scrobble.atproto.oauth.AtProtoAccount
import com.arn.scrobble.atproto.oauth.AtProtoAccountAuthority
import com.arn.scrobble.atproto.storage.SessionHandle
import com.arn.scrobble.pref.MainPrefs
import kotlinx.coroutines.CancellationException

internal class AtProtoPublicationException(val account: AtProtoAccount, cause: Exception) :
    Exception("Authorization saved but account publication failed", cause)

/** MainPrefs is a recoverable account projection, never the credential authority. */
internal class PanoAtProtoAccounts(
    val authority: AtProtoAccountAuthority,
    private val prefs: DataStore<MainPrefs>,
) {
    suspend fun complete(callback: String): AtProtoAccount {
        val completed = authority.completeAuthorization(callback)
        try { publish(completed.session) }
        catch (cause: CancellationException) { throw cause }
        catch (cause: Exception) { throw AtProtoPublicationException(completed, cause) }
        return completed
    }

    /** Explicit retry after projection failure; a callback code is never replayed for this. */
    suspend fun publish(handle: SessionHandle) = authority.publishAccount(handle) { account ->
        prefs.updateData { current ->
            val previous = current.scrobbleAccounts.singleOrNull {
                it.type == AccountType.ATPROTO && it.atProtoSession?.clientId == account.session.clientId && it.authKey == account.session.did
            }
            val reference = account.reference().let {
                if (previous == null) it else it.copy(atProtoTargets = previous.atProtoTargets, canScrobble = previous.canScrobble)
            }
            current.copy(
                scrobbleAccounts = current.scrobbleAccounts.filterNot { it.type == AccountType.ATPROTO } + reference,
                currentAccountType = AccountType.ATPROTO,
            )
        }
        Unit
    }

    suspend fun setEnabled(reference: UserAccountSerializable, enabled: Boolean) {
        require(reference.type == AccountType.ATPROTO)
        val handle = requireNotNull(reference.atProtoSession) { "Sign in again before enabling this account" }
        authority.publishAccount(handle) {
            prefs.updateData { current -> current.copy(scrobbleAccounts = current.scrobbleAccounts.map {
                if (it.type == AccountType.ATPROTO && it.atProtoSession == handle) it.copy(canScrobble = enabled) else it
            }) }
        }
    }

    suspend fun logout() = authority.logout {
        prefs.updateData { current ->
            val remaining = current.scrobbleAccounts.filterNot { it.type == AccountType.ATPROTO }
            current.copy(
                scrobbleAccounts = remaining,
                currentAccountType = if (current.currentAccountType == AccountType.ATPROTO)
                    remaining.firstOrNull()?.type ?: AccountType.LASTFM else current.currentAccountType,
                drawerData = current.drawerData - AccountType.ATPROTO,
            )
        }
    }

    /** The reference the operator clicked, not whichever login is current when this coroutine runs. */
    suspend fun remove(reference: UserAccountSerializable) {
        require(reference.type == AccountType.ATPROTO)
        val handle = reference.atProtoSession
        val removeProjection: suspend () -> Unit = {
            prefs.updateData { current ->
                val remaining = current.scrobbleAccounts.filterNot {
                    it.type == AccountType.ATPROTO && if (handle == null) it == reference else it.atProtoSession == handle
                }
                val hasReplacement = remaining.any { it.type == AccountType.ATPROTO }
                current.copy(scrobbleAccounts = remaining,
                    currentAccountType = if (current.currentAccountType == AccountType.ATPROTO && !hasReplacement)
                        remaining.firstOrNull()?.type ?: AccountType.LASTFM else current.currentAccountType,
                    drawerData = if (hasReplacement) current.drawerData else current.drawerData - AccountType.ATPROTO)
            }
            Unit
        }
        if (handle == null) removeProjection() else authority.removeAccount(handle, removeProjection)
    }

    private fun AtProtoAccount.reference() = UserAccountSerializable(
        type = AccountType.ATPROTO,
        user = UserCached(handle ?: session.did, "https://bsky.app/profile/${session.did}", handle ?: session.did, "", -1),
        authKey = session.did,
        apiRoot = pds,
        atProtoSession = session,
    )
}
