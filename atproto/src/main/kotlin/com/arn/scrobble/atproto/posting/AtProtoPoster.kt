package com.arn.scrobble.atproto.posting

import com.arn.scrobble.atproto.oauth.AtProtoSessionClient
import com.arn.scrobble.atproto.repo.AtProtoRepository
import com.arn.scrobble.atproto.repo.CreatedRecord
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

enum class AtProtoTarget(val collection: String) {
    TEAL("fm.teal.feed.play"),
    ROCKSKY("app.rocksky.scrobble");

    companion object {
        fun grantedBy(
            scope: String,
            selectedTargets: Set<AtProtoTarget> = entries.toSet(),
        ): Set<AtProtoTarget> {
            // RFC 6749 scope tokens are space-separated, case-sensitive ASCII.
            if (scope.any { it != ' ' && it !in '\u0021'..'\u007e' || it == '"' || it == '\\' }) return emptySet()
            val values = scope.split(' ')
            if (values.any { it.isEmpty() } || "atproto" !in values) return emptySet()
            if ("transition:generic" in values) return entries.filterTo(linkedSetOf()) { it in selectedTargets }
            val permissions = values.mapNotNull(::parseRepoCreateGrant)

            return entries.filterTo(linkedSetOf()) { target ->
                target in selectedTargets && permissions.any { "*" in it || target.collection in it }
            }
        }
    }
}

data class AtProtoPostingConfig(
    val targets: Set<AtProtoTarget> = AtProtoTarget.entries.toSet(),
    val submissionClientAgent: String? = null,
) {
    init {
        require(targets.isNotEmpty()) { "At least one AT Protocol posting target must be enabled" }
    }
}

data class AtProtoPostingResult(
    val created: Map<AtProtoTarget, CreatedRecord>,
    val failures: Map<AtProtoTarget, Exception>,
) {
    val isSuccess: Boolean
        get() = failures.isEmpty()
}

class AtProtoPoster internal constructor(
    private val config: AtProtoPostingConfig,
    private val createRecord: suspend (did: String, collection: String, record: kotlinx.serialization.json.JsonObject) -> CreatedRecord,
    private val json: Json,
) {
    constructor(
        httpClient: HttpClient,
        sessionClient: AtProtoSessionClient,
        config: AtProtoPostingConfig = AtProtoPostingConfig(),
    ) : this(
        config = config,
        createRecord = AtProtoRepository(httpClient, sessionClient)::createRecord,
        json = Json { explicitNulls = false },
    )

    suspend fun post(did: String, play: Play): AtProtoPostingResult {
        val created = linkedMapOf<AtProtoTarget, CreatedRecord>()
        val failures = linkedMapOf<AtProtoTarget, Exception>()

        for (target in AtProtoTarget.entries) {
            if (target !in config.targets) continue
            try {
                val jsonRecord = when (target) {
                    AtProtoTarget.TEAL -> json.encodeToJsonElement(
                        TealPlayRecord.serializer(),
                        play.toTealRecord(config.submissionClientAgent),
                    ).jsonObject

                    AtProtoTarget.ROCKSKY -> json.encodeToJsonElement(
                        RockskyScrobbleRecord.serializer(),
                        play.toRockskyRecord(),
                    ).jsonObject
                }
                created[target] = createRecord(did, target.collection, jsonRecord)
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                failures[target] = cause
            }
        }

        return AtProtoPostingResult(created = created.toMap(), failures = failures.toMap())
    }
}
