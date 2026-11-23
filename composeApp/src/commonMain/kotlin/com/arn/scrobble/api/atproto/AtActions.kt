package com.arn.scrobble.api.atproto

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

internal class AtActions(
    private val authority: AtAuthority = atAuthority,
    private val public: AtPublic = atPublic,
    private val wire: AtWire = anonymousAt,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val tid = AtTid(clock)

    suspend fun ownRecord(query: AtHistoryQuery, item: AtListen): AtRef {
        checkAt(authority.account().identity?.did == query.identity.did, AtProblem.PERMISSION)
        item.ref?.let { return it }
        val address = actorAddress(item.id, query.identity.did, query.target.play) ?: throw AtFailure(AtProblem.RESPONSE)
        val record = wire.record(query.identity.pds, address) ?: throw AtFailure(AtProblem.CONFLICT)
        checkAt(record.value.text("\$type") == query.target.play && record.value.text("title") == item.title &&
            record.value.text("artist") == item.artist && instantMs(record.value.text("createdAt")) == item.time, AtProblem.CONFLICT)
        return record.ref
    }

    suspend fun delete(ref: AtRef, target: AtTarget) {
        checkAt(ref.address.collection == target.play, AtProblem.PERMISSION)
        delete(ref)
    }

    private suspend fun delete(ref: AtRef) {
        val addr = ref.address
        val response = authority.write(addr.did, addr.collection, AtMutation.DELETE, addr.body { put("swapRecord", ref.cid) })
        checkAt(response.ok, when {
            response.error in setOf("InvalidSwap", "RecordNotFound") -> AtProblem.CONFLICT
            response.transient -> AtProblem.NETWORK
            response.code in setOf(401, 403) -> AtProblem.PERMISSION
            else -> AtProblem.RESPONSE
        })
    }

    suspend fun matchingLikes(actor: AtIdentity, subject: AtRef): List<AtRef> {
        val references = mutableListOf<AtRef>()
        val seen = HashSet<String>()
        var cursor: String? = null
        repeat(12) {
            val result = public.listRecords(actor, ROCKSKY_LIKE, 100, cursor)
            val rows = result.array("records") ?: throw AtFailure(AtProblem.RESPONSE)
            for (raw in rows.filterIsInstance<JsonObject>()) {
                val value = raw.obj("value") ?: continue
                val pointer = value.obj("subject") ?: continue
                if (value.text("\$type") != ROCKSKY_LIKE || pointer.text("uri") != subject.address.uri) continue
                // A prior catalog CID for the same immutable address still denotes the same song.
                val addr = actorAddress(raw.text("uri"), actor.did, ROCKSKY_LIKE) ?: throw AtFailure(AtProblem.RESPONSE)
                references += AtRef.read(raw, addr)
            }
            cursor = result.text("cursor")?.takeIf(String::isNotEmpty) ?: return references
            checkAt(seen.add(cursor!!))
        }
        throw AtFailure(AtProblem.RESPONSE) // can't establish complete ownership; not an empty result
    }

    suspend fun rockskySubject(item: AtListen): AtRef {
        val raw = item.catalogUri ?: throw AtFailure(AtProblem.RESPONSE)
        val did = raw.removePrefix("at://").substringBefore('/')
        checkAt(did.startsWith("did:"))
        val address = actorAddress(raw, did, "app.rocksky.song") ?: throw AtFailure(AtProblem.RESPONSE)
        val identity = public.resolve(did)
        val record = wire.record(identity.pds, address) ?: throw AtFailure(AtProblem.RESPONSE)
        checkAt(record.value.text("\$type") == "app.rocksky.song")
        return record.ref
    }

    suspend fun setLoved(actor: AtIdentity, subject: AtRef, love: Boolean): Boolean {
        val existing = matchingLikes(actor, subject)
        if (!love) existing.forEach { delete(it) }
        else if (existing.isEmpty()) {
            val addr = AtAddress(actor.did, ROCKSKY_LIKE, tid.next())
            val reply = authority.write(actor.did, ROCKSKY_LIKE, AtMutation.CREATE, addr.body {
                put("validate", false)
                put("record", buildJsonObject {
                    put("\$type", ROCKSKY_LIKE)
                    put("createdAt", Instant.ofEpochMilli(clock()).toString())
                    put("subject", buildJsonObject { put("uri", subject.address.uri); put("cid", subject.cid) })
                })
            })
            checkAt(reply.ok, if (reply.transient) AtProblem.NETWORK else AtProblem.RESPONSE)
            AtRef.read(reply.json, addr)
        }
        val actual = matchingLikes(actor, subject).isNotEmpty()
        checkAt(love == actual, AtProblem.CONFLICT)
        return actual
    }
}

internal val atActions = AtActions()
