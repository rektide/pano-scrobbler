package com.arn.scrobble.api.atproto

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl

internal const val ROCKSKY_VIEW = "https://api.rocksky.app"
internal const val TEAL_VIEW = "https://view.teal.fm"

internal data class AtListen(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val time: Long?,
    val image: String? = null,
    val ref: AtRef? = null,
    val catalogUri: String? = null,
)

internal data class AtPublicProfile(val displayName: String?, val handle: String?, val avatar: String?, val source: String)
internal data class AtReadPage<C : Any, T>(val values: List<T>, val next: C?)

/** A walk over real continuations. An empty *filtered* page can still have a next cursor. */
internal class AtPageWalk<C : Any, T>(private val first: C, private val id: (T) -> String,
                                    private val fetch: suspend (C) -> AtReadPage<C, T>) {
    private val lock = Mutex()
    private val pages = mutableListOf<AtReadPage<C, T>>()
    private val seen = HashSet<String>()
    private val consumed = HashSet<C>()

    suspend fun page(number: Int, refresh: Boolean = false): AtReadPage<C, T> = lock.withLock {
        checkAt(number in 1..400 && number <= pages.size + 12)
        if (refresh) { pages.clear(); seen.clear(); consumed.clear(); checkAt(number == 1) }
        while (pages.size < number) {
            currentCoroutineContext().ensureActive()
            val cursor = if (pages.isEmpty()) first else pages.last().next ?: return@withLock AtReadPage(emptyList(), null)
            val result = fetch(cursor)
            checkAt(result.next != cursor && result.next !in consumed)
            consumed.add(cursor)
            pages.add(AtReadPage(result.values.filter { seen.add(id(it)) }, result.next))
        }
        pages[number - 1]
    }
}

internal data class AtHistoryQuery(val target: AtTarget, val identity: AtIdentity, val from: Long?, val until: Long?, val limit: Int = 50)

internal class AtPublic(
    private val wire: AtWire = anonymousAt,
    private val resolveIdentity: suspend (String) -> AtIdentity = AtIdentityResolver(wire)::resolve,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun resolve(input: String) = resolveIdentity(input)

    fun history(query: AtHistoryQuery): AtPageWalk<String, AtListen> = AtPageWalk("", AtListen::id) { continuation ->
        checkAt(query.limit in 1..50)
        val page = if (query.target == AtTarget.TEAL) repoHistory(query, continuation)
            else rockskyHistory(query, continuation.toIntOrNull() ?: 0)
        page.copy(values = page.values.filter { item -> item.time?.let { time ->
            (query.from == null || time >= query.from) && (query.until == null || time < query.until)
        } == true })
    }

    private suspend fun repoHistory(query: AtHistoryQuery, continuation: String): AtReadPage<String, AtListen> {
        val doc = listRecords(query.identity, query.target.play, query.limit, continuation.ifEmpty { null })
        val records = doc.array("records") ?: throw AtFailure(AtProblem.RESPONSE)
        val values = records.mapNotNull { j ->
            val objectRow = j as? JsonObject ?: return@mapNotNull null
            val address = actorAddress(objectRow.text("uri"), query.identity.did, query.target.play) ?: return@mapNotNull null
            val ref = try { AtRef.read(objectRow, address) } catch (_: AtFailure) { return@mapNotNull null }
            val data = objectRow.obj("value") ?: return@mapNotNull null
            if (data.text("\$type") != query.target.play) return@mapNotNull null
            tealListen(data, address.uri, ref)
        }
        return AtReadPage(values, doc.text("cursor")?.takeIf(String::isNotEmpty))
    }

    private suspend fun rockskyHistory(query: AtHistoryQuery, offset: Int): AtReadPage<String, AtListen> {
        val doc = rocksky("actor.getActorScrobbles", query.identity.did, query.limit, offset)
        val rows = doc.array("scrobbles") ?: throw AtFailure(AtProblem.RESPONSE)
        val values = rows.filterIsInstance<JsonObject>().mapNotNull { it.rockskyListen(query.identity.did) }
        return AtReadPage(values, if (rows.size == query.limit) (offset + rows.size).toString() else null)
    }

    suspend fun live(query: AtHistoryQuery): AtListen? {
        val address = AtAddress(query.identity.did, query.target.status, "self")
        val record = wire.record(query.identity.pds, address) ?: return null
        val data = validLive(record.value, query.target, clock()) ?: return null
        return when (query.target) {
            AtTarget.TEAL -> tealListen(data, address.uri)?.copy(time = null)
            AtTarget.ROCKSKY -> AtListen(address.uri, data.text("name").atField(512) ?: return null,
                data.text("artist").atField(256) ?: return null, data.text("album"), null, image(data.text("albumCoverUrl")))
        }
    }

    suspend fun profile(query: AtHistoryQuery): AtPublicProfile {
        val identity = query.identity
        if (query.target == AtTarget.ROCKSKY) {
            val doc = rocksky("actor.getProfile", identity.did)
            checkAt(doc.text("did") == identity.did)
            return AtPublicProfile(doc.text("displayName"), doc.text("handle"), image(doc.text("avatar")), "Rocksky profile")
        }
        // Teal's deployed profile query requires auth. Public AT identity profile is labeled separately.
        val doc = json(secureUrl("https://public.api.bsky.app").xrpc("app.bsky.actor.getProfile")
            .addQueryParameter("actor", identity.did).build())
        checkAt(doc.text("did") == identity.did)
        return AtPublicProfile(doc.text("displayName"), doc.text("handle"), image(doc.text("avatar")), "Public AT account profile (Bluesky)")
    }

    suspend fun listRecords(identity: AtIdentity, collection: String, limit: Int, cursor: String?) =
        json(secureUrl(identity.pds).xrpc("com.atproto.repo.listRecords").addQueryParameter("repo", identity.did)
            .addQueryParameter("collection", collection).addQueryParameter("limit", limit.toString()).apply {
                if (cursor != null) addQueryParameter("cursor", cursor)
            }.build())

    suspend fun rocksky(suffix: String, did: String, limit: Int? = null, offset: Int? = null,
                        extra: Map<String, String> = emptyMap()) = json(secureUrl(ROCKSKY_VIEW).xrpc("app.rocksky.$suffix")
        .addQueryParameter("did", did).apply {
            if (limit != null) addQueryParameter("limit", limit.toString())
            if (offset != null) addQueryParameter("offset", offset.toString())
            extra.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build())

    suspend fun json(url: HttpUrl): JsonObject = wire.get(url).let {
        checkAt(it.ok, if (it.transient) AtProblem.NETWORK else AtProblem.RESPONSE)
        it.json
    }
}

internal fun actorAddress(raw: String?, did: String, collection: String): AtAddress? {
    val key = raw?.substringAfterLast('/') ?: return null
    return AtAddress(did, collection, key).takeIf { it.accepts(raw) }
}

internal fun image(raw: String?) = try { secureUrl(raw).toString() } catch (_: AtFailure) { null }

private fun tealListen(data: JsonObject, id: String, ref: AtRef? = null): AtListen? {
    val title = data.text("trackName").atField(256) ?: return null
    val artist = data.array("artists").orEmpty().filterIsInstance<JsonObject>().mapNotNull { it.text("artistName") }
        .ifEmpty { data.strings("artistNames") }.joinToString(", ")
    return AtListen(id, title, artist, data.text("releaseName"), instantMs(data.text("playedTime")), ref = ref)
}

private fun JsonObject.rockskyListen(did: String): AtListen? {
    if (text("did") != did) return null
    val uri = actorAddress(text("uri"), did, AtTarget.ROCKSKY.play)?.uri
    val id = uri ?: text("id")?.let { "rocksky:$it" } ?: return null
    return AtListen(id, text("title").atField(512) ?: return null, text("artist").orEmpty(), text("album"),
        instantMs(text("createdAt")), image(text("albumArt")), catalogUri = text("trackUri"))
}

internal val atPublic = AtPublic()
