package com.arn.scrobble.atproto.oauth

import com.arn.scrobble.atproto.crypto.P256PrivateJwk
import com.arn.scrobble.atproto.crypto.generateP256PrivateJwk
import com.arn.scrobble.atproto.http.BufferedHttpResponse
import com.arn.scrobble.atproto.http.AtProtoHttpClientProvider
import com.arn.scrobble.atproto.http.DpopHttp
import com.arn.scrobble.atproto.http.DpopNonceStore
import com.arn.scrobble.atproto.http.DpopNonceException
import com.arn.scrobble.atproto.http.DpopServer
import com.arn.scrobble.atproto.storage.AtProtoAccountStore
import com.arn.scrobble.atproto.storage.SessionHandle
import com.arn.scrobble.atproto.storage.SessionSnapshot
import com.arn.scrobble.atproto.storage.SessionUpdate
import com.arn.scrobble.atproto.storage.StaleSessionException
import io.ktor.client.HttpClient
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * One native lifecycle authority; providers retain only SessionHandle, never a token snapshot.
 * The client supplier must use Pano's dedicated redirect-free transport, not genericKtorClient.
 * Lease it for each operation so proxy invalidation never closes an engine mid-lifecycle.
 */
class AtProtoAccountAuthority internal constructor(
    private val store: AtProtoAccountStore,
    private val config: OAuthClientConfig,
    private val httpClients: AtProtoHttpClientProvider,
    private val clock: EpochClock,
) {
    constructor(store: AtProtoAccountStore, config: OAuthClientConfig, httpClients: AtProtoHttpClientProvider) :
        this(store, config, httpClients, EpochClock.System)

    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json { ignoreUnknownKeys = true; exceptionsWithDebugInfo = false }

    suspend fun beginAuthorization(identifier: String): AuthorizationLaunch = withClient { client ->
        val attempt = store.reserveAuthorization(config.clientId)
        val discovered = withTimeout(REQUEST_TIMEOUT_MILLIS) { OAuthDiscovery(client).resolve(identifier) }
        val state = randomUrlToken(32)
        val pkce = generatePkce()
        val key = generateP256PrivateJwk()
        val nonces = DpopNonceStore()
        val response = formRequest(client, discovered.authorizationServer.pushedAuthorizationRequestEndpoint, key, nonces,
            mapOf("response_type" to "code", "client_id" to config.clientId, "redirect_uri" to config.redirectUri,
                "scope" to config.scope, "state" to state, "code_challenge" to pkce.challenge,
                "code_challenge_method" to "S256", "login_hint" to (discovered.identity.handle ?: discovered.identity.did)))
        val par = response.decode<ParResponse>()
        require(response.status.value == 201 && par.requestUri.isNotBlank() && par.expiresIn > 0) { "Invalid PAR response" }
        val now = clock.millis()
        val expiresAt = Math.addExact(now, minOf(par.expiresIn, 600) * 1000)
        val pending = PendingAuthorization(config.clientId, config.redirectUri, discovered.identity,
            discovered.authorizationServer, pkce.verifier, key, nonces.snapshot())
        store.publishAuthorization(attempt, discovered.identity.did, state, expiresAt, encode(pending))
        val endpoint = discovered.authorizationServer.authorizationEndpoint
        AuthorizationLaunch(endpoint + (if ('?' in endpoint) '&' else '?') + formEncode(
            mapOf("client_id" to config.clientId, "request_uri" to par.requestUri)), expiresAt)
    }

    /** Android deep links and desktop paste must enter this exact same URL validator. */
    suspend fun completeAuthorization(callbackUrl: String): AtProtoAccount {
        val callback = NativeCallback.parse(callbackUrl, config.redirectUri)
        val claim = store.consumeAuthorization(config.clientId, callback.state, clock.millis())
            ?: throw AtProtoOAuthException("Authorization state is expired, unknown or already used")
        val pending = json.decodeFromJsonElement<PendingAuthorization>(claim.authorization)
        require(pending.clientId == config.clientId && pending.redirectUri == config.redirectUri &&
            pending.expectedIdentity.did == claim.did) { "Stored authorization identity does not match" }
        require(callback.issuer == pending.authorizationServer.issuer) { "OAuth callback issuer does not match" }
        if (callback.error != null) throw AuthorizationDeniedException()
        return withClient { client ->
        val nonces = DpopNonceStore(pending.nonces)
        val response = formRequest(client, pending.authorizationServer.tokenEndpoint, pending.dpopKey, nonces,
            mapOf("grant_type" to "authorization_code", "client_id" to pending.clientId, "redirect_uri" to pending.redirectUri,
                "code" to requireNotNull(callback.code), "code_verifier" to pending.verifier))
        val token = response.decode<TokenResponse>().token(clock.millis(), claim.did)
        // Re-resolve the original handle, when present, so a changed handle cannot bind another DID.
        val verified = withTimeout(REQUEST_TIMEOUT_MILLIS) {
            OAuthDiscovery(client).resolve(pending.expectedIdentity.handle ?: claim.did)
        }
        require(verified.identity.did == claim.did && verified.identity.pds == pending.expectedIdentity.pds &&
            verified.authorizationServer.issuer == pending.authorizationServer.issuer) { "Authorized identity or server binding changed" }
        val session = StoredSession(config.clientId, claim.did, verified.identity.handle, verified.identity.pds,
            verified.authorizationServer, pending.dpopKey, token, nonces.snapshot())
        account(store.completeAuthorization(claim, encode(session)))
        }
    }

    /** Read-only secret-free view; never use it as credentials for a subsequent request. */
    suspend fun currentAccount(): AtProtoAccount? = store.currentSession(config.clientId)?.let(::account)

    suspend fun refresh(handle: SessionHandle): AtProtoAccount = store.withSession(handle) {
        requireHandle(handle)
        withClient { client -> refreshIfNeeded(handle, client, force = false) }
        account(requireNotNull(snapshot()))
    }

    /** Narrow completed-record operation, not a general credential-bearing fetch API. */
    suspend fun createRecord(handle: SessionHandle, collection: String, record: JsonObject): JsonObject = store.withSession(handle) {
        requireHandle(handle)
        withClient { client ->
        refreshIfNeeded(handle, client, force = false)
        var current = decodeSession(credentials, handle)
        require("repo:$collection?action=create" in current.token.scope.split(' ')) { "Record collection was not granted" }
        require(collection == "fm.teal.feed.play" || collection == "app.rocksky.scrobble") { "Unsupported completed-play collection" }
        val nonces = DpopNonceStore(current.nonces) { values ->
            current = current.copy(nonces = values)
            replace(encode(current))
        }
        val body = json.encodeToString(CreateRecordRequest(handle.did, collection, record))
        val response = withTimeout(REQUEST_TIMEOUT_MILLIS) {
            DpopHttp(client, DpopProofFactory(current.dpopKey, clock), nonces).request(
                current.pds + "/xrpc/com.atproto.repo.createRecord", HttpMethod.Post, DpopServer.RESOURCE, current.token.accessToken,
            ) { contentType(ContentType.Application.Json); setBody(body) }
        }
        // No blind replay on invalid_token: refresh/retry belongs to a proved non-consuming boundary.
        // This operation returns errors to the pending-play caller; ordinary failure never marks rotation.
        response.decode<JsonObject>()
        }
    }

    /** Durable local removal wins even if the optional remote revocation fails. */
    suspend fun logout() {
        val removed = store.logout(config.clientId) ?: return
        val session = json.decodeFromJsonElement<StoredSession>(removed.credentials)
        val endpoint = session.authorizationServer.revocationEndpoint ?: return
        try {
            withClient { client -> formRequest(client, endpoint, session.dpopKey, DpopNonceStore(session.nonces),
                mapOf("client_id" to session.clientId, "token" to (session.token.refreshToken ?: session.token.accessToken),
                    "token_type_hint" to if (session.token.refreshToken != null) "refresh_token" else "access_token")) }
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Exception) {
            // Local removal is already durable; revocation is explicitly best effort.
        }
    }

    private suspend fun AtProtoAccountStore.SessionOperation.refreshIfNeeded(handle: SessionHandle, client: HttpClient, force: Boolean) {
        val current = decodeSession(credentials, handle)
        if (!force && current.token.expiresAt - clock.millis() > 60_000) return
        val refreshToken = current.token.refreshToken ?: throw AtProtoOAuthException("Authorization has no refresh token; sign in again")
        // Discovery is not token-consuming: failure must not quarantine a valid rotating credential.
        val verified = withTimeout(REQUEST_TIMEOUT_MILLIS) { OAuthDiscovery(client).resolve(handle.did) }
        require(verified.identity.did == handle.did && verified.authorizationServer.issuer == current.authorizationServer.issuer) {
            "Account is no longer controlled by the session authorization server"
        }
        var working = current.copy(handle = verified.identity.handle, pds = verified.identity.pds, authorizationServer = verified.authorizationServer)
        var failure: Exception? = null
        rotate {
            val nonces = DpopNonceStore(working.nonces) { working = working.copy(nonces = it) }
            val response = try {
                formRequest(client, working.authorizationServer.tokenEndpoint, working.dpopKey, nonces,
                    mapOf("grant_type" to "refresh_token", "client_id" to config.clientId, "refresh_token" to refreshToken))
            } catch (cause: DpopNonceException) {
                // Reject the non-conforming response without treating an HTTP error as a successful rotation.
                if (cause.status !in 400..599) throw cause
                failure = cause
                return@rotate SessionUpdate.Replace(encode(working))
            }
            if (response.status.value !in 200..299) {
                val error = response.oauthError()
                failure = error
                when {
                    error.status == 400 && error.error == "invalid_grant" -> SessionUpdate.Remove
                    error.status == 429 || error.status in 500..599 || (error.status in 400..499 && error.error != null) ->
                        SessionUpdate.Replace(encode(working))
                    else -> throw error // Unclassified/malformed response: consumption cannot be ruled out.
                }
            } else {
                val token = response.decode<TokenResponse>().token(clock.millis(), handle.did, refreshToken)
                SessionUpdate.Replace(encode(working.copy(token = token)))
            }
        }
        // The classified durable outcome is acknowledged before exposing its original protocol error.
        failure?.let { throw it }
    }

    private suspend fun formRequest(client: HttpClient, endpoint: String, key: P256PrivateJwk, nonces: DpopNonceStore, values: Map<String, String>): BufferedHttpResponse =
        withTimeout(REQUEST_TIMEOUT_MILLIS) {
            DpopHttp(client, DpopProofFactory(key, clock), nonces).request(endpoint, HttpMethod.Post, DpopServer.AUTHORIZATION) {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(formEncode(values))
            }
        }

    private fun requireHandle(handle: SessionHandle) {
        if (handle.clientId != config.clientId) throw StaleSessionException()
    }

    private suspend fun <T> withClient(action: suspend (HttpClient) -> T): T = httpClients.acquire().use { action(it.client) }

    private fun decodeSession(payload: JsonObject, handle: SessionHandle): StoredSession = json.decodeFromJsonElement<StoredSession>(payload).also {
        require(it.clientId == handle.clientId && it.did == handle.did) { "Stored session identity does not match its handle" }
    }

    private fun account(snapshot: SessionSnapshot): AtProtoAccount {
        val session = decodeSession(snapshot.credentials, snapshot.handle)
        return AtProtoAccount(snapshot.handle, session.handle, session.pds, session.token.scope.split(' ').toSet())
    }

    private inline fun <reified T> encode(value: T): JsonObject = json.encodeToJsonElement(value).jsonObject

    private inline fun <reified T> BufferedHttpResponse.decode(): T {
        if (status.value !in 200..299) throw oauthError()
        require(headers["Content-Type"]?.substringBefore(';')?.trim()?.lowercase() == "application/json") { "OAuth response is not JSON" }
        return json.decodeFromString(body)
    }

    private fun BufferedHttpResponse.oauthError(): OAuthResponseException {
        val error = runCatching { json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
        return OAuthResponseException(status.value, error)
    }
}

private const val REQUEST_TIMEOUT_MILLIS = 40_000L

@Serializable
private data class ParResponse(@SerialName("request_uri") val requestUri: String, @SerialName("expires_in") val expiresIn: Long)

@Serializable
private data class CreateRecordRequest(val repo: String, val collection: String, val record: JsonObject)
