package com.arn.scrobble.api.atproto

import com.arn.scrobble.api.Requesters
import com.arn.scrobble.api.lastfm.ApiException
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.util.Base64URL
import io.ktor.client.HttpClient
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.ParseException
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

const val ATPROTO_CLIENT_ID = "https://rektide.github.io/pano-scrobbler/oauth-client-metadata.json"
const val ATPROTO_REDIRECT_URI = "io.github.rektide:/pano-scrobbler/oauth/callback"

enum class AtprotoOAuthPermission(val scope: String) {
    TEAL_SCROBBLES("repo:fm.teal.feed.play?action=create&action=delete"),
    ROCKSKY_SCROBBLES("repo:app.rocksky.scrobble?action=create&action=delete"),
    TEAL_NOW_PLAYING("repo:fm.teal.actor.status?action=create&action=update&action=delete"),
    ROCKSKY_NOW_PLAYING("repo:app.rocksky.actor.status?action=create&action=update&action=delete"),
    ROCKSKY_LIKES("repo:app.rocksky.like?action=create&action=delete"),
}

@Serializable
@SerialName("oauth")
class AtprotoOAuthSession(
    override val did: String,
    override val pds: String,
    val issuer: String,
    val clientId: String,
    val scopes: Set<String>,
    val expiresAt: Instant,
    val accessToken: String,
    val refreshToken: String?,
    val dpop: AtprotoOAuthDpop,
) : AtprotoSession

/** The short-lived material needed to finish one authorization, even after process death. */
@Serializable
internal class AtprotoOAuthAttempt(
    val authorizationUrl: String,
    val issuer: String,
    val tokenEndpoint: String,
    val state: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val did: String,
    val pds: String,
    val handle: String,
    val permissions: Set<AtprotoOAuthPermission>,
    val verifier: String,
    val dpop: AtprotoOAuthDpop,
) {
    fun isActive(now: Instant = Clock.System.now()) = now >= createdAt && now < expiresAt
}

class AtprotoOAuthSignIn internal constructor(
    internal val attempt: AtprotoOAuthAttempt,
    exchange: suspend (String) -> AtprotoOAuthSession,
) {
    val authorizationUrl get() = attempt.authorizationUrl
    private val pending = AtomicReference<suspend (String) -> AtprotoOAuthSession>(exchange)

    /**
     * Checks the exact redirect URI, state and issuer before accepting a callback.
     * It consumes the pending exchange atomically before any network call, so even a denial or a failed exchange cannot be reused.
     */
    suspend fun complete(callbackUrl: String): AtprotoOAuthSession = complete(callbackUrl) { true }

    internal suspend fun complete(callbackUrl: String, consume: suspend () -> Boolean): AtprotoOAuthSession {
        val invalid = "Invalid AT Protocol OAuth callback"
        val uri = callbackUrl.takeIf { it.length <= 16 * 1024 }?.let(::parseOAuthUri)
        val query = uri?.rawQuery
        if (query == null || uri.rawFragment != null || callbackUrl.substringBefore('?') != ATPROTO_REDIRECT_URI)
            throw ApiException(-1, invalid)
        val parameters = try {
            query.split('&').groupBy(
                { URLDecoder.decode(it.substringBefore('='), "UTF-8") },
                { URLDecoder.decode(it.substringAfter('=', ""), "UTF-8") },
            )
        } catch (_: IllegalArgumentException) {
            throw ApiException(-1, invalid)
        }
        if (parameters["state"]?.singleOrNull() != attempt.state ||
            parameters["iss"]?.singleOrNull() != attempt.issuer
        ) throw ApiException(-1, invalid)

        val code = parameters["code"]
        val error = parameters["error"]
        val value = (if (error == null) code else if (code == null) error else null)?.singleOrNull()
        if (value.isNullOrBlank() || value.any { it.isISOControl() }) throw ApiException(-1, invalid)
        // Consume before doing I/O, even on denial, cancellation or a failed exchange.
        val exchange = pending.getAndSet(null)
        if (exchange == null || !attempt.isActive() || !consume())
            throw ApiException(-1, "AT Protocol OAuth attempt has expired or already finished")
        if (error != null) throw ApiException(-1, "AT Protocol authorization was not granted")
        return exchange(value)
    }
}

class AtprotoOAuth(private val baseClient: () -> HttpClient) {
    constructor() : this({ Requesters.atprotoOAuthKtorClient })

    /**
     * Resolves the account and pushes the authorization request before returning a browser URL that points to it.
     * The browser gets a request reference instead of the form; the PKCE verifier stays in Pano.
     * See the [AT Protocol OAuth profile](https://atproto.com/specs/oauth).
     */
    suspend fun start(handle: String, permissions: Set<AtprotoOAuthPermission>): AtprotoOAuthSignIn =
        client().use { http ->
            val identity = resolveAtprotoHandle(handle) { url ->
                if (parseOAuthUri(url)?.host?.isPublicAtprotoHost() != true)
                    throw ApiException(-1, "Invalid AT Protocol identity host")
                http.getIdentityDocument(url)
            }
            start(http, identity, handle.trim().removePrefix("@").lowercase(), permissions)
        }

    private fun client() = baseClient().config { followRedirects = false }

    private suspend fun start(
        http: HttpClient,
        identity: AtprotoIdentity,
        loginHint: String,
        permissions: Set<AtprotoOAuthPermission>,
    ): AtprotoOAuthSignIn {
        val server = http.discoverAtprotoOAuth(identity.pds)
        val scopes = listOf("atproto") + AtprotoOAuthPermission.entries.filter { it in permissions }.map { it.scope }
        val verifier = randomOAuthValue()
        val state = randomOAuthValue()
        val challenge = Base64URL.encode(MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray())).toString()
        val dpop = AtprotoOAuthDpop(ECKeyGenerator(Curve.P_256).generate())
        val par = http.oauthPost(server.par, dpop, AtprotoParResponse.serializer(), listOf(
            "client_id" to ATPROTO_CLIENT_ID,
            "redirect_uri" to ATPROTO_REDIRECT_URI,
            "response_type" to "code",
            "scope" to scopes.joinToString(" "),
            "state" to state,
            "code_challenge" to challenge,
            "code_challenge_method" to "S256",
            "login_hint" to loginHint,
        ))
        val requestUri = parseOAuthUri(par.requestUri)
        if (par.expiresIn <= 0L || !par.requestUri.startsWith("urn:ietf:params:oauth:request_uri:") ||
            requestUri?.isOpaque != true || requestUri.rawFragment != null
        ) throw ApiException(-1, "Invalid AT Protocol authorization request")

        val browserUrl = server.authorization.newBuilder()
            .addQueryParameter("client_id", ATPROTO_CLIENT_ID)
            .addQueryParameter("request_uri", par.requestUri)
            .build().toString()
        val now = Clock.System.now()
        return resume(AtprotoOAuthAttempt(browserUrl, server.issuer, server.token.toString(), state,
            now, now + par.expiresIn.coerceAtMost(600L).seconds, identity.did, identity.pds,
            loginHint, permissions, verifier, dpop))
    }

    internal fun resume(attempt: AtprotoOAuthAttempt): AtprotoOAuthSignIn = AtprotoOAuthSignIn(attempt) { code ->
        val tokenEndpoint = attempt.tokenEndpoint.toHttpUrl()
        require(tokenEndpoint.isHttps && tokenEndpoint.oauthOrigin() == attempt.issuer)
        client().use {
            exchange(it, tokenEndpoint, attempt, code)
        }
    }

    private suspend fun exchange(
        client: HttpClient,
        tokenEndpoint: HttpUrl,
        attempt: AtprotoOAuthAttempt,
        code: String,
    ): AtprotoOAuthSession {
        val token = client.oauthPost(tokenEndpoint, attempt.dpop, AtprotoTokenResponse.serializer(), listOf(
            "client_id" to ATPROTO_CLIENT_ID,
            "grant_type" to "authorization_code",
            "redirect_uri" to ATPROTO_REDIRECT_URI,
            "code" to code,
            "code_verifier" to attempt.verifier,
        ))
        val receivedAt = Clock.System.now()
        if (token.subject != attempt.did || !token.tokenType.equals("DPoP", ignoreCase = true) ||
            token.expiresIn <= 0 || !token.accessToken.isOAuthCredential() ||
            token.refreshToken?.isOAuthCredential() == false
        ) throw ApiException(-1, "Invalid AT Protocol OAuth token response")

        val granted = token.scope.split(' ').map(::canonicalScope).toSet()
        if (granted != (listOf("atproto") + attempt.permissions.map { it.scope }).map(::canonicalScope).toSet())
            throw ApiException(-1, "AT Protocol did not grant the selected permissions")
        return AtprotoOAuthSession(attempt.did, attempt.pds, attempt.issuer, ATPROTO_CLIENT_ID,
            granted, receivedAt + token.expiresIn.seconds,
            token.accessToken, token.refreshToken, attempt.dpop)
    }
}

private val oauthRandom = SecureRandom()

private fun randomOAuthValue() = Base64URL.encode(ByteArray(32).also(oauthRandom::nextBytes)).toString()

private fun String.isOAuthCredential() = isNotEmpty() && all { it in '!'..'~' }

private fun canonicalScope(scope: String): String = scope.substringBefore('?').let { name ->
    if ('?' !in scope) name else "$name?${scope.substringAfter('?').split('&').sorted().joinToString("&")}"
}

/**
 * DPoP (Demonstrating Proof of Possession) signs requests to prove Pano holds the private key bound to the session's tokens.
 * The same key is used throughout the session, but each server origin gets its own nonce.
 */
@Serializable(with = AtprotoOAuthDpop.Serializer::class)
class AtprotoOAuthDpop(private val key: ECKey) {
    private val nonces = mutableMapOf<String, String>()

    fun proof(method: String, url: HttpUrl, accessToken: String? = null): String =
        key.atprotoDpop(method, url, nonces[url.oauthOrigin()], accessToken)

    fun rememberNonce(url: HttpUrl, nonce: String) {
        nonces[url.oauthOrigin()] = nonce
    }

    // Only the token-bound key is durable; nonces are per-origin request state.
    private object Serializer : KSerializer<AtprotoOAuthDpop> {
        override val descriptor = PrimitiveSerialDescriptor("AtprotoOAuthDpop", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: AtprotoOAuthDpop) {
            encoder.encodeString(value.key.requirePrivateP256().toJSONString())
        }

        override fun deserialize(decoder: Decoder): AtprotoOAuthDpop {
            val key = try {
                ECKey.parse(decoder.decodeString())
            } catch (_: ParseException) {
                throw SerializationException("Invalid AT Protocol signing key")
            }
            return AtprotoOAuthDpop(key.requirePrivateP256())
        }

        private fun ECKey.requirePrivateP256() = apply {
            if (curve != Curve.P_256 || d == null)
                throw SerializationException("Invalid AT Protocol signing key")
        }
    }
}

@Serializable
private class AtprotoParResponse(
    @SerialName("request_uri") val requestUri: String,
    @SerialName("expires_in") val expiresIn: Long,
)

@Serializable
private class AtprotoTokenResponse(
    @SerialName("sub") val subject: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long,
    val scope: String,
)

fun parseOAuthUri(value: String): URI? = try {
    URI(value)
} catch (_: java.net.URISyntaxException) {
    null
}
