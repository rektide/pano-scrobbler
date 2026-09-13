package com.arn.scrobble.atproto.oauth

import com.arn.scrobble.atproto.crypto.P256PrivateJwk
import com.arn.scrobble.atproto.crypto.generateP256PrivateJwk
import com.arn.scrobble.atproto.http.BufferedHttpResponse
import com.arn.scrobble.atproto.http.DpopHttp
import com.arn.scrobble.atproto.http.DpopNonceStore
import com.arn.scrobble.atproto.http.DpopServer
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.Clock

private const val AUTHORIZATION_LIFETIME_MILLIS = 10 * 60 * 1_000L

class AtProtoOAuthClient internal constructor(
    private val httpClient: HttpClient,
    private val config: OAuthClientConfig,
    private val authorizationStates: AuthorizationStateStore,
    private val sessions: AtProtoSessionStore,
    private val discovery: OAuthDiscovery,
    private val clock: Clock,
    private val nextState: () -> String,
    private val generateKey: () -> P256PrivateJwk,
    private val json: Json,
) {
    constructor(
        httpClient: HttpClient,
        config: OAuthClientConfig,
        authorizationStates: AuthorizationStateStore,
        sessions: AtProtoSessionStore,
    ) : this(
        httpClient = httpClient,
        config = config,
        authorizationStates = authorizationStates,
        sessions = sessions,
        discovery = OAuthDiscovery(httpClient),
        clock = Clock.systemUTC(),
        nextState = { randomUrlToken(24) },
        generateKey = ::generateP256PrivateJwk,
        json = Json { ignoreUnknownKeys = true },
    )

    private val nonces = DpopNonceStore()
    val sessionClient = AtProtoSessionClient(
        httpClient = httpClient,
        sessions = sessions,
        discovery = discovery,
        clock = clock,
        json = json,
    )

    suspend fun beginAuthorization(identifier: String): AuthorizationLaunch {
        val discovered = discovery.resolve(identifier)
        val state = nextState()
        require(state.length >= 16) { "OAuth state must contain at least 16 characters" }
        val pkce = generatePkce()
        val key = generateKey()
        val dpop = DpopHttp(httpClient, DpopProofFactory(key), nonces)
        val response = dpop.request(
            url = discovered.authorizationServer.pushedAuthorizationRequestEndpoint,
            method = HttpMethod.Post,
            server = DpopServer.AUTHORIZATION,
        ) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                formEncode(
                    mapOf(
                        "response_type" to "code",
                        "client_id" to config.clientId,
                        "redirect_uri" to config.redirectUri,
                        "scope" to config.scope,
                        "state" to state,
                        "code_challenge" to pkce.challenge,
                        "code_challenge_method" to "S256",
                        "login_hint" to (discovered.identity.handle ?: discovered.identity.did),
                        "response_mode" to config.responseMode.value,
                    ),
                ),
            )
        }
        val par = response.decodeOAuthResponse<ParResponse>(json, "PAR")
        require(par.requestUri.isNotBlank() && par.expiresIn > 0) { "Authorization server returned an invalid PAR response" }
        val expiresAt = clock.millis() + AUTHORIZATION_LIFETIME_MILLIS

        authorizationStates.put(
            PendingAuthorization(
                state = state,
                clientId = config.clientId,
                redirectUri = config.redirectUri,
                expectedIdentity = discovered.identity,
                authorizationServer = discovered.authorizationServer,
                verifier = pkce.verifier,
                dpopKey = key,
                expiresAt = expiresAt,
            ),
        )

        return AuthorizationLaunch(
            authorizationUrl = appendQuery(
                discovered.authorizationServer.authorizationEndpoint,
                mapOf("client_id" to config.clientId, "request_uri" to par.requestUri),
            ),
            expiresAt = expiresAt,
        )
    }

    suspend fun completeAuthorization(callback: OAuthCallback): AtProtoSession {
        val state = callback.state ?: throw AtProtoOAuthException("OAuth callback is missing state")
        val pending = authorizationStates.take(state)
            ?: throw AtProtoOAuthException("OAuth callback state is unknown or has already been used")
        require(clock.millis() <= pending.expiresAt) { "OAuth callback state has expired" }
        callback.error?.let {
            throw AtProtoOAuthException(callback.errorDescription ?: it)
        }
        val code = callback.code ?: throw AtProtoOAuthException("OAuth callback is missing its authorization code")
        require(callback.issuer == pending.authorizationServer.issuer) { "OAuth callback issuer does not match" }

        val response = tokenRequest(
            server = pending.authorizationServer,
            key = pending.dpopKey,
            values = mapOf(
                "grant_type" to "authorization_code",
                "client_id" to pending.clientId,
                "redirect_uri" to pending.redirectUri,
                "code" to code,
                "code_verifier" to pending.verifier,
            ),
        )
        val tokenResponse = response.decodeOAuthResponse<TokenResponse>(json, "token")

        try {
            val token = tokenResponse.toToken(clock)
            require(tokenResponse.subject == pending.expectedIdentity.did) {
                "Token subject does not match the account that began authorization"
            }
            val verified = discovery.resolve(tokenResponse.subject)
            require(verified.authorizationServer.issuer == pending.authorizationServer.issuer) {
                "Token subject is not controlled by the authorization server"
            }
            val session = AtProtoSession(
                did = tokenResponse.subject,
                clientId = pending.clientId,
                handle = verified.identity.handle ?: pending.expectedIdentity.handle,
                pds = verified.identity.pds,
                authorizationServer = verified.authorizationServer,
                dpopKey = pending.dpopKey,
                token = token,
            )
            sessions.put(session)
            return session
        } catch (cause: Exception) {
            if (cause is CancellationException) throw cause
            bestEffortRevoke(
                pending.authorizationServer,
                pending.dpopKey,
                pending.clientId,
                tokenResponse.accessToken,
            )
            throw cause
        }
    }

    suspend fun getSession(did: String): AtProtoSession {
        return sessionClient.getSession(did)
    }

    suspend fun refreshSession(
        did: String,
        staleAccessToken: String? = null,
    ): AtProtoSession = sessionClient.refreshSession(did, staleAccessToken)

    suspend fun revokeSession(did: String) {
        sessionClient.revokeSession(did)
    }

    private suspend fun tokenRequest(
        server: AuthorizationServerMetadata,
        key: P256PrivateJwk,
        values: Map<String, String>,
    ): BufferedHttpResponse = DpopHttp(httpClient, DpopProofFactory(key), nonces).request(
        url = server.tokenEndpoint,
        method = HttpMethod.Post,
        server = DpopServer.AUTHORIZATION,
    ) {
        contentType(ContentType.Application.FormUrlEncoded)
        setBody(formEncode(values))
    }

    private suspend fun bestEffortRevoke(
        server: AuthorizationServerMetadata,
        key: P256PrivateJwk,
        clientId: String,
        token: String,
    ) {
        val endpoint = server.revocationEndpoint ?: return
        try {
            DpopHttp(httpClient, DpopProofFactory(key), nonces).request(
                url = endpoint,
                method = HttpMethod.Post,
                server = DpopServer.AUTHORIZATION,
            ) {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(formEncode(mapOf("token" to token, "client_id" to clientId)))
            }
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            // Revocation is best effort; local session deletion must still proceed.
        }
    }
}

@Serializable
private data class ParResponse(
    @SerialName("request_uri") val requestUri: String,
    @SerialName("expires_in") val expiresIn: Long,
)

@Serializable
internal data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val scope: String,
    @SerialName("sub") val subject: String,
)

@Serializable
internal data class OAuthErrorResponse(
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)

internal fun TokenResponse.toToken(
    clock: Clock,
    previousRefreshToken: String? = null,
): AtProtoToken {
    require(accessToken.isNotBlank()) { "Token response is missing an access token" }
    require(tokenType == "DPoP") { "Authorization server returned a non-DPoP token" }
    require(scope.split(Regex("\\s+")).contains("atproto")) { "Token response does not grant the atproto scope" }
    require(subject.startsWith("did:")) { "Token response subject is not a DID" }
    require(expiresIn == null || expiresIn > 0) { "Token response expiry is invalid" }

    return AtProtoToken(
        accessToken = accessToken,
        refreshToken = refreshToken ?: previousRefreshToken,
        scope = scope,
        expiresAt = expiresIn?.let { clock.millis() + it * 1_000 },
    )
}

internal inline fun <reified T> BufferedHttpResponse.decodeOAuthResponse(json: Json, label: String): T {
    val contentType = headers["Content-Type"]?.substringBefore(';')?.trim()?.lowercase()
    require(contentType == "application/json") { "OAuth $label response is not JSON" }
    if (!status.isSuccess()) {
        val oauthError = runCatching { json.decodeFromString<OAuthErrorResponse>(body) }.getOrNull()
        throw OAuthResponseException(
            status = status.value,
            error = oauthError?.error,
            message = oauthError?.errorDescription ?: oauthError?.error ?: "OAuth $label request failed with HTTP ${status.value}",
        )
    }
    return json.decodeFromString(body)
}

private fun appendQuery(url: String, values: Map<String, String>): String {
    val separator = if ('?' in url) '&' else '?'
    return url + separator + formEncode(values)
}
