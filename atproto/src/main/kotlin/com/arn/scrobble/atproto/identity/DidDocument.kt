package com.arn.scrobble.atproto.identity

import com.arn.scrobble.atproto.http.httpOrigin
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

@Serializable
internal data class DidDocument(
    val id: String,
    val alsoKnownAs: List<String> = emptyList(),
    val service: List<DidService> = emptyList(),
)

@Serializable
internal data class DidService(
    val id: String,
    val type: JsonElement,
    val serviceEndpoint: JsonElement,
)

internal fun DidDocument.pdsEndpoint(): String {
    val expectedId = "$id#atproto_pds"
    val service = service.firstOrNull {
        (it.id == "#atproto_pds" || it.id == expectedId) &&
            it.type.containsString("AtprotoPersonalDataServer")
    } ?: throw IllegalArgumentException("DID document does not declare an AT Protocol PDS")
    val endpoint = service.serviceEndpoint.jsonPrimitive.contentOrNull
        ?: throw IllegalArgumentException("AT Protocol PDS endpoint must be a URL string")

    return requireServiceOrigin(endpoint)
}

private fun JsonElement.containsString(expected: String): Boolean = when {
    this is kotlinx.serialization.json.JsonPrimitive -> contentOrNull == expected
    this is kotlinx.serialization.json.JsonArray -> jsonArray.any { it.jsonPrimitive.contentOrNull == expected }
    else -> false
}

private fun requireServiceOrigin(value: String): String {
    val uri = URI(value)
    require(uri.scheme == "https") { "AT Protocol PDS endpoints must use HTTPS" }
    require(uri.host != null && uri.userInfo == null) { "AT Protocol PDS endpoint is invalid" }
    require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") {
        "AT Protocol PDS endpoint must not contain a path"
    }
    require(uri.rawQuery == null && uri.rawFragment == null) {
        "AT Protocol PDS endpoint must not contain a query or fragment"
    }
    return httpOrigin(value)
}
