package com.arn.scrobble

import androidx.datastore.core.DataStoreFactory
import com.arn.scrobble.api.atproto.ATPROTO_CLIENT_ID
import com.arn.scrobble.api.atproto.AtprotoAppPasswordSession
import com.arn.scrobble.api.atproto.AtprotoOAuthDpop
import com.arn.scrobble.api.atproto.AtprotoOAuthPermission
import com.arn.scrobble.api.atproto.AtprotoOAuthSession
import com.arn.scrobble.api.atproto.AtprotoSessionSerializer
import com.arn.scrobble.api.atproto.AtprotoSessionStorageException
import com.arn.scrobble.api.atproto.AtprotoSessionStore
import com.arn.scrobble.api.atproto.atprotoSessionStore
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.File
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class AtprotoSessionStoreTest {
    @Test
    fun completedLoginsReopenWithOriginalProviderAndSigningKey() = withStoreFile { file ->
        val key = ECKeyGenerator(Curve.P_256).generate()
        val oauth = oauthSession(key)
        val appPassword = AtprotoAppPasswordSession(OTHER_DID, PDS, "synthetic-app-access", "synthetic-app-refresh")
        file.open { store ->
            assertNull(store.restore(DID))
            store.save(oauth)
            store.save(appPassword)
        }
        // No credential, private scalar, or account/provider metadata is stored as clear text.
        val disk = file.path.readBytes().decodeToString()
        for (value in listOf(DID, OTHER_DID, PDS, ISSUER, ATPROTO_CLIENT_ID, key.d.toString(),
            oauth.accessToken, requireNotNull(oauth.refreshToken), appPassword.accessJwt, appPassword.refreshJwt))
            assertFalse(value in disk)

        file.open { store ->
            val restoredApp = assertIs<AtprotoAppPasswordSession>(store.restore(OTHER_DID))
            assertEquals(appPassword.did, restoredApp.did)
            assertEquals(appPassword.pds, restoredApp.pds)
            assertEquals(appPassword.accessJwt, restoredApp.accessJwt)
            assertEquals(appPassword.refreshJwt, restoredApp.refreshJwt)

            val restored = assertIs<AtprotoOAuthSession>(store.restore(DID))
            assertEquals(oauth.did, restored.did)
            assertEquals(oauth.pds, restored.pds)
            assertEquals(oauth.issuer, restored.issuer)
            assertEquals(oauth.clientId, restored.clientId)
            assertEquals(oauth.scopes, restored.scopes)
            assertEquals(oauth.expiresAt, restored.expiresAt)
            assertEquals(oauth.accessToken, restored.accessToken)
            assertEquals(oauth.refreshToken, restored.refreshToken)

            val url = "$PDS/xrpc/com.atproto.repo.createRecord".toHttpUrl()
            val proof = SignedJWT.parse(restored.dpop.proof("POST", url, restored.accessToken))
            val advertisedKey = assertIs<ECKey>(proof.header.jwk)
            assertEquals(key.computeThumbprint(), advertisedKey.computeThumbprint())
            assertFalse(advertisedKey.isPrivate)
            assertTrue(proof.verify(ECDSAVerifier(key.toPublicJWK())))
            assertEquals(url.toString(), proof.jwtClaimsSet.getStringClaim("htu"))
            val hash = MessageDigest.getInstance("SHA-256").digest(oauth.accessToken.encodeToByteArray())
            assertEquals(Base64URL.encode(hash).toString(), proof.jwtClaimsSet.getStringClaim("ath"))
        }
    }

    @Test
    fun replacementAndDisconnectAreDurableAndAccountScoped() = withStoreFile { file ->
        val old = AtprotoAppPasswordSession(DID, PDS, "synthetic-old-access", "synthetic-old-refresh")
        val unrelated = AtprotoAppPasswordSession(OTHER_DID, PDS, "synthetic-other-access", "synthetic-other-refresh")
        file.open { store ->
            store.save(old)
            store.save(unrelated)
        }
        val replacement = oauthSession(ECKeyGenerator(Curve.P_256).generate(), refreshToken = null)
        file.rejectWrites = true
        file.open { store ->
            assertStorageFailure("save") { store.save(replacement) }
        }
        file.rejectWrites = false
        file.open { store ->
            assertEquals(old.accessJwt, assertIs<AtprotoAppPasswordSession>(store.restore(DID)).accessJwt)
            store.save(replacement)
        }
        file.open { store ->
            val restored = assertIs<AtprotoOAuthSession>(store.restore(DID))
            assertEquals(replacement.accessToken, restored.accessToken)
            assertNull(restored.refreshToken)
            store.disconnect(DID)
        }
        file.open { store ->
            assertNull(store.restore(DID))
            assertEquals(unrelated.accessJwt, assertIs<AtprotoAppPasswordSession>(store.restore(OTHER_DID)).accessJwt)
            store.disconnect(OTHER_DID)
            store.disconnect(DID)
        }
        file.open { store ->
            assertNull(store.restore(OTHER_DID))
        }
    }

    @Test
    fun foreignClientIsNotSavedOverExistingLogin() = withStoreFile { file ->
        val valid = oauthSession(ECKeyGenerator(Curve.P_256).generate())
        file.open { it.save(valid) }
        file.open { store ->
            val foreign = oauthSession(ECKeyGenerator(Curve.P_256).generate(), clientId = "https://foreign.example/client.json")
            assertStorageFailure("save") { store.save(foreign) }
        }
        file.open { store ->
            assertEquals(valid.accessToken, assertIs<AtprotoOAuthSession>(store.restore(DID)).accessToken)
        }
    }

    @Test
    fun missingKeyAndTamperingFailClosed() = withStoreFile { file ->
        file.open { it.save(oauthSession(ECKeyGenerator(Curve.P_256).generate())) }
        val original = file.path.readBytes()
        file.rejectReads = true
        file.open { store -> assertStorageFailure("restore") { store.restore(DID) } }
        assertTrue(original.contentEquals(file.path.readBytes()))
        file.rejectReads = false
        val tampered = original.copyOf().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
        file.path.writeBytes(tampered)
        file.open { store -> assertStorageFailure("restore") { store.restore(DID) } }
        assertTrue(tampered.contentEquals(file.path.readBytes()))
    }

    @Test
    fun desktopDoesNotFallBackToUnprotectedStorage() {
        val error = assertFailsWith<AtprotoSessionStorageException> { atprotoSessionStore }
        assertEquals("Protected AT Protocol session storage is unavailable on desktop", error.message)
    }

    private fun oauthSession(
        key: ECKey,
        refreshToken: String? = "synthetic-oauth-refresh",
        clientId: String = ATPROTO_CLIENT_ID,
    ) = AtprotoOAuthSession(
        DID, PDS, ISSUER,
        setOf("atproto", AtprotoOAuthPermission.TEAL_SCROBBLES.scope, AtprotoOAuthPermission.ROCKSKY_LIKES.scope),
        Instant.parse("2026-01-01T12:00:00Z"), "synthetic-oauth-access", refreshToken,
        AtprotoOAuthDpop(key), clientId,
    )

    private inline fun assertStorageFailure(operation: String, block: () -> Unit) {
        val error = assertFailsWith<AtprotoSessionStorageException>(block = block)
        assertEquals("Could not $operation the local AT Protocol session", error.message)
        assertNull(error.cause)
        assertTrue(error.suppressedExceptions.isEmpty())
    }

    private fun withStoreFile(block: suspend (StoreFile) -> Unit) = runBlocking {
        val directory = createTempDirectory("atproto-session-test-").toFile()
        try {
            block(StoreFile(directory.resolve("sessions.v1")))
        } finally {
            check(directory.deleteRecursively())
        }
    }

    // A fixture key, not a desktop protection backend. Each open creates a fresh DataStore and cache.
    private class StoreFile(val path: File) {
        private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        var rejectReads = false
        var rejectWrites = false

        suspend fun open(block: suspend (AtprotoSessionStore) -> Unit) {
            val job = Job()
            val serializer = AtprotoSessionSerializer { create ->
                check(if (create) !rejectWrites else !rejectReads) { "synthetic-credential-in-error" }
                key
            }
            val store = DataStoreFactory.create(serializer, scope = CoroutineScope(Dispatchers.IO + job)) { path }
            try {
                block(AtprotoSessionStore(store))
            } finally {
                job.cancelAndJoin()
            }
        }
    }

    private companion object {
        const val DID = "did:web:one.example"
        const val OTHER_DID = "did:web:two.example"
        const val PDS = "https://pds.example"
        const val ISSUER = "https://issuer.example"
    }
}
