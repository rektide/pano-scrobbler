package com.arn.scrobble.api.atproto

import androidx.datastore.core.DataStore
import com.arn.scrobble.api.AccountType
import com.arn.scrobble.api.Requesters
import com.arn.scrobble.api.ScrobbleEvent
import com.arn.scrobble.api.lastfm.ScrobbleData
import com.arn.scrobble.db.AccountBitmaskConverter.accountTypesToBitMask
import com.arn.scrobble.db.PendingScrobble
import com.arn.scrobble.db.PendingScrobblesDao
import com.arn.scrobble.pref.MainPrefs
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.first
import kotlin.coroutines.cancellation.CancellationException

internal val atprotoMask = accountTypesToBitMask(setOf(AccountType.TEAL, AccountType.ROCKSKY))

/** The shared Pending worker is the only sender. It can recover even if the first response is lost. */
internal class AtprotoPendingPlays(
    private val dao: PendingScrobblesDao,
    private val prefs: DataStore<MainPrefs>,
    private val client: () -> HttpClient = { Requesters.atprotoRepoReader },
) {
    suspend fun enqueue(play: ScrobbleData): Boolean {
        val accounts = prefs.data.first().atprotoTargets()
        val owner = accounts.firstOrNull()?.authKey ?: return false
        dao.insertIgnore(PendingScrobble(
            scrobbleData = play,
            event = ScrobbleEvent.scrobble,
            services = accounts.map { it.type }.toSet(),
            atprotoDid = owner,
            atprotoTid = AtprotoEventTid.next(),
        ))
        return true
    }

    suspend fun drain(limit: Int): Boolean {
        val snapshot = prefs.data.first()
        val session = snapshot.atprotoSession ?: return false
        val accounts = snapshot.atprotoTargets().filter { it.authKey == session.did && it.apiRoot == session.pds }
        if (accounts.isEmpty()) return false
        val entries = dao.atprotoScrobblesForSubmission(session.did, accountTypesToBitMask(accounts.map { it.type }.toSet()), limit)
        var retry = false
        var madeProgress = false
        val deferred = mutableSetOf<AccountType>()
        for (entry in entries) {
            val did = requireNotNull(entry.atprotoDid)
            val rkey = requireNotNull(entry.atprotoTid)
            for (account in accounts) {
                val type = account.type
                val bit = accountTypesToBitMask(setOf(type))
                if (type !in entry.services || entry.atprotoBlocked and bit != 0 || type in deferred) continue
                val pds = requireNotNull(account.apiRoot)
                val collection = type.playCollection
                try {
                    val payload = entry.scrobbleData.atprotoPlay(type)
                    val http = client()
                    val existing = http.findPlay(pds, did, collection, rkey)
                    val receipt = if (existing != null) {
                        existing.receipt(did, collection, rkey)
                        if (!existing.matchesPlay(payload)) throw AtprotoPlayRejected("Different $collection record already uses this key")
                        existing
                    } else {
                        AtprotoSessions(prefs).send(account, http) { it -> http.createPlay(it, collection, rkey, payload) }
                    }
                    val cid = receipt.receipt(did, collection, rkey)
                    dao.acknowledgeServices(entry._id, bit, cid.takeIf { type == AccountType.TEAL }, cid.takeIf { type == AccountType.ROCKSKY })
                    madeProgress = true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    when (e) {
                        is AtprotoPlayRejected -> {
                            dao.blockAtproto(entry._id, bit)
                            madeProgress = true
                        }
                        is AtprotoReconnect, is AtprotoPermissionMissing -> deferred += type
                        else -> {
                            deferred += type
                            retry = true
                        }
                    }
                    dao.logFailure(listOf(entry._id), listOf(e))
                }
            }
        }
        return retry || madeProgress && entries.size == limit
    }

    private fun MainPrefs.atprotoTargets() = atprotoConnection?.let { owner ->
        scrobbleAccounts.filter { it.type in owner.destinations && it.authKey == owner.did && it.canScrobble }
    }.orEmpty()
}
