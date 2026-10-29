package com.arn.scrobble.atproto.identity

import com.arn.scrobble.atproto.http.fetchJson
import com.arn.scrobble.atproto.http.requireHttpsOrigin
import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private val PLC_DID = Regex("^did:plc:[a-z2-7]{24}$")

@Serializable
data class ResolvedIdentity(
    val did: String,
    val handle: String?,
    val pds: String,
)

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
            requestedHandle = requireResolvableHandle(normalizeHandle(trimmed))
            did = resolveHandle(requestedHandle)
        }

        val document = resolveDidDocument(did)
        require(document.id == did) { "Resolved DID document ID does not match the requested DID" }
        val pds = document.pdsEndpoint()
        val handle = if (requestedHandle != null) {
            require(document.claimedHandle() == requestedHandle) {
                "Handle is not bidirectionally linked from the DID document"
            }
            requestedHandle
        } else {
            resolveVerifiedHandle(document, did)
        }

        return ResolvedIdentity(did = did, handle = handle, pds = pds)
    }

    suspend fun resolveDid(did: String): ResolvedIdentity = resolve(requireSupportedDid(did))

    private suspend fun resolveHandle(handle: String): String {
        requireResolvableHandle(handle)
        val url = origin(handleResolver) +
            "/xrpc/com.atproto.identity.resolveHandle?handle=" +
            URLEncoder.encode(handle, StandardCharsets.UTF_8.name())
        val element = httpClient.fetchJson(url, maxBytes = 4 * 1024, json = json)
        val response = json.decodeFromString<ResolveHandleResponse>(element.toString())
        return requireSupportedDid(response.did)
    }

    private suspend fun resolveDidDocument(did: String): DidDocument {
        val url = when {
            did.startsWith("did:plc:") -> origin(plcDirectory) + "/" +
                URLEncoder.encode(did, StandardCharsets.UTF_8.name())

            did.startsWith("did:web:") -> didWebDocumentUrl(did)
            else -> throw IllegalArgumentException("Unsupported DID method")
        }
        val element = httpClient.fetchJson(
            url = url,
            acceptedTypes = setOf("application/json", "application/did+json", "application/did+ld+json"),
            maxBytes = 20 * 1024,
            json = json,
        )
        return json.decodeFromString(element.toString())
    }

    private suspend fun resolveVerifiedHandle(document: DidDocument, did: String): String? {
        val handle = document.claimedHandle() ?: return null
        val resolved = try {
            resolveHandle(handle)
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            null
        }
        return handle.takeIf { resolved == did }
    }
}

@Serializable
private data class ResolveHandleResponse(val did: String)

internal fun requireSupportedDid(value: String): String {
    require(PLC_DID.matches(value) || isWebDid(value)) { "Unsupported or invalid AT Protocol DID" }
    return value
}

private fun isWebDid(value: String): Boolean {
    if (!value.startsWith("did:web:")) return false
    val host = value.removePrefix("did:web:")
    // Production AT Protocol only supports hostname-level did:web. Encoded
    // ports are a localhost-only development exception, not a native app input.
    return runCatching { requireResolvableHandle(parseHandle(host)) == host }.getOrDefault(false)
}

private fun didWebDocumentUrl(did: String): String {
    requireSupportedDid(did)
    return "https://${did.removePrefix("did:web:")}/.well-known/did.json"
}

private fun origin(value: String): String = requireHttpsOrigin(value, "Discovery origin")
