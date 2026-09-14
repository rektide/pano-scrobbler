package com.arn.scrobble.api.atproto

import androidx.datastore.core.DataStore
import com.arn.scrobble.pref.MainPrefs
import io.github.kikin81.atproto.oauth.OAuthSession
import io.github.kikin81.atproto.oauth.PendingAuth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PanoUpstreamAtProtoStoresTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Test
    fun `old MainPrefs JSON defaults both upstream stores to empty`() {
        val restored = json.decodeFromString<MainPrefs>("{}")

        assertNull(restored.atProtoUpstreamPendingAuth)
        assertNull(restored.atProtoUpstreamSession)
    }

    @Test
    fun `MainPrefs round trip preserves fork pending and session models`() {
        val pending = pendingAuth()
        val session = oauthSession()
        val encoded = json.encodeToString(
            MainPrefs(
                atProtoUpstreamPendingAuth = pending,
                atProtoUpstreamSession = session,
            ),
        )

        val restored = json.decodeFromString<MainPrefs>(encoded)

        assertEquals(pending, restored.atProtoUpstreamPendingAuth)
        assertEquals(session.scope, restored.atProtoUpstreamSession?.scope)
        assertContentEquals(session.dpopPrivateKey, restored.atProtoUpstreamSession?.dpopPrivateKey)
        assertContentEquals(session.dpopPublicKey, restored.atProtoUpstreamSession?.dpopPublicKey)
    }

    @Test
    fun `separate adapters update and clear only their own durable field`() = runTest {
        val dataStore = TestDataStore(MainPrefs())
        val pendingStore = PanoUpstreamPendingAuthStore(dataStore)
        val sessionStore = PanoUpstreamOAuthSessionStore(dataStore)
        val pending = pendingAuth()
        val session = oauthSession()

        pendingStore.save(pending)
        sessionStore.save(session)

        assertEquals(pending, pendingStore.load())
        assertEquals(session.scope, sessionStore.load()?.scope)
        assertEquals(pending, dataStore.data.first().atProtoUpstreamPendingAuth)

        pendingStore.clear()
        assertNull(pendingStore.load())
        assertEquals(session.scope, sessionStore.load()?.scope)

        sessionStore.clear()
        assertNull(sessionStore.load())
    }

    private fun pendingAuth(): PendingAuth = PendingAuth(
        state = "state",
        codeVerifier = "verifier",
        redirectUri = "pano-scrobbler:/oauth/callback",
        flowOrigin = "Login",
        authServerNonce = "as-nonce",
        dpopPrivateKey = byteArrayOf(1, 2, 3),
        dpopPublicKey = byteArrayOf(4, 5, 6),
        issuer = "https://auth.test",
        authorizationEndpoint = "https://auth.test/authorize",
        tokenEndpoint = "https://auth.test/token",
        parEndpoint = "https://auth.test/par",
        pdsUrl = "https://pds.test",
        did = "did:plc:test",
        handle = "test.example",
        createdAtEpochMillis = 123,
    )

    private fun oauthSession(): OAuthSession = OAuthSession(
        accessToken = "access",
        refreshToken = "refresh",
        did = "did:plc:test",
        handle = "test.example",
        pdsUrl = "https://pds.test",
        tokenEndpoint = "https://auth.test/token",
        clientId = "https://app.test/client-metadata.json",
        dpopPrivateKey = byteArrayOf(7, 8, 9),
        dpopPublicKey = byteArrayOf(10, 11, 12),
        authServerNonce = "as-nonce",
        pdsNonce = "pds-nonce",
        scope = "atproto repo:fm.teal.feed.play?action=create",
    )
}

private class TestDataStore<T>(initial: T) : DataStore<T> {
    private val state = MutableStateFlow(initial)

    override val data: Flow<T> = state

    override suspend fun updateData(transform: suspend (t: T) -> T): T {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}
