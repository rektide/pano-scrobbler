package com.arn.scrobble.api.atproto

import io.github.kikin81.atproto.oauth.OAuthSession
import io.github.kikin81.atproto.oauth.OAuthSessionStore
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class PanoUpstreamAtProtoAccountCoordinatorTest {
    @Test
    fun `delegates existing-account login without exposing signup`() = runTest {
        val store = TestOAuthSessionStore()
        val oauth = TestOAuthLifecycle(store)
        val coordinator = UpstreamAtProtoAccountCoordinator(oauth, store)

        val authorizationUrl = coordinator.beginLogin("alice.example")

        assertEquals("alice.example", oauth.loginHint)
        assertEquals("https://auth.test/authorize", authorizationUrl)
    }

    @Test
    fun `complete callback returns the persisted session and invalidates old client`() = runTest {
        val oldSession = session("did:plc:old")
        val newSession = session("did:plc:new")
        val store = TestOAuthSessionStore(oldSession)
        val oauth = TestOAuthLifecycle(store, completedSession = newSession)
        val coordinator = UpstreamAtProtoAccountCoordinator(oauth, store)
        val oldClient = coordinator.getOrCreateClient()

        val completed = coordinator.completeLogin("pano-scrobbler:/oauth/callback?code=x")
        val newClient = coordinator.getOrCreateClient()

        assertEquals(newSession.did, completed.did)
        assertEquals(newSession.did, coordinator.currentSession()?.did)
        assertEquals("pano-scrobbler:/oauth/callback?code=x", oauth.callbackUrl)
        assertEquals(2, oauth.createClientCalls)
        kotlin.test.assertNotSame(oldClient, newClient)
    }

    @Test
    fun `concurrent callers share exactly one authenticated client`() = runTest {
        val store = TestOAuthSessionStore(session("did:plc:test"))
        val oauth = TestOAuthLifecycle(store, suspendClientCreation = true)
        val coordinator = UpstreamAtProtoAccountCoordinator(oauth, store)

        val clients = coroutineScope {
            List(12) { async { coordinator.getOrCreateClient() } }.awaitAll()
        }

        assertEquals(1, oauth.createClientCalls)
        clients.forEach { assertSame(clients.first(), it) }
    }

    @Test
    fun `invalidate drops only the cached client`() = runTest {
        val stored = session("did:plc:test")
        val store = TestOAuthSessionStore(stored)
        val oauth = TestOAuthLifecycle(store)
        val coordinator = UpstreamAtProtoAccountCoordinator(oauth, store)
        coordinator.getOrCreateClient()

        coordinator.invalidate()
        coordinator.getOrCreateClient()

        assertEquals(2, oauth.createClientCalls)
        assertEquals(stored.did, store.load()?.did)
    }

    @Test
    fun `logout clears session and cached client`() = runTest {
        val store = TestOAuthSessionStore(session("did:plc:test"))
        val oauth = TestOAuthLifecycle(store)
        val coordinator = UpstreamAtProtoAccountCoordinator(oauth, store)
        coordinator.getOrCreateClient()

        coordinator.logout()

        assertEquals(1, oauth.logoutCalls)
        assertNull(coordinator.currentSession())
        assertFailsWith<IllegalStateException> { coordinator.getOrCreateClient() }
    }

    private fun session(did: String): OAuthSession = OAuthSession(
        accessToken = "access-$did",
        refreshToken = "refresh-$did",
        did = did,
        handle = "test.example",
        pdsUrl = "https://pds.test",
        tokenEndpoint = "https://auth.test/token",
        dpopPrivateKey = byteArrayOf(1),
        dpopPublicKey = byteArrayOf(2),
        scope = "atproto",
    )
}

private class TestOAuthLifecycle(
    private val store: OAuthSessionStore,
    private val completedSession: OAuthSession? = null,
    private val suspendClientCreation: Boolean = false,
) : UpstreamOAuthLifecycle {
    var loginHint: String? = null
    var callbackUrl: String? = null
    var createClientCalls = 0
    var logoutCalls = 0

    override suspend fun beginLogin(handleOrDid: String): String {
        loginHint = handleOrDid
        return "https://auth.test/authorize"
    }

    override suspend fun completeLogin(callbackUrl: String) {
        this.callbackUrl = callbackUrl
        completedSession?.let { store.save(it) }
    }

    override suspend fun createClient(): XrpcClient {
        createClientCalls++
        if (suspendClientCreation) yield()
        return XrpcClient(
            baseUrl = "https://pds.test",
            httpClient = HttpClient(MockEngine { respondOk() }),
        )
    }

    override suspend fun logout() {
        logoutCalls++
        store.clear()
    }
}

private class TestOAuthSessionStore(
    private var session: OAuthSession? = null,
) : OAuthSessionStore {
    override suspend fun load(): OAuthSession? = session

    override suspend fun save(session: OAuthSession) {
        this.session = session
    }

    override suspend fun clear() {
        session = null
    }
}
