package com.arn.scrobble.api.atproto

import com.arn.scrobble.BuildKonfig
import com.arn.scrobble.api.DrawerData
import com.arn.scrobble.api.Requesters
import com.arn.scrobble.api.Scrobblable
import com.arn.scrobble.api.ScrobbleResult
import com.arn.scrobble.api.UserAccountSerializable
import com.arn.scrobble.api.UserCached
import com.arn.scrobble.api.cache.CacheStrategy
import com.arn.scrobble.api.lastfm.MusicEntry
import com.arn.scrobble.api.lastfm.PageResult
import com.arn.scrobble.api.lastfm.ScrobbleData
import com.arn.scrobble.api.lastfm.Track
import com.arn.scrobble.api.lastfm.User
import com.arn.scrobble.atproto.oauth.AtProtoSessionClient
import com.arn.scrobble.atproto.posting.AtProtoPoster
import com.arn.scrobble.atproto.posting.AtProtoPostingConfig
import com.arn.scrobble.atproto.posting.AtProtoPostingResult
import com.arn.scrobble.atproto.posting.AtProtoTarget
import com.arn.scrobble.atproto.posting.Play
import com.arn.scrobble.charts.ListeningActivity
import com.arn.scrobble.charts.TimePeriod
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class AtProtoScrobblable(userAccount: UserAccountSerializable) : Scrobblable(userAccount) {
    private val did: String
        get() = userAccount.authKey
    private val sessionClient = AtProtoSessionClient(Requesters.genericKtorClient, PanoAtProtoStore)
    private val posters = ConcurrentHashMap<Set<AtProtoTarget>, AtProtoPoster>()

    override suspend fun updateNowPlaying(scrobbleData: ScrobbleData) =
        Result.success(ScrobbleResult(ignored = false))

    override suspend fun scrobble(scrobbleData: ScrobbleData): Result<ScrobbleResult> {
        return try {
            val play = scrobbleData.toAtProtoPlay()
            val postId = play.postId(did)
            val targets = PanoAtProtoStore.targets(did)
            check(targets.isNotEmpty()) { "The AT Protocol session has no granted posting targets" }
            val previouslyCompleted = PanoAtProtoStore.completedTargets(postId)
            val remainingTargets = targets - previouslyCompleted

            if (remainingTargets.isEmpty()) {
                PanoAtProtoStore.savePostingProgress(postId, null)
                return Result.success(ScrobbleResult(ignored = false))
            }

            val posterConfig = AtProtoPostingConfig(
                targets = remainingTargets.toSet(),
                submissionClientAgent = "${BuildKonfig.APP_ID}/${BuildKonfig.VER_NAME}",
            )
            val poster = when (AtProtoFieldPath.selected) {
                AtProtoFieldBackend.LOCAL -> posters.computeIfAbsent(remainingTargets.toSet()) {
                    AtProtoPoster(
                        httpClient = Requesters.genericKtorClient,
                        sessionClient = sessionClient,
                        config = posterConfig,
                    )
                }
                AtProtoFieldBackend.UPSTREAM -> AtProtoPoster(
                    xrpcClient = PanoUpstreamAtProtoAccountCoordinator.getOrCreateClient(),
                    config = posterConfig,
                )
            }
            val result = poster.post(did, play)
            val completed = previouslyCompleted + result.created.keys

            if (result.isSuccess) {
                PanoAtProtoStore.savePostingProgress(postId, null)
                Result.success(
                    ScrobbleResult(
                        ignored = false,
                        msid = result.created.values.firstOrNull()?.uri,
                    ),
                )
            } else {
                PanoAtProtoStore.savePostingProgress(postId, completed)
                Result.failure(AtProtoPostingException(result))
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (cause: Exception) {
            Result.failure(cause)
        }
    }

    override suspend fun scrobble(scrobbleDatas: List<ScrobbleData>): Result<ScrobbleResult> {
        for (scrobbleData in scrobbleDatas) {
            val result = scrobble(scrobbleData)
            if (result.isFailure) return result
        }
        return Result.success(ScrobbleResult(ignored = false))
    }

    override suspend fun loveOrUnlove(track: Track, love: Boolean) =
        Result.success(ScrobbleResult(ignored = false))

    override suspend fun delete(track: Track) = Result.success(Unit)

    override suspend fun getRecents(
        page: Int,
        username: String,
        cached: Boolean,
        from: Long,
        to: Long,
        includeNowPlaying: Boolean,
        limit: Int,
    ): Result<PageResult<Track>> = Result.success(createEmptyPageResult())

    override suspend fun getLoves(
        page: Int,
        username: String,
        cacheStrategy: CacheStrategy,
        limit: Int,
    ): Result<PageResult<Track>> = Result.success(createEmptyPageResult())

    override suspend fun getFriends(
        page: Int,
        username: String,
        cached: Boolean,
        limit: Int,
    ): Result<PageResult<User>> = Result.success(createEmptyPageResult())

    override suspend fun loadDrawerData(username: String): Result<DrawerData>? = null

    override suspend fun getCharts(
        type: Int,
        timePeriod: TimePeriod,
        page: Int,
        username: String,
        cacheStrategy: CacheStrategy,
        limit: Int,
    ): Result<PageResult<out MusicEntry>> = Result.success(createEmptyPageResult())

    override suspend fun getListeningActivity(
        timePeriod: TimePeriod,
        user: UserCached?,
        cacheStrategy: CacheStrategy,
    ) = ListeningActivity()
}

private class AtProtoPostingException(result: AtProtoPostingResult) : Exception(
    result.failures.entries.joinToString(
        prefix = "AT Protocol posting failed: ",
        separator = "; ",
    ) { (target, cause) -> "${target.name}: ${cause.message ?: cause::class.simpleName}" },
)

private fun ScrobbleData.toAtProtoPlay() = Play(
    track = track,
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    playedAtMillis = timestamp,
    durationMillis = duration?.takeIf { it > 0 },
    trackNumber = trackNumber?.takeIf { it > 0 },
    sourceTag = appId,
)

private fun Play.postId(did: String): String {
    val input = listOf(
        did,
        playedAtMillis.toString(),
        track,
        artist,
        album.orEmpty(),
        albumArtist.orEmpty(),
        durationMillis?.toString().orEmpty(),
        trackNumber?.toString().orEmpty(),
        sourceTag.orEmpty(),
    ).joinToString("\u0000")
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
    val hex = "0123456789abcdef"
    return buildString(digest.size * 2) {
        digest.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(hex[value ushr 4])
            append(hex[value and 0x0f])
        }
    }
}
