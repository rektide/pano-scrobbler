package com.arn.scrobble.api.atproto

import co.touchlab.kermit.Logger
import com.arn.scrobble.api.Requesters
import io.github.kikin81.atproto.oauth.AtOAuth
import io.github.kikin81.atproto.oauth.OAuthSession
import io.github.kikin81.atproto.oauth.OAuthSessionStore
import io.github.kikin81.atproto.runtime.XrpcClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide owner of the staged fork OAuth lifecycle and authenticated
 * client. No production path uses it unless the field-test selector is enabled.
 */
internal object PanoUpstreamAtProtoAccountCoordinator {
    private val sessionStore by lazy { PanoUpstreamOAuthSessionStore() }
    private val coordinator by lazy {
        UpstreamAtProtoAccountCoordinator(
            oauth = ForkOAuthLifecycle(
                AtOAuth(
                    clientMetadataUrl = PanoAtProtoConfig.clientMetadataUrl,
                    redirectUri = PanoAtProtoConfig.redirectUri,
                    sessionStore = sessionStore,
                    httpClient = Requesters.genericKtorClient,
                    scope = PanoAtProtoConfig.scope,
                    onSessionPersistFailure = { cause ->
                        Logger.e(cause) { "Could not persist a rotated upstream AT Protocol session" }
                    },
                    pendingStore = PanoUpstreamPendingAuthStore(),
                ),
            ),
            sessionStore = sessionStore,
        )
    }

    suspend fun beginLogin(handleOrDid: String): String = coordinator.beginLogin(handleOrDid)

    suspend fun completeLogin(callbackUrl: String): OAuthSession = coordinator.completeLogin(callbackUrl)

    suspend fun currentSession(): OAuthSession? = coordinator.currentSession()

    suspend fun getOrCreateClient(): XrpcClient = coordinator.getOrCreateClient()

    suspend fun logout() = coordinator.logout()

    suspend fun invalidate() = coordinator.invalidate()
}

/** Mutex-serialized, single-client lifecycle with an injectable OAuth seam. */
internal class UpstreamAtProtoAccountCoordinator(
    private val oauth: UpstreamOAuthLifecycle,
    private val sessionStore: OAuthSessionStore,
) {
    private val clientMutex = Mutex()
    private var cachedClient: CachedClient? = null

    suspend fun beginLogin(handleOrDid: String): String = oauth.beginLogin(handleOrDid)

    suspend fun completeLogin(callbackUrl: String): OAuthSession = clientMutex.withLock {
        cachedClient = null
        oauth.completeLogin(callbackUrl)
        sessionStore.load()
            ?: error("Upstream AT Protocol login completed without a persisted session")
    }

    suspend fun currentSession(): OAuthSession? = clientMutex.withLock { sessionStore.load() }

    suspend fun getOrCreateClient(): XrpcClient = clientMutex.withLock {
        val session = sessionStore.load()
            ?: error("No upstream AT Protocol session; complete a fresh fork-backed login")
        val did = session.did
            ?: error("Upstream AT Protocol session has no account DID; signup sessions are unsupported")
        cachedClient?.takeIf { it.did == did }?.client
            ?: oauth.createClient().also { cachedClient = CachedClient(did, it) }
    }

    suspend fun logout() = clientMutex.withLock {
        try {
            oauth.logout()
        } finally {
            cachedClient = null
        }
    }

    suspend fun invalidate() = clientMutex.withLock {
        cachedClient = null
    }

    private data class CachedClient(
        val did: String,
        val client: XrpcClient,
    )
}

internal interface UpstreamOAuthLifecycle {
    suspend fun beginLogin(handleOrDid: String): String

    suspend fun completeLogin(callbackUrl: String)

    suspend fun createClient(): XrpcClient

    suspend fun logout()
}

private class ForkOAuthLifecycle(
    private val oauth: AtOAuth,
) : UpstreamOAuthLifecycle {
    override suspend fun beginLogin(handleOrDid: String): String = oauth.beginLogin(handleOrDid)

    override suspend fun completeLogin(callbackUrl: String) = oauth.completeLogin(callbackUrl)

    override suspend fun createClient(): XrpcClient = oauth.createClient()

    override suspend fun logout() = oauth.logout()
}
