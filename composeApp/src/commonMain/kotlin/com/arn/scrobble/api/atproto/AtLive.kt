package com.arn.scrobble.api.atproto

import com.arn.scrobble.api.lastfm.ScrobbleData
import com.arn.scrobble.api.listenbrainz.ListenBrainz.Companion.MUSICBRAINZ_TRACKID
import com.arn.scrobble.db.AtLiveDao
import com.arn.scrobble.db.AtLiveOwner
import com.arn.scrobble.db.PanoDb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.time.Instant

internal data class AtPlayback(val token: String, val hash: Int, val startedAt: Long)

internal class AtLive(
    private val authority: AtAuthority = atAuthority,
    private val dao: () -> AtLiveDao = { PanoDb.db.atLiveDao() },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val lock = Mutex()

    suspend fun start(playback: AtPlayback, data: ScrobbleData, stillPlaying: () -> Boolean) = lock.withLock {
        val account = available { authority.account() } ?: return@withLock
        val identity = account.identity ?: return@withLock
        if (account.method == null) return@withLock
        val expires = playback.startedAt + liveLength(data.duration)
        for (target in account.settings.live) {
            if (!stillPlaying() || clock() >= expires) break
            available {
                if (dao().owner(identity.did, target.name)?.playback == playback.token) return@available
                val address = AtAddress(identity.did, target.status, "self")
                val record = data.statusRecord(target, playback.startedAt, expires)
                val response = authority.write(identity.did, target.status, AtMutation.PUT,
                    address.body { put("record", record); put("validate", false) })
                checkAt(response.ok, if (response.transient) AtProblem.NETWORK else AtProblem.RESPONSE)
                val ref = AtRef.read(response.json, address)
                dao().set(AtLiveOwner(identity.did, target.name, playback.token, ref.cid, expires))
            }
        }
    }

    suspend fun stop(playback: AtPlayback) = lock.withLock {
        val did = available { authority.account() }?.identity?.did ?: return@withLock
        AtTarget.entries.forEach { target ->
            available { clear(did, target, playback.token) }
        }
    }

    suspend fun disable(did: String, targets: Set<AtTarget>) = lock.withLock {
        targets.forEach { target -> available { clear(did, target, null) } }
    }

    private suspend fun clear(did: String, target: AtTarget, playback: String?) {
        val owner = dao().owner(did, target.name) ?: return
        if (playback != null && owner.playback != playback) return
        if (owner.expires <= clock()) {
            dao().forget(did, target.name, owner.cid)
            return
        }
        val address = AtAddress(did, target.status, "self")
        val response = authority.write(did, target.status, AtMutation.DELETE,
            address.body { put("swapRecord", owner.cid) }, clearingStatus = true)
        if (response.ok || response.error in setOf("InvalidSwap", "RecordNotFound"))
            dao().forget(did, target.name, owner.cid)
        else throw AtFailure(if (response.transient) AtProblem.NETWORK else AtProblem.RESPONSE)
    }

    private suspend fun <T> available(block: suspend () -> T): T? = try { block() }
    catch (e: CancellationException) { throw e }
    catch (e: AtFailure) { authority.notices.value = e.problem; null }
    catch (_: java.io.IOException) { authority.notices.value = AtProblem.NETWORK; null }
}

private fun liveLength(duration: Long?) = (duration?.coerceIn(15_000, 540_000)?.plus(60_000) ?: 180_000)
internal fun ScrobbleData.statusRecord(target: AtTarget, start: Long, expires: Long) = buildJsonObject {
    put("\$type", target.status)
    val started = Instant.ofEpochMilli(start).toString()
    val expiration = Instant.ofEpochMilli(expires).toString()
    when (target) {
        AtTarget.TEAL -> {
            val play = copy(timestamp = start).playRecord(target)
            checkAt(play.array("artists")?.isNotEmpty() == true, AtProblem.INPUT)
            put("time", started)
            put("expiry", expiration)
            put("item", JsonObject(play - "\$type"))
        }
        AtTarget.ROCKSKY -> {
            put("startedAt", started)
            put("expiresAt", expiration)
            put("track", buildJsonObject {
                put("name", track.atField(512) ?: throw AtFailure(AtProblem.INPUT))
                put("artist", artist.atField(256) ?: throw AtFailure(AtProblem.INPUT))
                album.atField(256)?.let { put("album", it) }
                duration?.takeIf { it > 0 }?.let { put("durationMs", it) }
                mbid(extraTags[MUSICBRAINZ_TRACKID])?.let { put("recordingMbId", it) }
                trackNumber?.takeIf { it > 0 }?.let { put("trackNumber", it) }
            })
        }
    }
}

internal fun validLive(value: JsonObject, target: AtTarget, now: Long): JsonObject? {
    if (value.text("\$type") != target.status) return null
    val start = instantMs(value.text(if (target == AtTarget.TEAL) "time" else "startedAt")) ?: return null
    if (start !in (now - 600_000)..now) return null
    val expiresKey = if (target == AtTarget.TEAL) "expiry" else "expiresAt"
    val expiry = if (expiresKey in value) instantMs(value.text(expiresKey)) ?: return null
        else start + if (target == AtTarget.TEAL) 600_000 else liveLength(value.obj("track")?.number("durationMs"))
    if (expiry <= now || expiry <= start || expiry - start > 600_000) return null
    return value.obj(if (target == AtTarget.TEAL) "item" else "track")
}

internal val atLive = AtLive()
