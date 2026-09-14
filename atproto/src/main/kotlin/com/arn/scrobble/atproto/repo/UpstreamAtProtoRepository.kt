package com.arn.scrobble.atproto.repo

import io.github.kikin81.atproto.runtime.XrpcClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Staged create-record transport backed by atproto-kotlin.
 *
 * This adapter is intentionally not wired into [com.arn.scrobble.atproto.posting.AtProtoPoster]
 * yet. Its internal boundary accepts and returns only Pano-owned values; the
 * supplied [XrpcClient] owns authentication, DPoP, response observation, retry,
 * decoding, and XRPC error mapping.
 */
internal class UpstreamAtProtoRepository(
    private val xrpcClient: XrpcClient,
) {
    suspend fun createRecord(
        did: String,
        collection: String,
        record: JsonObject,
    ): CreatedRecord {
        require(record["\$type"]?.jsonPrimitive?.contentOrNull == collection) {
            "AT Protocol record type must match its collection"
        }

        val response = xrpcClient.procedure(
            nsid = CREATE_RECORD_NSID,
            params = Unit,
            paramsSerializer = Unit.serializer(),
            input = UpstreamCreateRecordRequest(
                repo = did,
                collection = collection,
                record = record,
            ),
            inputSerializer = UpstreamCreateRecordRequest.serializer(),
            responseSerializer = UpstreamCreateRecordResponse.serializer(),
        )
        return CreatedRecord(uri = response.uri, cid = response.cid)
    }
}

@Serializable
private data class UpstreamCreateRecordRequest(
    val repo: String,
    val collection: String,
    val record: JsonObject,
)

@Serializable
private data class UpstreamCreateRecordResponse(
    val uri: String,
    val cid: String,
)

private const val CREATE_RECORD_NSID = "com.atproto.repo.createRecord"
