package com.arn.scrobble.atproto.repo

import com.arn.scrobble.atproto.http.BufferedHttpResponse
import com.arn.scrobble.atproto.http.DpopHttp
import com.arn.scrobble.atproto.http.DpopNonceStore
import com.arn.scrobble.atproto.http.DpopServer
import com.arn.scrobble.atproto.oauth.AtProtoSession
import com.arn.scrobble.atproto.oauth.AtProtoSessionClient
import com.arn.scrobble.atproto.oauth.DpopProofFactory
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class CreatedRecord(
    val uri: String,
    val cid: String,
)

class AtProtoRepositoryException(
    val status: Int,
    message: String,
) : Exception(message)

internal class AtProtoRepository(
    private val httpClient: HttpClient,
    private val sessionClient: AtProtoSessionClient,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val nonces = DpopNonceStore()

    suspend fun createRecord(
        did: String,
        collection: String,
        record: JsonObject,
    ): CreatedRecord {
        require(record["\$type"]?.jsonPrimitive?.contentOrNull == collection) {
            "AT Protocol record type must match its collection"
        }

        var session = sessionClient.getSession(did)
        repeat(2) { attempt ->
            val response = createRecord(session, collection, record)
            if (attempt == 0 && response.isInvalidToken()) {
                session = sessionClient.refreshSession(did, staleAccessToken = session.token.accessToken)
            } else {
                return response.decodeCreateRecord(json)
            }
        }

        error("Invalid-token retry budget exhausted")
    }

    private suspend fun createRecord(
        session: AtProtoSession,
        collection: String,
        record: JsonObject,
    ): BufferedHttpResponse {
        val endpoint = "${session.pds}/xrpc/com.atproto.repo.createRecord"
        val body = json.encodeToString(
            CreateRecordRequest(
                repo = session.did,
                collection = collection,
                record = record,
            ),
        )

        return DpopHttp(
            httpClient = httpClient,
            proofFactory = DpopProofFactory(session.dpopKey),
            nonces = nonces,
        ).request(
            url = endpoint,
            method = HttpMethod.Post,
            server = DpopServer.RESOURCE,
            accessToken = session.token.accessToken,
        ) {
            accept(ContentType.Application.Json)
            contentType(ContentType.Application.Json)
            setBody(body)
        }
    }
}

@Serializable
private data class CreateRecordRequest(
    val repo: String,
    val collection: String,
    val record: JsonObject,
)

private fun BufferedHttpResponse.isInvalidToken(): Boolean {
    if (status != HttpStatusCode.Unauthorized) return false
    val challenge = headers[HttpHeaders.WWWAuthenticate] ?: return false
    val recognizedScheme = challenge.startsWith("DPoP", ignoreCase = true) ||
        challenge.startsWith("Bearer", ignoreCase = true)
    return recognizedScheme && challenge.contains("error=\"invalid_token\"", ignoreCase = true)
}

private fun BufferedHttpResponse.decodeCreateRecord(json: Json): CreatedRecord {
    if (!status.isSuccess()) {
        throw AtProtoRepositoryException(
            status = status.value,
            message = "com.atproto.repo.createRecord failed with HTTP ${status.value}: $body",
        )
    }
    val contentType = headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim()?.lowercase()
    require(contentType == "application/json") { "createRecord response is not JSON" }
    return json.decodeFromString(CreatedRecord.serializer(), body)
}
