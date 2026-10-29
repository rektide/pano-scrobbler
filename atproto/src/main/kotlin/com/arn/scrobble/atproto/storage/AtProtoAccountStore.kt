package com.arn.scrobble.atproto.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.util.UUID

@Serializable
data class SessionHandle(val clientId: String, val did: String, val generation: String)

class AuthorizationAttempt internal constructor(val clientId: String, internal val generation: String)

/** Opaque protocol payloads are encoded by the OAuth owner, not interpreted by storage. */
data class SessionSnapshot(val handle: SessionHandle, val credentials: JsonObject) {
    override fun toString() = "SessionSnapshot(handle=$handle, credentials=<redacted>)"
}

class AuthorizationClaim internal constructor(
    val clientId: String,
    val did: String,
    internal val generation: String,
    val authorization: JsonObject,
) {
    override fun toString() = "AuthorizationClaim(clientId=$clientId, did=$did, authorization=<redacted>)"
}

sealed interface SessionUpdate {
    data class Replace(val credentials: JsonObject) : SessionUpdate {
        override fun toString() = "Replace(credentials=<redacted>)"
    }
    /** Only when the protocol owner has proved the old token was not consumed. */
    data object Keep : SessionUpdate
    data object Remove : SessionUpdate
}

/**
 * Internal native account authority. Every operation rereads disk under the same OS lock.
 * There is one active DID per client ID; handles identify a particular authorization,
 * not merely a DID. No public API can unconditionally overwrite an old session.
 */
class AtProtoAccountStore(
    private val persistence: VaultPersistence,
    private val cipher: VaultCipher,
) {
    private val lock = ExclusiveFileLock(persistence.lockFile)
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json { exceptionsWithDebugInfo = false }

    /** Reserve before discovery/PAR: logout or a newer login can invalidate slow work. */
    suspend fun reserveAuthorization(clientId: String): AuthorizationAttempt = locked {
        require(clientId.isNotBlank())
        val vault = read()
        val client = vault.clients[clientId] ?: ClientState()
        val generation = UUID.randomUUID().toString()
        save(vault.withClient(clientId, client.copy(pending = null, claim = null, attempt = generation)))
        AuthorizationAttempt(clientId, generation)
    }

    suspend fun publishAuthorization(
        attempt: AuthorizationAttempt,
        did: String,
        state: String,
        expiresAt: Long,
        authorization: JsonObject,
    ) = locked {
        require(did.startsWith("did:") && state.isNotBlank())
        val vault = read()
        val client = vault.clients[attempt.clientId] ?: throw StaleAuthorizationException()
        if (client.attempt != attempt.generation) throw StaleAuthorizationException()
        save(vault.withClient(attempt.clientId, client.copy(
            pending = PendingState(did, state, expiresAt, authorization), attempt = null,
        )))
    }

    suspend fun beginAuthorization(
        clientId: String,
        did: String,
        state: String,
        expiresAt: Long,
        authorization: JsonObject,
    ) = locked {
        require(clientId.isNotBlank() && did.startsWith("did:") && state.isNotBlank())
        val vault = read()
        val client = vault.clients[clientId] ?: ClientState()
        save(vault.withClient(clientId, client.copy(
            pending = PendingState(did, state, expiresAt, authorization),
            claim = null,
            attempt = null,
        )))
    }

    /** Matching state is consumed durably before its secrets are returned to code exchange. */
    suspend fun consumeAuthorization(clientId: String, state: String, now: Long): AuthorizationClaim? = locked {
        val vault = read()
        val client = vault.clients[clientId] ?: return@locked null
        val pending = client.pending?.takeIf { it.state == state } ?: return@locked null
        val generation = UUID.randomUUID().toString()
        val claim = if (now < pending.expiresAt) ClaimState(pending.did, generation) else null
        save(vault.withClient(clientId, client.copy(pending = null, claim = claim, attempt = null)))
        claim?.let { AuthorizationClaim(clientId, it.did, generation, pending.authorization) }
    }

    suspend fun completeAuthorization(claim: AuthorizationClaim, credentials: JsonObject): SessionSnapshot = locked {
        val vault = read()
        val client = vault.clients[claim.clientId] ?: throw StaleAuthorizationException()
        if (client.claim != ClaimState(claim.did, claim.generation)) throw StaleAuthorizationException()
        val session = SessionState(claim.did, UUID.randomUUID().toString(), credentials)
        save(vault.withClient(claim.clientId, client.copy(claim = null, session = session)))
        session.snapshot(claim.clientId)
    }

    suspend fun currentSession(clientId: String): SessionSnapshot? = locked {
        read().clients[clientId]?.session?.let {
            if (it.updating) throw IncompleteSessionUpdateException()
            it.snapshot(clientId)
        }
    }

    /**
     * The action may consume a rotating credential. Cancellation is honored while waiting
     * for the lock and before the marker is written. From that point through persistence
     * the operation finishes non-cancellably; the caller must supply bounded network I/O.
     * A crash, thrown action or failed final write leaves a durable unusable marker.
     */
    suspend fun updateSession(
        handle: SessionHandle,
        action: suspend (JsonObject) -> SessionUpdate,
    ): SessionSnapshot? = withSession(handle) {
        rotate(action)
        snapshot()
    }

    /** Fresh read through request completion/logout, without marking ordinary requests as rotation. */
    internal suspend fun <T> withSession(handle: SessionHandle, action: suspend SessionOperation.() -> T): T = locked {
        val vault = read()
        val client = vault.clients[handle.clientId] ?: throw StaleSessionException()
        val session = client.session?.takeIf { it.did == handle.did && it.generation == handle.generation }
            ?: throw StaleSessionException()
        if (session.updating) throw IncompleteSessionUpdateException()
        val operation = SessionOperation(session.credentials, handle, persist = { credentials, updating ->
            save(vault.withClient(handle.clientId, client.copy(session = credentials?.let {
                session.copy(credentials = it, updating = updating)
            })))
        })
        try {
            operation.action()
        } finally {
            operation.close()
        }
    }

    /** Only valid during withSession. Never acquire this store's lock from its action. */
    internal class SessionOperation internal constructor(
        initial: JsonObject,
        private val handle: SessionHandle,
        private val persist: (JsonObject?, Boolean) -> Unit,
    ) {
        private var active = true
        private var current: JsonObject? = initial
        val credentials: JsonObject get() {
            check(active) { "Session operation has completed" }
            return current ?: throw StaleSessionException()
        }

        /** Non-rotating metadata (e.g. a response nonce). A failure never marks the old token unsafe. */
        fun replace(credentials: JsonObject) {
            this.credentials
            persist(credentials, false)
            current = credentials
        }

        suspend fun rotate(action: suspend (JsonObject) -> SessionUpdate) {
            val before = credentials
            currentCoroutineContext().ensureActive()
            withContext(NonCancellable) {
                persist(before, true)
                // Until a final write succeeds this operation must not expose the old credential.
                current = null
                val updated = when (val result = action(before)) {
                    is SessionUpdate.Replace -> result.credentials
                    SessionUpdate.Keep -> before
                    SessionUpdate.Remove -> null
                }
                persist(updated, false)
                current = updated
            }
        }

        fun snapshot(): SessionSnapshot? {
            check(active) { "Session operation has completed" }
            return current?.let { SessionSnapshot(handle, it) }
        }

        internal fun close() { active = false }
    }

    /** Local deletion is durable first; remote revocation is a separate best-effort action. */
    suspend fun logout(clientId: String, afterRemoval: suspend () -> Unit = {}): SessionSnapshot? = locked {
        val vault = read()
        val removed = vault.clients[clientId]
        save(vault.copy(clients = vault.clients - clientId))
        afterRemoval()
        removed?.session?.snapshot(clientId)
    }

    private suspend fun <T> locked(action: suspend () -> T): T = withContext(Dispatchers.IO) {
        lock.withLock {
            currentCoroutineContext().ensureActive()
            action()
        }
    }

    private fun read(): VaultState {
        val bytes = persistence.read() ?: return VaultState()
        return try {
            json.decodeFromString<VaultState>(cipher.decrypt(bytes).decodeToString()).also {
                require(it.version == 1) { "Unsupported AT Protocol vault version" }
            }
        } catch (cause: Exception) {
            throw VaultCorruptionException(cause)
        }
    }

    private fun save(vault: VaultState) {
        val encoded = cipher.encrypt(json.encodeToString(vault).encodeToByteArray())
        if (encoded.size > MAX_VAULT_BYTES) throw IOException("AT Protocol vault is too large")
        persistence.write(encoded)
    }

    private fun VaultState.withClient(clientId: String, client: ClientState) =
        copy(clients = clients + (clientId to client))

    private fun SessionState.snapshot(clientId: String) =
        SessionSnapshot(SessionHandle(clientId, did, generation), credentials)
}

@Serializable
private data class VaultState(val version: Int = 1, val clients: Map<String, ClientState> = emptyMap())

@Serializable
private data class ClientState(
    val pending: PendingState? = null,
    val claim: ClaimState? = null,
    val session: SessionState? = null,
    val attempt: String? = null,
)

@Serializable
private data class PendingState(val did: String, val state: String, val expiresAt: Long, val authorization: JsonObject)

@Serializable
private data class ClaimState(val did: String, val generation: String)

@Serializable
private data class SessionState(
    val did: String,
    val generation: String,
    val credentials: JsonObject,
    val updating: Boolean = false,
)
