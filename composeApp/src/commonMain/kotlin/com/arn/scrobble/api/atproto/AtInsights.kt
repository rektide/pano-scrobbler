package com.arn.scrobble.api.atproto

import kotlinx.serialization.json.JsonObject
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import pano_scrobbler.composeapp.generated.resources.*
import java.time.Instant

internal enum class AtSection(val label: StringResource) {
    HISTORY(Res.string.at_section_history), ARTISTS(Res.string.at_section_artists), RELEASES(Res.string.at_section_releases),
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

internal data class AtInsight(val id: String, val name: String, val detail: String? = null, val image: String? = null, val actor: String? = null)

internal fun AtPublic.insights(query: AtHistoryQuery, section: AtSection, period: AtPeriod, now: Long = System.currentTimeMillis()): AtPageWalk<String, AtInsight> =
    AtPageWalk("", AtInsight::id) { next ->
        val did = query.identity.did
        checkAt(query.target.limitation(section) == null && section != AtSection.HISTORY)
        val limit = 50
        val offset = next.toIntOrNull() ?: 0
        val start = period.days?.let { Instant.ofEpochMilli(now - it * 86_400_000L).toString() }
        val end = Instant.ofEpochMilli(now).toString()
        if (query.target == AtTarget.TEAL) {
            val doc = json(secureUrl(TEAL_VIEW).xrpc("fm.teal.site.getActorStats").addQueryParameter("did", did)
                .addQueryParameter("period", period.teal).addQueryParameter("limit", limit.toString()).build())
            checkAt(doc.text("did") == did && doc.text("period") == period.teal)
            val values = when (section) {
                AtSection.ARTISTS, AtSection.RELEASES -> doc.rows(if (section == AtSection.ARTISTS) "artists" else "releases").mapIndexed { i, j ->
                    val name = j.text(if (section == AtSection.ARTISTS) "name" else "releaseName") ?: throw AtFailure(AtProblem.RESPONSE)
                    val count = j.number("plays") ?: throw AtFailure(AtProblem.RESPONSE)
                    AtInsight(i.toString(), name, getString(Res.string.at_plays_rank, i + 1, count))
                }
                AtSection.ACTIVITY -> listOf(AtInsight("plays", getString(Res.string.at_indexed_plays,
                    doc.number("plays") ?: throw AtFailure(AtProblem.RESPONSE)), getString(period.label)))
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
            AtSection.ARTISTS, AtSection.RELEASES -> {
                val artists = section == AtSection.ARTISTS
                val dates = if (start == null) emptyMap() else mapOf("startDate" to start, "endDate" to end)
                val rows = rocksky(if (artists) "actor.getActorArtists" else "actor.getActorAlbums", did, limit, offset, dates)
                    .rows(if (artists) "artists" else "albums")
                AtReadPage(rows.mapIndexedNotNull { i, row ->
                    val id = row.text("uri") ?: row.text("id") ?: return@mapIndexedNotNull null
                    val name = row.text(if (artists) "name" else "title") ?: return@mapIndexedNotNull null
                    val count = row.number("playCount") ?: throw AtFailure(AtProblem.RESPONSE)
                    AtInsight(id, name, getString(Res.string.at_plays_rank, offset + i + 1, count), image(row.text(if (artists) "picture" else "albumArt")))
                }, (offset + rows.size).toString().takeIf { rows.size == limit })
            }
            AtSection.ACTIVITY -> {
                checkAt(start != null)
                val rows = rocksky("charts.getScrobblesChart", did, extra = mapOf("from" to start!!, "to" to end)).rows("scrobbles")
                AtReadPage(rows.map { row ->
                    val date = row.text("date") ?: throw AtFailure(AtProblem.RESPONSE)
                    AtInsight(date, date, getString(Res.string.at_play_count, row.number("count") ?: throw AtFailure(AtProblem.RESPONSE)))
                }, null)
            }
            AtSection.HISTORY -> throw AtFailure(AtProblem.RESPONSE)
        }
    }

private fun JsonObject.rows(name: String): List<JsonObject> = array(name)?.filterIsInstance<JsonObject>()
    ?: throw AtFailure(AtProblem.RESPONSE)
