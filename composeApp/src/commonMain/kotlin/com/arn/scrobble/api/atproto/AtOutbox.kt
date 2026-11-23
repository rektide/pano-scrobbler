package com.arn.scrobble.api.atproto

import com.arn.scrobble.api.lastfm.ScrobbleData
import com.arn.scrobble.db.AtDelivery
import com.arn.scrobble.db.AtDeliveryDao
import com.arn.scrobble.db.PanoDb
import com.arn.scrobble.work.PendingScrobblesWork
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

internal data class AtRemoteRecord(val ref: AtRef, val value: JsonObject)

internal suspend fun AtWire.record(pds: String, address: AtAddress): AtRemoteRecord? {
    val reply = get(secureUrl(pds).xrpc("com.atproto.repo.getRecord")
        .addQueryParameter("repo", address.did).addQueryParameter("collection", address.collection)
        .addQueryParameter("rkey", address.key).build())
    if (reply.code in setOf(400, 404) && reply.error == "RecordNotFound") return null
    checkAt(reply.ok, if (reply.transient) AtProblem.NETWORK else AtProblem.RESPONSE)
    return AtRemoteRecord(AtRef.read(reply.json, address), reply.json.obj("value") ?: throw AtFailure(AtProblem.RESPONSE))
}

internal class AtOutbox(
    private val dao: () -> AtDeliveryDao = { PanoDb.db.atDeliveryDao() },
    private val authority: AtAuthority = atAuthority,
    private val public: AtWire = anonymousAt,
    private val schedule: (Boolean) -> Unit = PendingScrobblesWork::scheduleAtDelivery,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val tid = AtTid(clock)

    suspend fun submit(data: ScrobbleData): Boolean {
        val account = accountOrNull() ?: return false
        val identity = account.identity ?: return false
        if (account.settings.completed.isEmpty()) return false
        val event = tid.next()
        val original = Json.encodeToString(data)
        val rows = account.settings.completed.map { target ->
            val payload = try { data.playRecord(target).toString() } catch (_: AtFailure) { null }
            AtDelivery(identity.did, event, target.name, data.timestamp, original, payload,
                state = if (payload == null) "METADATA" else "READY")
        }
        dao().stage(rows)
        schedule(false) // durable rows exist before foreground or worker can perform a POST
        drain(event)
        return true
    }

    private suspend fun accountOrNull() = try { authority.account() }
    catch (e: AtFailure) {
        // No desktop vault exists until opt-in; Android storage failure is surfaced in the account UI.
        if (e.problem != AtProblem.STORAGE) throw e
        null
    }

    /** Returns whether runnable deliveries remain (including those in retry backoff). */
    suspend fun drain(event: String? = null): Boolean {
        val account = accountOrNull() ?: return false
        val identity = account.identity ?: return false
        if (account.method == null || account.settings.completed.isEmpty()) return false
        val targets = account.settings.completed.map { it.name }
        val dao = dao()
        for (row in dao.actionable(identity.did, targets, event, clock())) {
            val claim = atSecret()
            val now = clock()
            if (dao.claim(row.did, row.tid, row.target, row.attempts, claim, now, now + 180_000) != 1) continue
            val outcome = try { send(identity, row) }
            catch (e: CancellationException) { throw e }
            catch (e: AtFailure) { when (e.problem) {
                AtProblem.RECONNECT -> Outcome("RECONNECT")
                AtProblem.PERMISSION -> Outcome("PERMISSION")
                AtProblem.STORAGE -> Outcome("RETRY")
                AtProblem.CONFLICT -> Outcome("CONFLICT")
                AtProblem.INPUT -> Outcome("METADATA")
                else -> Outcome("RETRY")
            } }
            catch (_: IOException) { Outcome("RETRY") }
            val retry = clock() + backoff(row.attempts)
            val next = when (outcome.state) {
                "RETRY" -> maxOf(outcome.retryAfter ?: 0, retry)
                "RECONNECT", "PERMISSION" -> retry
                else -> 0
            }
            dao.finish(row.did, row.tid, row.target, claim, outcome.state, next, outcome.ref?.address?.uri, outcome.ref?.cid)
        }
        return dao.countRunnable(identity.did, targets) > 0
    }

    suspend fun pending(did: String) = dao().pending(did)
    suspend fun retry(did: String) { dao().retry(did); schedule(true) }
    suspend fun hasRunnable(): Boolean {
        val account = accountOrNull() ?: return false
        val did = account.identity?.did ?: return false
        val targets = account.settings.completed.map { it.name }
        return account.method != null && targets.isNotEmpty() && dao().countRunnable(did, targets) > 0
    }

    private data class Outcome(val state: String, val ref: AtRef? = null, val retryAfter: Long? = null)

    private suspend fun send(identity: AtIdentity, row: AtDelivery): Outcome {
        val address = AtAddress(row.did, AtTarget.valueOf(row.target).play, row.tid)
        val payload = Json.parseToJsonElement(row.payload ?: throw AtFailure(AtProblem.INPUT)).jsonObject
        suspend fun reconcile(): Outcome? = public.record(identity.pds, address)?.let {
            if (equalRecord(payload, it.value)) Outcome("ACK", it.ref) else Outcome("CONFLICT")
        }
        if (row.attempts > 0) reconcile()?.let { return it }
        val reply = try {
            authority.write(row.did, address.collection, AtMutation.CREATE, address.body { put("record", payload); put("validate", false) })
        } catch (e: AtFailure) {
            if (e.problem in setOf(AtProblem.RESPONSE, AtProblem.NETWORK)) return reconcile() ?: Outcome("RETRY")
            throw e
        } catch (_: IOException) { return reconcile() ?: Outcome("RETRY") }
        if (reply.ok) {
            return try { Outcome("ACK", AtRef.read(reply.json, address)) }
            catch (_: AtFailure) { reconcile() ?: Outcome("RETRY") }
        }
        if (reply.code == 403 || reply.error in setOf("InsufficientScope", "insufficient_scope")) return Outcome("PERMISSION")
        if (reply.code == 401) return Outcome("RECONNECT")
        if (reply.transient) return Outcome("RETRY", retryAfter = retryTime(reply.retryAfter, clock()))
        reconcile()?.let { return it }
        return if (reply.code == 400) Outcome("REJECTED") else Outcome("RETRY")
    }

    private fun backoff(attempts: Int) = 30_000L * (1L shl attempts.coerceIn(0, 9))
}

internal fun retryTime(header: String?, now: Long): Long? = header?.let {
    it.toLongOrNull()?.let { seconds -> now + seconds.coerceIn(0, 86_400) * 1000 }
        ?: runCatching { ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
}

internal val atOutbox = AtOutbox()
