package com.arn.scrobble.api.atproto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl

@Serializable
internal data class AtIdentity(val did: String, val pds: String, val handle: String? = null)

private val handleLabel = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
private val plcDid = Regex("did:plc:[a-z2-7]{24}")

private fun validHandle(value: String): Boolean {
    val labels = value.split('.')
    return value.length <= 253 && labels.size >= 2 && labels.last().firstOrNull()?.let { it in 'a'..'z' } == true &&
        labels.last() !in setOf("local", "localhost", "invalid", "test", "example", "onion", "alt", "internal", "arpa") &&
        labels.all(handleLabel::matches)
}

internal class AtIdentityResolver(private val wire: AtWire = anonymousAt) {
    suspend fun resolve(requested: String): AtIdentity {
        val input = requested.trim().removePrefix("at://").removePrefix("@")
        val handle = input.lowercase().takeUnless { input.startsWith("did:") }
        if (handle != null) checkAt(validHandle(handle), AtProblem.IDENTITY)
        // The public resolver implements HTTPS + DNS TXT, including DNS on Android.
        val did = if (handle == null) input else json(secureUrl("https://public.api.bsky.app").xrpc("com.atproto.identity.resolveHandle")
            .addQueryParameter("handle", handle).build()).text("did") ?: throw AtFailure(AtProblem.IDENTITY)
        val documentUrl = when {
            plcDid.matches(did) -> secureUrl("https://plc.directory/$did")
            did.startsWith("did:web:") && validHandle(did.removePrefix("did:web:")) ->
                secureUrl("https://${did.removePrefix("did:web:")}/.well-known/did.json")
            else -> throw AtFailure(AtProblem.IDENTITY)
        }
        return validateDocument(json(documentUrl), did, handle)
    }

    private suspend fun json(url: HttpUrl): JsonObject {
        val reply = wire.get(url)
        checkAt(reply.code == 200, if (reply.transient) AtProblem.NETWORK else AtProblem.IDENTITY)
        return reply.json
    }
}

internal fun validateDocument(doc: JsonObject, did: String, handle: String?): AtIdentity {
    checkAt(doc.text("id") == did, AtProblem.IDENTITY)
    checkAt(handle == null || doc.strings("alsoKnownAs").any { it.equals("at://$handle", true) }, AtProblem.IDENTITY)
    val service = doc.array("service").orEmpty().filterIsInstance<JsonObject>().singleOrNull {
        it.text("id") in setOf("#atproto_pds", "$did#atproto_pds")
    }
    checkAt(service?.text("type") == "AtprotoPersonalDataServer", AtProblem.IDENTITY)
    val pds = secureUrl(service?.text("serviceEndpoint"))
    checkAt(pds == pds.origin(), AtProblem.IDENTITY)
    return AtIdentity(did, pds.toString(), handle)
}
