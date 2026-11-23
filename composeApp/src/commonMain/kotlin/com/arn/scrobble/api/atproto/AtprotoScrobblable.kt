package com.arn.scrobble.api.atproto

import androidx.collection.LruCache
import com.arn.scrobble.api.AccountType
import com.arn.scrobble.api.Requesters
import com.arn.scrobble.api.Scrobblable
import com.arn.scrobble.api.UserAccountSerializable
import com.arn.scrobble.api.UserCached
import com.arn.scrobble.api.cache.CacheStrategy
import com.arn.scrobble.api.lastfm.Album
import com.arn.scrobble.api.lastfm.ApiException
import com.arn.scrobble.api.lastfm.Artist
import com.arn.scrobble.api.lastfm.ImagesUrls
import com.arn.scrobble.api.lastfm.MusicEntry
import com.arn.scrobble.api.lastfm.PageAttr
import com.arn.scrobble.api.lastfm.PageResult
import com.arn.scrobble.api.lastfm.ScrobbleData
import com.arn.scrobble.api.lastfm.Track
import com.arn.scrobble.api.lastfm.User
import com.arn.scrobble.charts.ListeningActivity
import com.arn.scrobble.charts.TimePeriod
import com.arn.scrobble.utils.Stuff
import com.arn.scrobble.utils.Stuff.cacheStrategy
import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.io.readString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.time.Instant
import java.time.format.DateTimeParseException
import kotlin.coroutines.cancellation.CancellationException

internal val AccountType.playCollection: String
    get() = when (this) {
        AccountType.TEAL -> "fm.teal.feed.play"
        AccountType.ROCKSKY -> "app.rocksky.scrobble"
        else -> error("Not an AT Protocol history source")
    }

/** Public history of the connected DID at its verified PDS; no AT session is used for reads. */
internal class AtprotoScrobblable(
    account: UserAccountSerializable,
    private val client: () -> HttpClient = { Requesters.atprotoRepoReader },
) : Scrobblable(account) {
    private val collection = account.type.playCollection
    private val pds = requireNotNull(account.apiRoot).trimEnd('/')
    private val did = account.authKey
    private val recordPrefix = "at://$did/$collection/"
    private val lock = Mutex()
    // The account (DID and PDS) and collection are implicit in this instance.
    private val queries = LruCache<Query, History>(4)

    private data class Query(val from: Long, val to: Long, val limit: Int)

    private class History {
        val pages = mutableListOf<PageResult<Track>>()
        val seenCursors = mutableSetOf<String>()
        val seenRecords = mutableSetOf<String>()
        var nextCursor: String? = null
    }

    override suspend fun getRecents(
        page: Int,
        username: String,
        cached: Boolean,
        from: Long,
        to: Long,
        includeNowPlaying: Boolean,
        limit: Int,
    ): Result<PageResult<Track>> = try {
        require(username == userAccount.user.name) { "AT Protocol history is only available for the connected account" }
        require(page >= 1)
        val query = Query(from, to, limit.takeIf { it > 0 }?.coerceAtMost(100) ?: 50)
        Result.success(lock.withLock {
            val history = queries[query]
            if (cached) {
                history?.pages?.getOrNull(page - 1) ?: throw ApiException(504, "AT Protocol history is not cached")
            } else {
                // Page 1 is a refresh; a failed refresh must not replace a usable cached page.
                val current = if (page == 1 || history == null) History() else history
                if (page > current.pages.size + 1)
                    throw ApiException(-1, "Reload AT Protocol history from the first page")
                current.pages.getOrNull(page - 1) ?: readPage(current, query, page).also {
                    current.pages += it
                    queries.put(query, current)
                }
            }
        })
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Scan past filtered/malformed records without confusing a short or empty batch with exhaustion. */
    private suspend fun readPage(history: History, query: Query, page: Int): PageResult<Track> {
        val tracks = mutableListOf<Track>()
        val records = mutableSetOf<String>()
        val cursors = mutableSetOf<String>()
        var cursor = history.nextCursor
        if (page > 1 && cursor == null) return PageResult(PageAttr(page, page, null), tracks)
        repeat(24) {
            val requestCursor = cursor
            val remaining = query.limit - tracks.size
            val response = client().prepareGet("$pds/xrpc/com.atproto.repo.listRecords") {
                parameter("repo", did)
                parameter("collection", collection)
                parameter("limit", remaining)
                requestCursor?.let { parameter("cursor", it) }
                cacheStrategy(CacheStrategy.NETWORK_ONLY)
            }.execute {
                if (!it.status.isSuccess()) throw ApiException(it.status.value, it.status.description)
                val maxSize = 2 * 1024 * 1024L
                val body = it.bodyAsChannel().readBuffer(maxSize + 1)
                if (body.size > maxSize) throw ApiException(-1, "AT Protocol history response too large")
                Stuff.myJson.decodeFromString<RecordPage>(body.readString())
            }
            if (response.records.size > remaining)
                throw ApiException(-1, "PDS returned too many AT Protocol records")
            for (record in response.records) {
                val uri = record["uri"].text() ?: continue
                if (!uri.startsWith(recordPrefix) || !recordKey.matches(uri.removePrefix(recordPrefix)) ||
                    uri in history.seenRecords || !records.add(uri)) continue
                val track = (record["value"] as? JsonObject)?.toTrack(userAccount.type) ?: continue
                val date = track.date ?: continue
                if ((query.from < 0 || date >= query.from) && (query.to < 0 || date <= query.to))
                    tracks += track
            }
            cursor = response.cursor?.takeIf { it.isNotBlank() }
            val next = cursor
            if (next != null && (next in history.seenCursors || !cursors.add(next)))
                throw ApiException(-1, "PDS repeated an AT Protocol history cursor")
            if (next == null || tracks.size == query.limit) {
                history.nextCursor = next
                history.seenCursors += cursors
                history.seenRecords += records
                return PageResult(PageAttr(page, if (next == null) page else page + 1, null), tracks)
            }
        }
        throw ApiException(-1, "PDS history scan could not finish this page")
    }

    override suspend fun updateNowPlaying(scrobbleData: ScrobbleData) = unavailable<Nothing>()
    override suspend fun scrobble(scrobbleData: ScrobbleData) = unavailable<Nothing>()
    override suspend fun scrobble(scrobbleDatas: List<ScrobbleData>) = unavailable<Nothing>()
    override suspend fun loveOrUnlove(track: Track, love: Boolean) = unavailable<Nothing>()
    override suspend fun delete(track: Track) = unavailable<Nothing>()
    override suspend fun getLoves(page: Int, username: String, cacheStrategy: CacheStrategy, limit: Int) =
        unavailable<PageResult<Track>>()

    override suspend fun getFriends(page: Int, username: String, cached: Boolean, limit: Int) =
        unavailable<PageResult<User>>()

    override suspend fun loadDrawerData(username: String) = null

    override suspend fun getCharts(
        type: Int, timePeriod: TimePeriod, page: Int, username: String, cacheStrategy: CacheStrategy, limit: Int,
    ) = unavailable<PageResult<MusicEntry>>()

    override suspend fun getListeningActivity(timePeriod: TimePeriod, user: UserCached?, cacheStrategy: CacheStrategy): ListeningActivity =
        throw UnsupportedOperationException("Only Recents is available for this AT Protocol source")

    private fun <T> unavailable(): Result<T> =
        Result.failure(UnsupportedOperationException("Only Recents is available for this AT Protocol source"))

    @Serializable
    private data class RecordPage(val records: List<JsonObject>, val cursor: String? = null)

    private companion object {
        val recordKey = Regex("[a-zA-Z0-9._~:-]{1,512}")
    }
}

private fun JsonElement?.text() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
private fun JsonObject.names(key: String, field: String) = (get(key) as? JsonArray).orEmpty().mapNotNull {
    (it as? JsonObject)?.get(field).text()
}.joinToString(", ").takeIf { it.isNotEmpty() }

private fun JsonObject.toTrack(type: AccountType): Track? {
    if (get("\$type").text() != type.playCollection) return null
    val teal = type == AccountType.TEAL
    val title = get(if (teal) "trackName" else "title").text() ?: return null
    val artist = if (teal) {
        names("artists", "artistName") ?: (get("artistNames") as? JsonArray).orEmpty()
            .mapNotNull { it.text() }.joinToString(", ").takeIf { it.isNotEmpty() }
    } else {
        get("artist").text() ?: names("artists", "name")
    }
    val playedAt = get(if (teal) "playedTime" else "createdAt").text() ?: return null
    val date = try {
        Instant.parse(playedAt).toEpochMilli()
    } catch (_: DateTimeParseException) {
        return null
    } catch (_: ArithmeticException) {
        return null
    }
    val duration = (get("duration") as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }
    val artUrl = if (teal) null else get("albumArtUrl").text()?.takeIf { raw ->
        runCatching { URI(raw) }.getOrNull()?.let { it.scheme == "https" && it.host != null && it.rawUserInfo == null } == true
    }
    return Track(
        name = title,
        artist = Artist(artist.orEmpty()),
        album = get(if (teal) "releaseName" else "album").text()?.let { name ->
            Album(name, artist = if (teal) null else get("albumArtist").text()?.let { Artist(it) },
                image = artUrl?.let { ImagesUrls(medium = it, large = it) })
        },
        duration = if (teal) duration?.takeIf { it <= Long.MAX_VALUE / 1000 }?.times(1000) else duration,
        date = date,
    )
}
