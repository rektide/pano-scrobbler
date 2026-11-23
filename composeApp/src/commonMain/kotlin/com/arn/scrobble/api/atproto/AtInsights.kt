package com.arn.scrobble.api.atproto

import kotlinx.serialization.json.JsonObject
import org.jetbrains.compose.resources.StringResource
import pano_scrobbler.composeapp.generated.resources.*
import java.time.Instant

internal enum class AtSection(val label: StringResource) {
    HISTORY(Res.string.at_section_history), ARTISTS(Res.string.at_section_artists), RELEASES(Res.string.at_section_releases), SONGS(Res.string.at_section_songs),
    ACTIVITY(Res.string.at_section_activity), LOVED(Res.string.at_section_loved), FOLLOWING(Res.string.at_section_following),
    FOLLOWERS(Res.string.at_section_followers),
}

internal enum class AtPeriod(val label: StringResource, val teal: String, val days: Int?) {
    WEEK(Res.string.at_period_week, "week", 7), MONTH(Res.string.at_period_month, "month", 30), ALL(Res.string.at_period_all, "all", null),
}

internal fun AtTarget.limitation(section: AtSection): StringResource? = when {
    this != AtTarget.TEAL -> null
    section == AtSection.LOVED -> Res.string.at_teal_love_na
    section in setOf(AtSection.FOLLOWERS, AtSection.FOLLOWING) -> Res.string.at_teal_graph_na
    else -> null
}

internal fun AtHistoryQuery.limitation(section: AtSection): StringResource? =
    if (source == AtReadSource.ROCKSKY_PDS && section != AtSection.HISTORY) Res.string.at_raw_history_only else target.limitation(section)

internal data class AtInsight(val id: String, val name: String, val detail: String? = null, val image: String? = null,
                              val actor: String? = null, val count: Long? = null, val rank: Int? = null,
                              val indexedTotal: Boolean = false)

internal fun AtPublic.insights(query: AtHistoryQuery, section: AtSection, period: AtPeriod, now: Long = System.currentTimeMillis()): AtPageWalk<String, AtInsight> =
    AtPageWalk("", AtInsight::id) { next ->
        val did = query.identity.did
        checkAt(query.limitation(section) == null && section != AtSection.HISTORY, AtProblem.UNSUPPORTED)
        val limit = 50
        val offset = next.toIntOrNull() ?: 0
        val start = period.days?.let { Instant.ofEpochMilli(now - it * 86_400_000L).toString() }
        val end = Instant.ofEpochMilli(now).toString()
        if (query.target == AtTarget.TEAL) {
            val doc = json(secureUrl(TEAL_VIEW).xrpc("fm.teal.site.getActorStats").addQueryParameter("did", did)
                .addQueryParameter("period", period.teal).addQueryParameter("limit", limit.toString()).build())
            checkAt(doc.text("did") == did && doc.text("period") == period.teal)
            val values = when (section) {
                AtSection.ARTISTS, AtSection.RELEASES, AtSection.SONGS -> doc.rows(when (section) {
                    AtSection.ARTISTS -> "artists"; AtSection.RELEASES -> "releases"; else -> "tracks"
                }).mapIndexed { i, j ->
                    val name = j.text(when (section) {
                        AtSection.ARTISTS -> "name"; AtSection.RELEASES -> "releaseName"; else -> "trackName"
                    }) ?: throw AtFailure(AtProblem.RESPONSE)
                    val count = j.number("plays") ?: throw AtFailure(AtProblem.RESPONSE)
                    AtInsight(i.toString(), name, count = count, rank = i + 1)
                }
                AtSection.ACTIVITY -> listOf(AtInsight("plays", "", count = doc.number("plays") ?: throw AtFailure(AtProblem.RESPONSE),
                    indexedTotal = true))
                else -> throw AtFailure(AtProblem.RESPONSE)
            }
            AtReadPage(values, null)
        } else when (section) {
            AtSection.FOLLOWERS, AtSection.FOLLOWING -> {
                val following = section == AtSection.FOLLOWING
                val doc = json(secureUrl(ROCKSKY_VIEW).xrpc(if (following) "app.rocksky.graph.getFollows" else "app.rocksky.graph.getFollowers")
                    .addQueryParameter("actor", did).addQueryParameter("limit", limit.toString()).apply {
                        if (next.isNotEmpty()) addQueryParameter("cursor", next)
                    }.build())
                checkAt(doc.obj("subject")?.text("did") == did)
                val values = doc.rows(if (following) "follows" else "followers").mapNotNull { row ->
                    val id = row.text("did") ?: return@mapNotNull null
                    AtInsight(id, row.text("displayName") ?: row.text("handle") ?: id, row.text("handle"), image(row.text("avatar")), id)
                }
                AtReadPage(values, doc.text("cursor")?.takeIf(String::isNotEmpty))
            }
            AtSection.LOVED -> {
                val rows = rocksky("actor.getActorLovedSongs", did, limit, offset).rows("tracks")
                AtReadPage(rows.mapNotNull { row ->
                    val id = row.text("uri") ?: row.text("id") ?: return@mapNotNull null
                    AtInsight(id, row.text("title") ?: return@mapNotNull null, row.text("artist"), image(row.text("albumArt")))
                }, (offset + rows.size).toString().takeIf { rows.size == limit })
            }
            AtSection.ARTISTS, AtSection.RELEASES, AtSection.SONGS -> {
                val artists = section == AtSection.ARTISTS
                val dates = if (start == null) emptyMap() else mapOf("startDate" to start, "endDate" to end)
                val (operation, property) = when (section) {
                    AtSection.ARTISTS -> "actor.getActorArtists" to "artists"
                    AtSection.RELEASES -> "actor.getActorAlbums" to "albums"
                    else -> "actor.getActorSongs" to "tracks"
                }
                val rows = rocksky(operation, did, limit, offset, dates).rows(property)
                AtReadPage(rows.mapIndexedNotNull { i, row ->
                    val id = row.text("uri") ?: row.text("id") ?: return@mapIndexedNotNull null
                    val name = row.text(if (artists) "name" else "title") ?: return@mapIndexedNotNull null
                    val count = row.number("playCount") ?: throw AtFailure(AtProblem.RESPONSE)
                    AtInsight(id, name, image = image(row.text(if (artists) "picture" else "albumArt")), count = count, rank = offset + i + 1)
                }, (offset + rows.size).toString().takeIf { rows.size == limit })
            }
            AtSection.ACTIVITY -> {
                checkAt(start != null)
                val rows = rocksky("charts.getScrobblesChart", did, extra = mapOf("from" to start!!, "to" to end)).rows("scrobbles")
                AtReadPage(rows.map { row ->
                    val date = row.text("date") ?: throw AtFailure(AtProblem.RESPONSE)
                    AtInsight(date, date, count = row.number("count") ?: throw AtFailure(AtProblem.RESPONSE))
                }, null)
            }
            AtSection.HISTORY -> throw AtFailure(AtProblem.RESPONSE)
        }
    }

private fun JsonObject.rows(name: String): List<JsonObject> = array(name)?.filterIsInstance<JsonObject>()
    ?: throw AtFailure(AtProblem.RESPONSE)
