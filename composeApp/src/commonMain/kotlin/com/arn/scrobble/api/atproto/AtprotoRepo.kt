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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Instant

internal class AtprotoXrpcError(status: Int, val error: String?) : ApiException(status, "AT Protocol request failed") {
    val expired get() = error == "ExpiredToken" || code == 401 && error in listOf("invalid_token", "InvalidToken")
}

private suspend inline fun <reified T> HttpResponse.repoJson(): T {
    val limit = 128 * 1024L
    val buf = bodyAsChannel().readBuffer(limit + 1)
    if (buf.size > limit) throw ApiException(-1, "AT Protocol response too large")
    val body = buf.readString()
    if (!status.isSuccess()) {
        val error = runCatching { Stuff.myJson.decodeFromString<JsonObject>(body)["error"] as? JsonPrimitive }.getOrNull()
        if (status.value in listOf(400, 401) && error?.content == "use_dpop_nonce" && dpopNonce() != null)
            throw AtprotoNonceRequired()
        throw AtprotoXrpcError(status.value, error?.content)
    }
    return try {
        Stuff.myJson.decodeFromString<T>(body)
    } catch (_: kotlinx.serialization.SerializationException) {
        throw ApiException(-1, "Invalid AT Protocol response")
    }
}

@Serializable
internal data class RepoRecord(val uri: String, val cid: String, val value: JsonObject? = null)

/** Ask the authoritative PDS about this exact key, not the service's search index. */
internal suspend fun HttpClient.findPlay(pds: String, did: String, collection: String, rkey: String): RepoRecord? =
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

internal fun RepoRecord.receipt(did: String, collection: String, rkey: String): String {
    if (uri != "at://$did/$collection/$rkey" || !repoCid.matches(cid))
        throw ApiException(-1, "PDS returned a receipt for an invalid record address or CID")
    return cid
}

internal fun RepoRecord.matchesPlay(record: JsonObject): Boolean {
    val timestamp = if ("playedTime" in record) "playedTime" else "createdAt"
    val saved = value ?: return false
    fun JsonObject.time() = (get(timestamp) as? JsonPrimitive)?.content?.let { runCatching { Instant.parse(it) }.getOrNull() }
    return record.time() != null && record.time() == saved.time() && (record - timestamp) == (saved - timestamp)
}

internal suspend fun HttpClient.createPlay(
    session: AtprotoSession, collection: String, rkey: String, record: JsonObject,
): RepoRecord {
    val url = "${session.pds}/xrpc/com.atproto.repo.createRecord".toHttpUrl()
    val payload = buildJsonObject {
        put("repo", session.did)
        put("collection", collection)
        put("rkey", rkey)
        put("record", record)
    }.toString()
    repeat(2) { attempt ->
        try {
            return preparePost(url.toString()) {
                when (session) {
                    is AtprotoAppPasswordSession -> header(HttpHeaders.Authorization, "Bearer ${session.accessJwt}")
                    is AtprotoOAuthSession -> {
                        header(HttpHeaders.Authorization, "DPoP ${session.accessToken}")
                        header("DPoP", session.dpop.proof("POST", url, session.accessToken, resource = true))
                    }
                }
                setBody(AtprotoPostContent(payload, ContentType.Application.Json))
            }.execute { response ->
                if (session is AtprotoOAuthSession)
                    response.dpopNonce()?.let { session.dpop.rememberNonce(url, it, resource = true) }
                response.repoJson<RepoRecord>()
            }
        } catch (e: AtprotoNonceRequired) {
            if (session !is AtprotoOAuthSession || attempt != 0) throw e
        } catch (e: AtprotoXrpcError) {
            if (e.code == 403 && e.error in listOf("insufficient_scope", "InsufficientScope")) throw AtprotoPermissionMissing()
            throw e
        }
    }
    error("Unreachable DPoP nonce retry")
}

internal class AtprotoNonceRequired : ApiException(401, "AT Protocol server requires a new DPoP nonce")

internal fun HttpResponse.dpopNonce(): String? = headers.getAll("DPoP-Nonce")?.singleOrNull()
    ?.takeIf { it.length in 1..4096 && it.all { char -> char in '!'..'~' && char != ',' } }

@Serializable
internal data class AppPasswordRotation(val did: String, val accessJwt: String, val refreshJwt: String, val active: Boolean = true)

internal suspend fun HttpClient.rotateAppPassword(pds: String, did: String, token: String): AtprotoAppPasswordSession {
    val result = preparePost("$pds/xrpc/com.atproto.server.refreshSession") {
        header(HttpHeaders.Authorization, "Bearer $token")
        setBody(AtprotoPostContent("", ContentType.Application.Json))
    }.execute { it.repoJson<AppPasswordRotation>() }
    if (result.did != did || !result.active || !result.accessJwt.isOAuthCredential() || !result.refreshJwt.isOAuthCredential())
        throw AtprotoReconnect()
    return AtprotoAppPasswordSession(did, pds, result.accessJwt, result.refreshJwt)
}
