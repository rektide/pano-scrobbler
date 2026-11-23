package com.arn.scrobble.api.atproto

import com.arn.scrobble.api.Requesters
import com.arn.scrobble.utils.Stuff
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import java.net.URI

internal data class AtprotoIdentity(val did: String, val pds: String)

private val label = "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?"
private val handlePattern = Regex("(?:$label\\.)+[a-z](?:[a-z0-9-]{0,61}[a-z0-9])?")

@Serializable
private data class HandleResolution(val did: String)

@Serializable
private data class DidDocument(
    val id: String,
    val alsoKnownAs: List<String> = emptyList(),
    val service: List<Service> = emptyList(),
) {
    @Serializable
    data class Service(val id: String, val type: String, val serviceEndpoint: String)
}

internal suspend fun resolveAtprotoHandle(
    input: String,
    getDocument: suspend (String) -> String = { url ->
        Requesters.baseKtorClient.prepareGet(url).execute { response ->
            check(response.status.isSuccess()) { "Identity lookup failed: ${response.status.value}" }
            val maxSize = 128 * 1024L
            val body = response.bodyAsChannel().readRemaining(maxSize + 1).readByteArray()
            require(body.size <= maxSize) { "Identity document too large" }
            body.decodeToString()
        }
    },
): AtprotoIdentity {
    val handle = input.trim().removePrefix("@").lowercase()
    require(handle.length <= 253 && handlePattern.matches(handle)) { "Invalid handle" }
    val resolution = getDocument("https://public.api.bsky.app/xrpc/com.atproto.identity.resolveHandle?handle=$handle")
    val did = Stuff.myJson.decodeFromString<HandleResolution>(resolution).did
    val url = when {
        did.matches(Regex("did:plc:[a-z2-7]{24}")) -> "https://plc.directory/$did"
        did.startsWith("did:web:") -> {
            val host = did.removePrefix("did:web:")
            require(host.length <= 253 && handlePattern.matches(host.lowercase())) { "Invalid did:web host" }
            "https://$host/.well-known/did.json"
        }
        else -> throw IllegalArgumentException("Unsupported DID")
    }
    val doc = Stuff.myJson.decodeFromString<DidDocument>(getDocument(url))
    require(doc.id == did && doc.alsoKnownAs.firstOrNull { it.startsWith("at://") }
        .equals("at://$handle", ignoreCase = true)) { "Handle does not match DID document" }
    val pds = doc.service.singleOrNull { (it.id == "#atproto_pds" || it.id == "$did#atproto_pds") &&
        it.type == "AtprotoPersonalDataServer" } ?: throw IllegalArgumentException("Missing PDS")
    val endpoint = URI(pds.serviceEndpoint)
    require(endpoint.scheme == "https" && endpoint.host != null && endpoint.rawUserInfo == null &&
        endpoint.rawQuery == null && endpoint.rawFragment == null && endpoint.rawPath in listOf("", "/") &&
        (endpoint.port == -1 || endpoint.port in 1..65535)) { "Invalid PDS endpoint" }
    return AtprotoIdentity(did, pds.serviceEndpoint.trimEnd('/'))
}
