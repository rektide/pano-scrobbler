package com.arn.scrobble.api.atproto

import androidx.datastore.core.DataStore
import com.arn.scrobble.api.AccountType
import com.arn.scrobble.api.Requesters
import com.arn.scrobble.api.UserAccountSerializable
import com.arn.scrobble.api.UserCached
import com.arn.scrobble.api.withScrobbleAccount
import com.arn.scrobble.pref.MainPrefs
import com.arn.scrobble.utils.PlatformStuff
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.first
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/** The AT login is private to MainPrefs. Teal and Rocksky entries hold only its DID and PDS. */
class AtprotoSessions(private val prefs: DataStore<MainPrefs> = PlatformStuff.mainPrefs) {
    /** Saves a verified login and its enabled destinations together. */
    suspend fun connect(session: AtprotoAppPasswordSession, user: UserCached, destinations: Set<AccountType>) {
        require(destinations.isNotEmpty() && destinations.all { it.isAtproto })
        prefs.updateData { p ->
            val sameDid = p.atprotoConnection?.did == session.did
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
                atprotoRotation = null,
                scrobbleAccounts = accounts,
                currentAccountType = p.selectionIn(accounts),
                drawerData = p.drawerData.filterKeys { !it.isAtproto || sameDid && it in destinations },
            )
        }
    }

    /** Resolves an enabled destination, never handing a stale account another DID's tokens. */
    suspend fun restore(account: UserAccountSerializable): AtprotoAppPasswordSession? {
        if (!account.type.isAtproto) return null
        val p = prefs.data.first()
        if (p.scrobbleAccounts.none { it.type == account.type && it.authKey == account.authKey && it.apiRoot == account.apiRoot })
            return null
        return p.atprotoSession?.takeIf { it.did == account.authKey && it.pds == account.apiRoot }
    }

    suspend fun setDestination(did: String, type: AccountType, enabled: Boolean) {
        require(type.isAtproto)
        prefs.updateData { p ->
            val owner = p.scrobbleAccounts.first { it.type.isAtproto && it.authKey == did }
            check(p.atprotoSession?.did == did)
            val existing = p.scrobbleAccounts.firstOrNull { it.type == type }
            val accounts = if (existing != null) p.scrobbleAccounts.map {
                if (it.type == type) it.copy(canScrobble = enabled) else it
            } else if (enabled) p.scrobbleAccounts.withScrobbleAccount(
                UserAccountSerializable(type, owner.user, did, owner.apiRoot)
            ) else return@updateData p
            p.copy(scrobbleAccounts = accounts)
        }
    }

    /** Replaces credentials for the connected account without changing either destination. */
    suspend fun replaceSession(session: AtprotoAppPasswordSession) {
        prefs.updateData { p ->
            check(p.atprotoSession?.let { it.did == session.did && it.pds == session.pds } == true) {
                "AT Protocol account is no longer connected"
            }
            p.copy(atprotoSession = session, atprotoRotation = null)
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
            p.copy(atprotoSession = null, atprotoRotation = null, scrobbleAccounts = accounts, currentAccountType = p.selectionIn(accounts),
                drawerData = p.drawerData.filterKeys { !it.isAtproto })
        }
    }

    /** Serialize account replacement against authenticated sends using MainPrefs' multiprocess update lock. */
    suspend fun send(
        account: UserAccountSerializable,
        client: HttpClient = Requesters.atprotoPdsClient,
        action: suspend (AtprotoAppPasswordSession) -> RepoRecord,
    ): RepoRecord {
        val initial = restore(account) ?: throw AtprotoReconnect()
        suspend fun post(expected: AtprotoAppPasswordSession): RepoRecord {
            var receipt: RepoRecord? = null
            prefs.updateData { prefs ->
                val session = prefs.writeSession(account)
                if (session.accessJwt != expected.accessJwt || session.refreshJwt != expected.refreshJwt) throw AtprotoReconnect()
                receipt = action(session)
                prefs
            }
            return requireNotNull(receipt)
        }
        return try {
            post(initial)
        } catch (e: AtprotoXrpcError) {
            if (!e.expired) throw e
            val rotated = refresh(account, client, initial)
            try {
                post(rotated)
            } catch (e: AtprotoXrpcError) {
                if (e.expired) throw AtprotoReconnect() else throw e
            }
        }
    }

    /** Remove the old refresh token durably before sending it; a crash or uncertain rotation needs reconnect. */
    private suspend fun refresh(
        account: UserAccountSerializable, http: HttpClient, expected: AtprotoAppPasswordSession,
    ): AtprotoAppPasswordSession {
        val attempt = UUID.randomUUID().toString()
        var useSession: AtprotoAppPasswordSession? = null
        prefs.updateData { p ->
            val session = p.writeSession(account)
            if (session.accessJwt != expected.accessJwt || session.refreshJwt != expected.refreshJwt) {
                useSession = session
                p
            } else {
                p.copy(atprotoSession = null, atprotoRotation = attempt)
            }
        }
        useSession?.let { return it }
        val session = try {
            http.rotateAppPassword(expected.pds, expected.did, expected.refreshJwt)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            throw AtprotoReconnect()
        }
        prefs.updateData { p ->
            if (p.atprotoRotation != attempt || p.atprotoConnection?.did != session.did) throw AtprotoReconnect()
            p.copy(atprotoSession = session, atprotoRotation = null)
        }
        return session
    }

    private fun MainPrefs.hasDestination(account: UserAccountSerializable) = scrobbleAccounts.any {
        it.type == account.type && it.authKey == account.authKey && it.apiRoot == account.apiRoot && it.canScrobble
    }

    private fun MainPrefs.writeSession(account: UserAccountSerializable): AtprotoAppPasswordSession =
        atprotoSession?.takeIf { hasDestination(account) && it.did == account.authKey && it.pds == account.apiRoot }
            ?: throw AtprotoReconnect()

    private fun MainPrefs.selectionIn(accounts: List<UserAccountSerializable>) =
        currentAccountType.takeIf { selected -> accounts.any { it.type == selected } }
            ?: accounts.firstOrNull { !it.type.isAtproto }?.type
            ?: accounts.firstOrNull()?.type ?: AccountType.LASTFM
}
