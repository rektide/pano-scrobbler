package com.arn.scrobble.atproto.storage

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val CLIENT = "https://example.app/oauth-client-metadata.json"
private const val DID = "did:plc:test-account"

internal fun credentials(token: String) = buildJsonObject { put("token", token) }

private fun temporaryDirectory(): File {
    val parent = File(System.getProperty("pano.test.directory")).also { it.mkdirs() }
    return Files.createTempDirectory(parent.toPath(), "case-").toFile()
}

private fun store(root: File, cipher: VaultCipher = VaultCipher.Plaintext) =
    AtProtoAccountStore(JvmVaultPersistence(root), cipher)

private suspend fun authorize(
    store: AtProtoAccountStore,
    client: String = CLIENT,
    did: String = DID,
    payload: JsonObject = credentials("original"),
): SessionSnapshot {
    store.beginAuthorization(client, did, "state", Long.MAX_VALUE, credentials("verifier"))
    return store.completeAuthorization(assertNotNull(store.consumeAuthorization(client, "state", 1)), payload)
}

class AtProtoAccountStoreTest {
    @Test fun namespacesAndSingleUseSurviveRecreation(): Unit = runBlocking {
        val root = File(temporaryDirectory(), "vault")
        val first = store(root)
        first.beginAuthorization(CLIENT, DID, "state", 100, credentials("pkce-secret"))
        val second = store(root)
        assertNull(second.consumeAuthorization("https://other.app", "state", 1))
        assertNull(second.consumeAuthorization(CLIENT, "wrong-state", 1))
        val results = listOf(
            async { first.consumeAuthorization(CLIENT, "state", 1) },
            async { second.consumeAuthorization(CLIENT, "state", 1) },
        ).map { it.await() }
        val claim = results.filterNotNull().single()
        assertNull(store(root).consumeAuthorization(CLIENT, "state", 1))
        val session = store(root).completeAuthorization(claim, credentials("access"))
        assertEquals(DID, session.handle.did)
        assertEquals(session, first.currentSession(CLIENT))
        assertNull(first.currentSession("https://other.app"))
        assertFailsWith<StaleAuthorizationException> { first.completeAuthorization(claim, credentials("replayed")) }
    }

    @Test fun expiredStateIsConsumedButUnknownStateDoesNotCancelLogin(): Unit = runBlocking {
        val root = File(temporaryDirectory(), "vault")
        val vault = store(root)
        vault.beginAuthorization(CLIENT, DID, "state", 100, credentials("secret"))
        assertNull(vault.consumeAuthorization(CLIENT, "unknown", 1))
        assertNull(vault.consumeAuthorization(CLIENT, "state", 100))
        assertNull(vault.consumeAuthorization(CLIENT, "state", 1))
    }

    @Test fun logoutOnlyDeletesTheSelectedClientNamespace(): Unit = runBlocking {
        val root = File(temporaryDirectory(), "vault")
        val vault = store(root)
        authorize(vault)
        val other = authorize(vault, client = "https://other.app/metadata.json", did = "did:plc:other")
        vault.logout(CLIENT)
        assertNull(vault.currentSession(CLIENT))
        assertEquals(other, store(root).currentSession(other.handle.clientId))
    }

    @Test fun logoutAndNewLoginInvalidatePreviouslyConsumedClaimsAndSessionHandles(): Unit = runBlocking {
        val root = File(temporaryDirectory(), "vault")
        val first = store(root)
        val old = authorize(first)
        first.beginAuthorization(CLIENT, DID, "pending", 100, credentials("secret"))
        val claim = assertNotNull(first.consumeAuthorization(CLIENT, "pending", 1))
        store(root).logout(CLIENT)
        assertFailsWith<StaleAuthorizationException> { first.completeAuthorization(claim, credentials("stale")) }
        val replacement = authorize(store(root), did = "did:plc:replacement")
        var called = false
        assertFailsWith<StaleSessionException> {
            first.updateSession(old.handle) { called = true; SessionUpdate.Replace(credentials("stale")) }
        }
        assertFalse(called)
        assertEquals(replacement, first.currentSession(CLIENT))
        first.beginAuthorization(CLIENT, DID, "a", 100, credentials("a"))
        val a = assertNotNull(first.consumeAuthorization(CLIENT, "a", 1))
        first.beginAuthorization(CLIENT, DID, "b", 100, credentials("b"))
        assertFailsWith<StaleAuthorizationException> { first.completeAuthorization(a, credentials("stale")) }
    }

    @Test fun independentInstancesAlwaysReadLatestDiskState(): Unit = runBlocking {
        val root = File(temporaryDirectory(), "vault")
        val first = store(root)
        val second = store(root)
        val initial = authorize(first)
        second.updateSession(initial.handle) { SessionUpdate.Replace(credentials("rotated")) }
        assertEquals(credentials("rotated"), first.currentSession(CLIENT)?.credentials)
        first.updateSession(initial.handle) { latest ->
            assertEquals(credentials("rotated"), latest)
            SessionUpdate.Keep
        }
        assertNotEquals(initial.handle, authorize(second).handle)
    }

    @Test fun cancellationDuringLockWaitDoesNotEnterOrMarkAnUpdate(): Unit = runBlocking {
        val root = File(temporaryDirectory(), "vault")
        val vault = store(root)
        val initial = authorize(vault)
        val held = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val holder = launch {
            ExclusiveFileLock(JvmVaultPersistence(root).lockFile).withLock {
                held.complete(Unit)
                release.await()
            }
        }
        held.await()
        var entered = false
        val waiter = launch {
            store(root).updateSession(initial.handle) { entered = true; SessionUpdate.Keep }
        }
        delay(100)
        waiter.cancelAndJoin()
        release.complete(Unit)
        holder.join()
        assertFalse(entered)
        assertEquals(initial, vault.currentSession(CLIENT))
    }

    @Test fun cancellationAfterActionStartsFinishesDurableReplacementBeforeUnlock(): Unit = runBlocking {
        val root = File(temporaryDirectory(), "vault")
        val vault = store(root)
        val initial = authorize(vault)
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val operation = launch {
            vault.updateSession(initial.handle) {
                entered.complete(Unit)
                finish.await()
                SessionUpdate.Replace(credentials("rotated"))
            }
        }
        entered.await()
        operation.cancel()
        finish.complete(Unit)
        operation.join()
        assertEquals(credentials("rotated"), store(root).currentSession(CLIENT)?.credentials)
    }

    @Test fun logoutWaitsForUpdateThenOldOperationCannotResurrectAccount(): Unit = runBlocking {
        val root = File(temporaryDirectory(), "vault")
        val vault = store(root)
        val initial = authorize(vault)
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        val update = launch {
            vault.updateSession(initial.handle) {
                entered.complete(Unit)
                finish.await()
                SessionUpdate.Replace(credentials("rotated"))
            }
        }
        entered.await()
        val logout = async { store(root).logout(CLIENT) }
        delay(100)
        assertFalse(logout.isCompleted)
        finish.complete(Unit)
        update.join()
        assertEquals(credentials("rotated"), logout.await()?.credentials)
        assertNull(vault.currentSession(CLIENT))
        assertFailsWith<StaleSessionException> { vault.updateSession(initial.handle) { SessionUpdate.Keep } }
    }

    @Test fun actionFailureLeavesOldCredentialsUnusableUntilExplicitReauthorization(): Unit = runBlocking {
        val root = File(temporaryDirectory(), "vault")
        val vault = store(root)
        val initial = authorize(vault)
        assertFailsWith<IOException> { vault.updateSession(initial.handle) { throw IOException("response lost") } }
        assertFailsWith<IncompleteSessionUpdateException> { store(root).currentSession(CLIENT) }
        assertFailsWith<IncompleteSessionUpdateException> { vault.updateSession(initial.handle) { SessionUpdate.Keep } }
        val fresh = authorize(vault)
        assertEquals(fresh, store(root).currentSession(CLIENT))
    }

    @Test fun failedEncodingAndFinalWriteCannotReturnSuccessfulCredentials(): Unit = runBlocking {
        val root = File(temporaryDirectory(), "vault")
        val initial = authorize(store(root))
        val delegate = JvmVaultPersistence(root)
        var writes = 0
        val failing = object : VaultPersistence by delegate {
            override fun write(bytes: ByteArray) {
                if (++writes == 2) throw IOException("disk full")
                delegate.write(bytes)
            }
        }
        assertFailsWith<IOException> {
            AtProtoAccountStore(failing, VaultCipher.Plaintext).updateSession(initial.handle) {
                SessionUpdate.Replace(credentials("new-token"))
            }
        }
        assertFailsWith<IncompleteSessionUpdateException> { store(root).currentSession(CLIENT) }
        store(root).logout(CLIENT)
        val initial2 = authorize(store(root))
        var encryptions = 0
        val encoder = object : VaultCipher {
            override fun decrypt(ciphertext: ByteArray) = ciphertext
            override fun encrypt(plaintext: ByteArray): ByteArray {
                if (++encryptions == 2) throw IOException("encoding failed")
                return plaintext
            }
        }
        assertFailsWith<IOException> {
            AtProtoAccountStore(delegate, encoder).updateSession(initial2.handle) {
                SessionUpdate.Replace(credentials("new-token"))
            }
        }
        assertFailsWith<IncompleteSessionUpdateException> { store(root).currentSession(CLIENT) }
    }

    @Test fun failedMarkerWriteDoesNotStartTheIrreversibleAction(): Unit = runBlocking {
        val root = File(temporaryDirectory(), "vault")
        val initial = authorize(store(root))
        val delegate = JvmVaultPersistence(root)
        val failing = object : VaultPersistence by delegate {
            override fun write(bytes: ByteArray): Unit = throw IOException("cannot persist marker")
        }
        var called = false
        assertFailsWith<IOException> {
            AtProtoAccountStore(failing, VaultCipher.Plaintext).updateSession(initial.handle) {
                called = true
                SessionUpdate.Keep
            }
        }
        assertFalse(called)
        assertEquals(initial, store(root).currentSession(CLIENT))
    }

    @Test fun failedStateConsumptionOrCompletionDoesNotPublishAClaimOrAccount(): Unit = runBlocking {
        val root = File(temporaryDirectory(), "vault")
        val vault = store(root)
        vault.beginAuthorization(CLIENT, DID, "state", 100, credentials("secret"))
        val files = JvmVaultPersistence(root)
        val failing = object : VaultPersistence by files {
            override fun write(bytes: ByteArray): Unit = throw IOException("disk full")
        }
        val broken = AtProtoAccountStore(failing, VaultCipher.Plaintext)
        assertFailsWith<IOException> { broken.consumeAuthorization(CLIENT, "state", 1) }
        val claim = assertNotNull(vault.consumeAuthorization(CLIENT, "state", 1))
        assertFailsWith<IOException> { broken.completeAuthorization(claim, credentials("access")) }
        assertNull(vault.currentSession(CLIENT))
    }

    @Test fun corruptionIsNotSilentlyReplacedAndSecretsAreRedacted(): Unit = runBlocking {
        val root = File(temporaryDirectory(), "vault")
        val vault = store(root)
        val session = authorize(vault)
        assertFalse(session.toString().contains("original"))
        val data = File(root, "credentials.json")
        val corrupt = "not-json".encodeToByteArray()
        data.writeBytes(corrupt)
        assertFailsWith<VaultCorruptionException> { vault.currentSession(CLIENT) }
        assertFailsWith<VaultCorruptionException> { vault.logout(CLIENT) }
        assertFailsWith<VaultCorruptionException> { authorize(vault) }
        assertContentEquals(corrupt, data.readBytes())
    }

    @Test fun encryptedVaultAuthenticatesEnvelopeAndUsesFreshNonces(): Unit = runBlocking {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val cipher = AesGcmVaultCipher { key }
        val plain = "secret-token".encodeToByteArray()
        val first = cipher.encrypt(plain)
        val second = cipher.encrypt(plain)
        assertFalse(first.contentEquals(second))
        assertContentEquals(plain, cipher.decrypt(first))
        val root = File(temporaryDirectory(), "vault")
        val session = authorize(store(root, cipher), payload = credentials("secret-token"))
        assertFalse(File(root, "credentials.json").readText().contains("secret-token"))
        assertEquals(session, store(root, cipher).currentSession(CLIENT))
        val tampered = first.copyOf().apply { this[lastIndex] = (last().toInt() xor 1).toByte() }
        assertFailsWith<Exception> { cipher.decrypt(tampered) }
        assertFailsWith<Exception> { cipher.decrypt(byteArrayOf(1)) }
        val wrong = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertFailsWith<VaultCorruptionException> { store(root, AesGcmVaultCipher { wrong }).currentSession(CLIENT) }
    }

    @Test fun desktopFilesArePrivateAndLockFileIdentitySurvivesReplacement(): Unit = runBlocking {
        val root = File(temporaryDirectory(), "vault")
        val files = JvmVaultPersistence(root)
        val identity = Files.readAttributes(files.lockFile.toPath(), BasicFileAttributes::class.java).fileKey()
        val vault = AtProtoAccountStore(files, VaultCipher.Plaintext)
        authorize(vault)
        vault.logout(CLIENT)
        assertEquals(identity, Files.readAttributes(files.lockFile.toPath(), BasicFileAttributes::class.java).fileKey())
        if (Files.getFileStore(root.toPath()).supportsFileAttributeView("posix")) {
            assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(root.toPath()))
            for (file in listOf(files.lockFile, File(root, "credentials.json"))) {
                assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(file.toPath()))
            }
        }
    }

    @Test fun separateOSProcessesSerializeUpdatesAndReadTheLatestCredential(): Unit = runBlocking {
        val directory = temporaryDirectory()
        val root = File(directory, "vault")
        val initial = authorize(store(root), payload = buildJsonObject { put("count", 0) })
        val first = child(root, "increment", initial.handle)
        val second = child(root, "increment", initial.handle)
        try {
            assertTrue(first.waitFor(30, TimeUnit.SECONDS))
            assertTrue(second.waitFor(30, TimeUnit.SECONDS))
            assertEquals(0, first.exitValue(), File(directory, "increment-${first.pid()}.log").readText())
            assertEquals(0, second.exitValue(), File(directory, "increment-${second.pid()}.log").readText())
            assertEquals(24, store(root).currentSession(CLIENT)?.credentials?.get("count")?.jsonPrimitive?.int)
        } finally {
            first.destroyForcibly()
            second.destroyForcibly()
        }
    }

    @Test fun processDeathReleasesLockButDoesNotMakeAnInterruptedRotationReusable(): Unit = runBlocking {
        val root = File(temporaryDirectory(), "vault")
        val initial = authorize(store(root))
        val process = child(root, "hold-update", initial.handle)
        try {
            withTimeout(10_000) { while (!File(root, "entered").exists()) delay(20) }
            process.destroyForcibly()
            assertTrue(process.waitFor(10, TimeUnit.SECONDS))
            withTimeout(5_000) {
                assertFailsWith<IncompleteSessionUpdateException> { store(root).currentSession(CLIENT) }
                store(root).logout(CLIENT)
                assertNull(store(root).currentSession(CLIENT))
            }
        } finally {
            process.destroyForcibly()
        }
    }

    private fun child(root: File, mode: String, handle: SessionHandle): Process {
        val java = File(System.getProperty("java.home"), "bin/java").absolutePath
        val log = File(root.parentFile, "$mode-starting.log")
        val process = ProcessBuilder(java, "-cp", System.getProperty("pano.test.classpath"),
            VaultProcessWorker::class.java.name, root.absolutePath, mode,
            handle.clientId, handle.did, handle.generation)
            .redirectErrorStream(true).redirectOutput(log).start()
        // Each worker gets a separate descriptor; rename does not disturb its writes.
        log.renameTo(File(root.parentFile, "$mode-${process.pid()}.log"))
        return process
    }
}

object VaultProcessWorker {
    @JvmStatic fun main(args: Array<String>) = runBlocking {
        val root = File(args[0])
        val vault = store(root)
        val handle = SessionHandle(args[2], args[3], args[4])
        when (args[1]) {
            "increment" -> repeat(12) {
                vault.updateSession(handle) { current ->
                    delay(5)
                    SessionUpdate.Replace(buildJsonObject { put("count", current.getValue("count").jsonPrimitive.int + 1) })
                }
            }
            "hold-update" -> vault.updateSession(handle) {
                File(root, "entered").writeText("ready")
                awaitCancellation()
            }
            else -> error("Unknown worker mode")
        }
        Unit
    }
}
