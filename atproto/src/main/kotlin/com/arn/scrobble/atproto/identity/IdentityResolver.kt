package com.arn.scrobble.atproto.identity

import com.arn.scrobble.atproto.http.fetchJson
import com.arn.scrobble.atproto.http.httpOrigin
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private val PLC_DID = Regex("^did:plc:[a-z2-7]{24}$")

@Serializable
data class ResolvedIdentity(val did: String, val handle: String?, val pds: String)

internal class IdentityResolver(
    private val httpClient: HttpClient,
    private val handleResolver: String = "https://public.api.bsky.app",
    private val plcDirectory: String = "https://plc.directory",
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun resolve(identifier: String): ResolvedIdentity {
        val trimmed = identifier.trim()
        val requestedHandle: String?
        val did: String
        if (trimmed.startsWith("did:")) {
            requestedHandle = null
            did = requireSupportedDid(trimmed)
        } else {
            requestedHandle = normalizeHandle(trimmed)
            did = resolveHandle(requestedHandle)
        }
        val document = resolveDidDocument(did)
        require(document.id == did) { "Resolved DID document ID does not match the requested DID" }
        val pds = document.pdsEndpoint()
        val handle = if (requestedHandle != null) {
            require("at://$requestedHandle" in document.alsoKnownAs) { "Handle is not bidirectionally linked from the DID document" }
            requestedHandle
        } else resolveVerifiedHandle(document, did)
        return ResolvedIdentity(did, handle, pds)
    }

    suspend fun resolveDid(did: String): ResolvedIdentity = resolve(requireSupportedDid(did))

    private suspend fun resolveHandle(handle: String): String {
        val url = origin(handleResolver) + "/xrpc/com.atproto.identity.resolveHandle?handle=" +
            URLEncoder.encode(handle, StandardCharsets.UTF_8.name())
        val element = httpClient.fetchJson(url, maxBytes = 4 * 1024, json = json)
        return requireSupportedDid(json.decodeFromString<ResolveHandleResponse>(element.toString()).did)
    }

    private suspend fun resolveDidDocument(did: String): DidDocument {
        val url = when {
            did.startsWith("did:plc:") -> origin(plcDirectory) + "/" + URLEncoder.encode(did, StandardCharsets.UTF_8.name())
            did.startsWith("did:web:") -> didWebDocumentUrl(did)
            else -> throw IllegalArgumentException("Unsupported DID method")
        }
        return json.decodeFromString(httpClient.fetchJson(url,
            acceptedTypes = setOf("application/json", "application/did+ld+json"), maxBytes = 20 * 1024, json = json).toString())
    }

    private suspend fun resolveVerifiedHandle(document: DidDocument, did: String): String? {
        for (alias in document.alsoKnownAs) {
            if (!alias.startsWith("at://")) continue
            val handle = runCatching { normalizeHandle(alias.removePrefix("at://")) }.getOrNull() ?: continue
            val resolved = try { resolveHandle(handle) }
            catch (cause: CancellationException) { throw cause }
            catch (_: Exception) { null }
            if (resolved == did) return handle
        }
        return null
    }
}

@Serializable
private data class ResolveHandleResponse(val did: String)

private fun requireSupportedDid(value: String): String {
    require(PLC_DID.matches(value) || (value.startsWith("did:web:") && value.length > 8 &&
        value.none { it.isWhitespace() || it == '/' || it == '?' || it == '#' })) { "Unsupported or invalid AT Protocol DID" }
    return value
}

private fun didWebDocumentUrl(did: String): String {
    val parts = did.removePrefix("did:web:").split(':').map {
        URLDecoder.decode(it.replace("+", "%2B"), StandardCharsets.UTF_8.name())
    }
    val host = parts.first()
    require(host.isNotEmpty() && '/' !in host) { "Invalid did:web host" }
    val hostUri = URI("https://$host")
    require(hostUri.host != null && hostUri.userInfo == null && hostUri.rawPath.isNullOrEmpty()) { "Invalid did:web host" }
    val path = if (parts.size == 1) "/.well-known/did.json" else {
        require(parts.drop(1).all { it.isNotEmpty() && '/' !in it }) { "Invalid did:web path" }
        "/${parts.drop(1).joinToString("/")}/did.json"
    }
    return URI("https", null, hostUri.host.lowercase(), hostUri.port, path, null, null).toASCIIString()
}

private fun origin(value: String): String {
    val uri = URI(value)
    require(uri.scheme == "https" && uri.host != null && uri.userInfo == null) { "Discovery origin must use HTTPS" }
    require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") { "Discovery origin must not contain a path" }
    require(uri.rawQuery == null && uri.rawFragment == null) { "Discovery origin must not contain a query or fragment" }
    return httpOrigin(value)
}
