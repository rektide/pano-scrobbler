package com.arn.scrobble.atproto.oauth

import com.arn.scrobble.atproto.crypto.P256PrivateJwk
import com.arn.scrobble.atproto.http.BufferedHttpResponse
import com.arn.scrobble.atproto.http.DpopHttp
import com.arn.scrobble.atproto.http.DpopNonceStore
import com.arn.scrobble.atproto.http.DpopServer
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

private const val TOKEN_REFRESH_MARGIN_MILLIS = 60 * 1_000L

class AtProtoSessionClient internal constructor(
    private val httpClient: HttpClient,
    private val sessions: AtProtoSessionStore,
    private val discovery: OAuthDiscovery,
    private val clock: Clock,
    private val json: Json,
) {
    constructor(
        httpClient: HttpClient,
        sessions: AtProtoSessionStore,
    ) : this(
        httpClient = httpClient,
        sessions = sessions,
        discovery = OAuthDiscovery(httpClient),
        clock = Clock.systemUTC(),
        json = Json { ignoreUnknownKeys = true },
    )

    private val nonces = DpopNonceStore()
    private val refreshLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun getSession(did: String): AtProtoSession {
        val session = sessions.get(did) ?: throw AtProtoOAuthException("AT Protocol session not found")
        if (session.token.isUsable(clock)) return session
        return refreshSession(did)
    }

    suspend fun refreshSession(
        did: String,
        staleAccessToken: String? = null,
    ): AtProtoSession = refreshLocks.computeIfAbsent(did) { Mutex() }.withLock {
        val current = sessions.get(did) ?: throw AtProtoOAuthException("AT Protocol session not found")
        if (staleAccessToken != null && current.token.accessToken != staleAccessToken) return@withLock current
        if (staleAccessToken == null && current.token.isUsable(clock)) return@withLock current
        val refreshToken = current.token.refreshToken
            ?: throw AtProtoOAuthException("AT Protocol session has no refresh token")

        val verified = discovery.resolve(did)
        require(verified.authorizationServer.issuer == current.authorizationServer.issuer) {
            "AT Protocol account is no longer controlled by the session authorization server"
        }
        val response = tokenRequest(
            server = current.authorizationServer,
            key = current.dpopKey,
            values = mapOf(
                "grant_type" to "refresh_token",
                "client_id" to current.clientId,
                "refresh_token" to refreshToken,
            ),
        )
        val tokenResponse = response.decodeOAuthResponse<TokenResponse>(json, "token")
        require(tokenResponse.subject == did) { "Refreshed token subject does not match the session" }
        val refreshed = current.copy(
            handle = verified.identity.handle ?: current.handle,
            pds = verified.identity.pds,
            authorizationServer = verified.authorizationServer,
            token = tokenResponse.toToken(clock, previousRefreshToken = refreshToken),
        )
        sessions.put(refreshed)
        refreshed
    }

    suspend fun revokeSession(did: String) {
        val session = sessions.get(did)
        try {
            if (session != null) {
                bestEffortRevoke(
                    server = session.authorizationServer,
                    key = session.dpopKey,
                    clientId = session.clientId,
                    token = session.token.refreshToken ?: session.token.accessToken,
                )
            }
        } finally {
            sessions.delete(did)
        }
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

private fun AtProtoToken.isUsable(clock: Clock): Boolean =
    expiresAt == null || clock.millis() + TOKEN_REFRESH_MARGIN_MILLIS <= expiresAt
