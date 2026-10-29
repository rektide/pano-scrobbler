package com.arn.scrobble.atproto.identity

import com.arn.scrobble.atproto.http.requireHttpsOrigin
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.JsonPrimitive

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
            (it.type as? JsonPrimitive)?.takeIf { type -> type.isString }?.content == "AtprotoPersonalDataServer"
    } ?: throw IllegalArgumentException("DID document does not declare an AT Protocol PDS")
    val endpoint = (service.serviceEndpoint as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        ?: throw IllegalArgumentException("AT Protocol PDS endpoint must be a URL string")

    return requireHttpsOrigin(endpoint, "AT Protocol PDS endpoint")
}

internal fun DidDocument.claimedHandle(): String? {
    for (alias in alsoKnownAs) {
        if (!alias.startsWith("at://")) continue
        val handle = runCatching { parseHandle(alias.removePrefix("at://")) }.getOrNull()
        if (handle != null) return handle
    }
    return null
}
