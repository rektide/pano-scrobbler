package com.arn.scrobble.api.atproto

import com.arn.scrobble.BuildKonfig
import com.arn.scrobble.api.*
import com.arn.scrobble.api.cache.CacheStrategy
import com.arn.scrobble.api.lastfm.*
import com.arn.scrobble.atproto.posting.AtProtoTarget
import com.arn.scrobble.atproto.posting.CompletedPlay
import com.arn.scrobble.atproto.oauth.AtProtoAccountAuthority
import androidx.datastore.core.DataStore
import com.arn.scrobble.pref.MainPrefs
import com.arn.scrobble.charts.ListeningActivity
import com.arn.scrobble.charts.TimePeriod
import com.arn.scrobble.utils.PlatformStuff
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class AtProtoScrobblable internal constructor(
    userAccount: UserAccountSerializable,
    private val authority: AtProtoAccountAuthority,
    private val prefs: DataStore<MainPrefs>,
    private val clientAgent: String,
) : Scrobblable(userAccount) {
    constructor(userAccount: UserAccountSerializable) : this(userAccount, PanoAtProto.authority,
        PlatformStuff.mainPrefs, "${BuildKonfig.APP_ID}/${BuildKonfig.VER_NAME}")
    override suspend fun updateNowPlaying(scrobbleData: ScrobbleData) = Result.success(ScrobbleResult(ignored = true))

    override suspend fun scrobble(scrobbleData: ScrobbleData): Result<ScrobbleResult> = try {
        val handle = checkNotNull(userAccount.atProtoSession) { "AT Protocol account reference is missing" }
        val reference = prefs.data.first().scrobbleAccounts.singleOrNull {
            it.type == AccountType.ATPROTO && it.atProtoSession == handle
        } ?: error("AT Protocol account has been removed or replaced")
        val current = checkNotNull(authority.currentAccount()) { "AT Protocol authorization is missing" }
        check(current.session == handle) { "AT Protocol authorization has been replaced" }
        val targets = if (reference.canScrobble) reference.atProtoTargets intersect AtProtoTarget.grantedBy(current.grantedScopes) else emptySet()
        if (targets.isEmpty()) Result.success(ScrobbleResult(ignored = true)) else {
            val play = CompletedPlay(scrobbleData.track, scrobbleData.artist, scrobbleData.album, scrobbleData.albumArtist,
                scrobbleData.timestamp, scrobbleData.duration?.takeIf { it > 0 }, scrobbleData.trackNumber?.takeIf { it > 0 }, scrobbleData.appId)
            val records = AtProtoTarget.entries.filter { it in targets }.associate { it.collection to play.record(it, clientAgent) }
            val created = authority.postRecords(handle, play.id(), records)
            Result.success(ScrobbleResult(ignored = false, msid = created?.uri))
        }
    } catch (cause: CancellationException) { throw cause }
    catch (cause: Exception) { Result.failure(cause) }

    override suspend fun scrobble(scrobbleDatas: List<ScrobbleData>): Result<ScrobbleResult> {
        for (play in scrobbleDatas) scrobble(play).let { if (it.isFailure) return it }
        return Result.success(ScrobbleResult(ignored = false))
    }

    override suspend fun loveOrUnlove(track: Track, love: Boolean): Result<ScrobbleResult> = Result.failure(UnsupportedOperationException("AT Protocol love is not supported"))
    override suspend fun delete(track: Track): Result<Unit> = Result.failure(UnsupportedOperationException("AT Protocol delete is not supported"))
    override suspend fun getRecents(page: Int, username: String, cached: Boolean, from: Long, to: Long, includeNowPlaying: Boolean, limit: Int): Result<PageResult<Track>> = Result.success(createEmptyPageResult())
    override suspend fun getLoves(page: Int, username: String, cacheStrategy: CacheStrategy, limit: Int): Result<PageResult<Track>> = Result.success(createEmptyPageResult())
    override suspend fun getFriends(page: Int, username: String, cached: Boolean, limit: Int): Result<PageResult<User>> = Result.success(createEmptyPageResult())
    override suspend fun loadDrawerData(username: String): Result<DrawerData>? = null
    override suspend fun getCharts(type: Int, timePeriod: TimePeriod, page: Int, username: String, cacheStrategy: CacheStrategy, limit: Int): Result<PageResult<out MusicEntry>> = Result.success(createEmptyPageResult())
    override suspend fun getListeningActivity(timePeriod: TimePeriod, user: UserCached?, cacheStrategy: CacheStrategy) = ListeningActivity()
}
