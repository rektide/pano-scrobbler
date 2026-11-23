package com.arn.scrobble.api.atproto

import androidx.datastore.core.DataStore
import com.arn.scrobble.api.AccountType
import com.arn.scrobble.api.UserAccountSerializable
import com.arn.scrobble.api.UserCached
import com.arn.scrobble.api.withScrobbleAccount
import com.arn.scrobble.pref.MainPrefs
import com.arn.scrobble.utils.PlatformStuff
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

@Serializable
sealed interface AtprotoSession {
    val did: String
    val pds: String
}

/** The AT login is private to MainPrefs. Teal and Rocksky entries hold only its DID and PDS. */
class AtprotoSessions(private val prefs: DataStore<MainPrefs> = PlatformStuff.mainPrefs) {
    /** Saves a verified login and its enabled destinations together. */
    suspend fun connect(session: AtprotoSession, user: UserCached, destinations: Set<AccountType>) {
        require(session.isForThisClient())
        require(destinations.isNotEmpty() && destinations.all { it.isAtproto })
        prefs.updateData { p ->
            val sameDid = p.atprotoSession?.did == session.did
            val previous = p.scrobbleAccounts.filter { it.type.isAtproto }
            var accounts = p.scrobbleAccounts.filterNot { it.type.isAtproto && (!sameDid || it.type !in destinations) }
            for (type in destinations) {
                val existing = previous.firstOrNull { sameDid && it.type == type && it.authKey == session.did }
                val entry = existing?.copy(user = user, apiRoot = session.pds)
                    ?: UserAccountSerializable(type, user, session.did, session.pds)
                accounts = accounts.withScrobbleAccount(entry)
            }
            p.copy(
                atprotoSession = session,
                scrobbleAccounts = accounts,
                currentAccountType = p.selectionIn(accounts),
                drawerData = p.drawerData.filterKeys { !it.isAtproto || sameDid && it in destinations },
            )
        }
    }

    /** Resolves an enabled destination, never handing a stale account another DID's tokens. */
    suspend fun restore(account: UserAccountSerializable): AtprotoSession? {
        if (!account.type.isAtproto) return null
        val p = prefs.data.first()
        if (p.scrobbleAccounts.none { it.type == account.type && it.authKey == account.authKey && it.apiRoot == account.apiRoot })
            return null
        return p.atprotoSession?.takeIf { it.did == account.authKey && it.pds == account.apiRoot && it.isForThisClient() }
    }

    /** Replaces credentials for the connected account without changing either destination. */
    suspend fun replaceSession(session: AtprotoSession) {
        require(session.isForThisClient())
        prefs.updateData { p ->
            check(p.atprotoSession?.let { it.did == session.did && it.pds == session.pds } == true) {
                "AT Protocol account is no longer connected"
            }
            p.copy(atprotoSession = session)
        }
    }

    suspend fun disable(destination: AccountType) {
        require(destination.isAtproto)
        prefs.updateData { p ->
            val accounts = p.scrobbleAccounts.filterNot { it.type == destination }
            p.copy(scrobbleAccounts = accounts, currentAccountType = p.selectionIn(accounts),
                drawerData = p.drawerData - destination)
        }
    }

    /** Removes the local login and both account entries in one transaction. */
    suspend fun disconnect() {
        prefs.updateData { p ->
            val accounts = p.scrobbleAccounts.filterNot { it.type.isAtproto }
            p.copy(atprotoSession = null, scrobbleAccounts = accounts, currentAccountType = p.selectionIn(accounts),
                drawerData = p.drawerData.filterKeys { !it.isAtproto })
        }
    }

    private fun MainPrefs.selectionIn(accounts: List<UserAccountSerializable>) =
        currentAccountType.takeIf { selected -> accounts.any { it.type == selected } }
            ?: accounts.firstOrNull()?.type ?: AccountType.LASTFM

    private fun AtprotoSession.isForThisClient() = this !is AtprotoOAuthSession || clientId == ATPROTO_CLIENT_ID
}
