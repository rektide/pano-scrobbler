package com.arn.scrobble.api.atproto

import com.arn.scrobble.api.lastfm.ApiException
import com.arn.scrobble.utils.Stuff
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import io.ktor.utils.io.readBuffer
import kotlinx.io.readString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

class AtprotoXrpcError(status: Int, val error: String?) : ApiException(status, "AT Protocol request failed") {
    val expired get() = error == "ExpiredToken" || code == 401 && error in listOf("invalid_token", "InvalidToken")
}

private suspend inline fun <reified T> HttpResponse.repoJson(): T {
    val limit = 128 * 1024L
    val buf = bodyAsChannel().readBuffer(limit + 1)
    if (buf.size > limit) throw ApiException(-1, "AT Protocol response too large")
    val body = buf.readString()
    if (!status.isSuccess()) {
        val error = runCatching { Stuff.myJson.decodeFromString<JsonObject>(body)["error"] as? JsonPrimitive }.getOrNull()
        throw AtprotoXrpcError(status.value, error?.content)
    }
    return try {
        Stuff.myJson.decodeFromString<T>(body)
    } catch (_: kotlinx.serialization.SerializationException) {
        throw ApiException(-1, "Invalid AT Protocol response")
    }
}

@Serializable
data class RepoRecord(val uri: String, val cid: String, val value: JsonObject? = null)

/** Ask the authoritative PDS about this exact key, not the service's search index. */
suspend fun HttpClient.findPlay(pds: String, did: String, collection: String, rkey: String): RepoRecord? =
    try {
        prepareGet("$pds/xrpc/com.atproto.repo.getRecord") {
            parameter("repo", did)
            parameter("collection", collection)
            parameter("rkey", rkey)
            header(HttpHeaders.CacheControl, "no-cache")
        }.execute { it.repoJson<RepoRecord>() }
    } catch (e: AtprotoXrpcError) {
        if (e.error == "RecordNotFound" && e.code in listOf(400, 404)) null else throw e
    }

private val repoCid = Regex("bafyrei[a-z2-7]{51}[aeimquy4]")

fun RepoRecord.receipt(did: String, collection: String, rkey: String): String {
    if (uri != "at://$did/$collection/$rkey" || !repoCid.matches(cid))
        throw ApiException(-1, "PDS returned a receipt for an invalid record address or CID")
    return cid
}

fun RepoRecord.matchesPlay(record: JsonObject): Boolean {
    val timestamp = if ("playedTime" in record) "playedTime" else "createdAt"
    val saved = value ?: return false
    fun JsonObject.time() = (get(timestamp) as? JsonPrimitive)?.content?.let { runCatching { Instant.parse(it) }.getOrNull() }
    return record.time() != null && record.time() == saved.time() && (record - timestamp) == (saved - timestamp)
}

suspend fun HttpClient.createPlay(
    session: AtprotoAppPasswordSession, collection: String, rkey: String, record: JsonObject,
): RepoRecord {
    val payload = buildJsonObject {
        put("repo", session.did)
        put("collection", collection)
        put("rkey", rkey)
        put("record", record)
    }.toString()
    try {
        return preparePost("${session.pds}/xrpc/com.atproto.repo.createRecord") {
            header(HttpHeaders.Authorization, "Bearer ${session.accessJwt}")
            setBody(AtprotoPostContent(payload, ContentType.Application.Json))
        }.execute { it.repoJson<RepoRecord>() }
    } catch (e: AtprotoXrpcError) {
        if (e.code == 403 && e.error in listOf("insufficient_scope", "InsufficientScope")) throw AtprotoPermissionMissing()
        throw e
    }
}

@Serializable
private class AppPasswordRotation(val did: String, val accessJwt: String, val refreshJwt: String, val active: Boolean = true)

suspend fun HttpClient.rotateAppPassword(pds: String, did: String, token: String): AtprotoAppPasswordSession {
    if (!token.isBearerToken()) throw AtprotoReconnect()
    val result = preparePost("$pds/xrpc/com.atproto.server.refreshSession") {
        header(HttpHeaders.Authorization, "Bearer $token")
        setBody(AtprotoPostContent("", ContentType.Application.Json))
    }.execute { it.repoJson<AppPasswordRotation>() }
    if (result.did != did || !result.active || !result.accessJwt.isBearerToken() || !result.refreshJwt.isBearerToken())
        throw AtprotoReconnect()
    return AtprotoAppPasswordSession(did, pds, result.accessJwt, result.refreshJwt)
}

private fun String.isBearerToken() = isNotEmpty() && all { it in '!'..'~' }
