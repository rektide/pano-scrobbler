package com.arn.scrobble.api.atproto

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.RequestBody
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Date
import java.util.UUID

internal const val AT_CLIENT = "https://kawaiidango.github.io/pano-scrobbler/oauth-client-metadata.json"
internal const val AT_CALLBACK = "io.github.kawaiidango:/pano-scrobbler/oauth/callback"
private val random = SecureRandom()
internal fun atSecret() = Base64URL.encode(ByteArray(32).also(random::nextBytes)).toString()
internal fun atHash(value: String) = Base64URL.encode(MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray())).toString()
internal fun form(vararg fields: Pair<String, String>) = FormBody.Builder().apply { fields.forEach { (k, v) -> add(k, v) } }.build()

internal fun scope(permissions: Map<String, Set<String>>) = (listOf("atproto") + permissions.map { (nsid, actions) ->
    "repo:$nsid?" + actions.sorted().joinToString("&") { "action=$it" }
}).joinToString(" ")

internal fun parseScope(raw: String?, requested: Map<String, Set<String>>): Map<String, Set<String>> {
    val rows = raw.orEmpty().split(' ')
    checkAt("atproto" in rows, AtProblem.LOGIN)
    return requested.mapValues { (collection, allowed) ->
        rows.asSequence().filter { it.startsWith("repo:$collection?") }.flatMap { row ->
            val parameters = row.substringAfter('?').split('&')
            // Don't erase a constraint by extracting an action from the middle of it.
            // Accept only this client's unconditional, explicit action vocabulary.
            if (parameters.distinct().size != parameters.size || parameters.any { it !in unconditionalActions }) emptySequence()
            else parameters.asSequence().map { it.removePrefix("action=") }
        }.toSet().intersect(allowed)
    }
}

private val unconditionalActions = setOf("action=create", "action=update", "action=delete")

internal fun parseAtCallback(raw: String): Map<String, String> {
    checkAt(raw.length <= 16_384 && raw.startsWith("$AT_CALLBACK?"), AtProblem.CALLBACK)
    val uri = try { URI(raw) } catch (_: Exception) { throw AtFailure(AtProblem.CALLBACK) }
    checkAt(uri.rawFragment == null && uri.rawAuthority == null && uri.rawPath == "/pano-scrobbler/oauth/callback", AtProblem.CALLBACK)
    val query = secureUrl("https://invalid.invalid/").newBuilder().encodedQuery(uri.rawQuery).build()
    val pairs = (0 until query.querySize).map { query.queryParameterName(it) to query.queryParameterValue(it) }
    checkAt(pairs.all { (k, v) -> k.isNotEmpty() && !v.isNullOrEmpty() } && pairs.map { it.first }.distinct().size == pairs.size, AtProblem.CALLBACK)
    return pairs.associate { it.first to it.second.orEmpty() }
}

internal class AtOAuth(private val public: AtWire, private val private: AtWire, private val clock: () -> Long) {
    suspend fun checkMetadata(requested: Map<String, Set<String>>) {
        val reply = try { public.get(secureUrl(AT_CLIENT)) } catch (e: java.io.IOException) { throw AtFailure(AtProblem.METADATA) }
        try {
            checkAt(reply.code == 200 && reply.mediaType == "application/json", AtProblem.METADATA)
            val doc = reply.json
            checkAt(doc.text("client_id") == AT_CLIENT && doc.strings("redirect_uris") == listOf(AT_CALLBACK) &&
                doc.text("application_type") == "native" && doc.flag("dpop_bound_access_tokens") == true &&
                doc.text("token_endpoint_auth_method") == "none" &&
                doc.strings("response_types") == listOf("code") && doc.strings("grant_types").toSet() == setOf("authorization_code", "refresh_token") &&
                parseScope(doc.text("scope"), requested) == requested, AtProblem.METADATA)
        } catch (_: AtFailure) { throw AtFailure(AtProblem.METADATA) }
    }

    suspend fun discover(identity: AtIdentity): AtIssuer {
        val pds = secureUrl(identity.pds)
        suspend fun get(url: HttpUrl) = public.get(url).let {
            checkAt(it.code == 200 && it.mediaType == "application/json", if (it.transient) AtProblem.NETWORK else AtProblem.IDENTITY)
            it.json
        }
        val resource = get(pds.resolve("/.well-known/oauth-protected-resource")!!)
        checkAt(secureUrl(resource.text("resource")) == pds, AtProblem.IDENTITY)
        val issuer = resource.strings("authorization_servers").singleOrNull() ?: throw AtFailure(AtProblem.IDENTITY)
        val origin = secureUrl(issuer)
        checkAt(origin == origin.origin() && origin.toString().removeSuffix("/") == issuer, AtProblem.IDENTITY)
        val metadata = get(origin.resolve("/.well-known/oauth-authorization-server")!!)
        checkAt(metadata.text("issuer") == issuer && metadata.flag("require_pushed_authorization_requests") == true &&
            metadata.flag("authorization_response_iss_parameter_supported") == true &&
            metadata.flag("client_id_metadata_document_supported") == true &&
            "atproto" in metadata.strings("scopes_supported") && "none" in metadata.strings("token_endpoint_auth_methods_supported") &&
            "ES256" in metadata.strings("dpop_signing_alg_values_supported") && "S256" in metadata.strings("code_challenge_methods_supported") &&
            "code" in metadata.strings("response_types_supported") &&
            metadata.strings("grant_types_supported").containsAll(listOf("authorization_code", "refresh_token")), AtProblem.IDENTITY)
        fun endpoint(name: String): String = secureUrl(metadata.text(name)).also {
            checkAt(it.origin() == origin && it.query == null, AtProblem.IDENTITY)
        }.toString()
        return AtIssuer(issuer, endpoint("pushed_authorization_request_endpoint"), endpoint("authorization_endpoint"),
            endpoint("token_endpoint"), metadata.text("revocation_endpoint")?.let { endpoint("revocation_endpoint") })
    }

    suspend fun begin(identity: AtIdentity, settings: AtSettings): Pair<AtPending, String> {
        val requested = settings.requested()
        checkMetadata(requested)
        val issuer = discover(identity)
        val key = AtSigner(ECKeyGenerator(Curve.P_256).generate().toJSONString())
        val state = atSecret()
        val verifier = atSecret()
        val (reply, nonce) = dpop(secureUrl(issuer.par), key, form(
            "client_id" to AT_CLIENT, "response_type" to "code", "redirect_uri" to AT_CALLBACK,
            "scope" to scope(requested), "state" to state, "code_challenge" to atHash(verifier),
            "code_challenge_method" to "S256", "login_hint" to (identity.handle ?: identity.did),
        ))
        checkAt(reply.ok, if (reply.transient) AtProblem.NETWORK else AtProblem.LOGIN)
        val requestUri = reply.json.text("request_uri")?.takeIf { it.startsWith("urn:ietf:params:oauth:request_uri:") && it.length <= 2048 }
            ?: throw AtFailure(AtProblem.RESPONSE)
        val parTtl = reply.json.number("expires_in") ?: throw AtFailure(AtProblem.RESPONSE)
        checkAt(parTtl > 0)
        val url = secureUrl(issuer.authorize).newBuilder().addQueryParameter("client_id", AT_CLIENT)
            .addQueryParameter("request_uri", requestUri).build().toString()
        return AtPending(identity, issuer, key.copy(nonce = nonce), state, verifier,
            clock() + parTtl.coerceAtMost(600) * 1000, settings, requested) to url
    }

    suspend fun token(identity: AtIdentity, generation: String, issuer: AtIssuer, signer: AtSigner,
                      requested: Map<String, Set<String>>, body: RequestBody): AtSession {
        val (reply, nonce) = dpop(secureUrl(issuer.token), signer, body)
        checkAt(reply.ok, if (reply.transient) AtProblem.NETWORK else AtProblem.LOGIN)
        val json = reply.json
        checkAt(json.text("sub") == identity.did && json.text("token_type").equals("DPoP", true), AtProblem.LOGIN)
        val expires = json.number("expires_in") ?: throw AtFailure(AtProblem.RESPONSE)
        checkAt(expires in 1..86_400)
        return AtSession(identity, generation, AtMethod.OAUTH, requireToken(json.text("access_token")), requireToken(json.text("refresh_token")),
            clock() + expires * 1000, parseScope(json.text("scope"), requested), issuer, signer.copy(nonce = nonce), grantVersion = 1)
    }

    suspend fun dpop(url: HttpUrl, signer: AtSigner, body: RequestBody, access: String? = null): Pair<AtReply, String?> {
        val key = ECKey.parse(signer.key)
        suspend fun attempt(nonce: String?): AtReply {
            val claims = JWTClaimsSet.Builder().jwtID(UUID.randomUUID().toString()).issueTime(Date(clock()))
                .claim("htm", "POST").claim("htu", url.newBuilder().query(null).build().toString())
            if (nonce != null) claims.claim("nonce", nonce)
            if (access != null) claims.claim("ath", atHash(access))
            val proof = SignedJWT(JWSHeader.Builder(JWSAlgorithm.ES256).type(JOSEObjectType("dpop+jwt"))
                .jwk(key.toPublicJWK()).build(), claims.build()).apply { sign(ECDSASigner(key)) }.serialize()
            val headers = if (access == null) mapOf("DPoP" to proof) else mapOf("DPoP" to proof, "Authorization" to "DPoP $access")
            return private.post(url, body, headers)
        }
        val first = attempt(signer.nonce)
        val challenge = if (access == null) first.code == 400 && first.error == "use_dpop_nonce"
            else first.code == 401 && (first.challenges.any {
                it.scheme.equals("DPoP", ignoreCase = true) && it.authParams["error"] == "use_dpop_nonce"
            } || first.challenges.isEmpty() && first.error == "use_dpop_nonce")
        val reply = if (challenge && first.nonce != null && first.nonce != signer.nonce) attempt(first.nonce) else first
        checkAt(reply.nonce != null || !reply.ok, AtProblem.RESPONSE)
        return reply to reply.nonce
    }
}

internal fun requireToken(value: String?): String = value?.takeIf { it.length in 1..16384 && it.all { c -> c in '!'..'~' } }
    ?: throw AtFailure(AtProblem.RESPONSE)
