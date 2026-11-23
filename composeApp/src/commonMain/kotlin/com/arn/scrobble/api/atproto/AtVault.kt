package com.arn.scrobble.api.atproto

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.RandomAccessFile

@Serializable
internal enum class AtTarget(val label: String, val play: String, val status: String) {
    TEAL("Teal", "fm.teal.feed.play", "fm.teal.actor.status"),
    ROCKSKY("Rocksky", "app.rocksky.scrobble", "app.rocksky.actor.status"),
}

@Serializable
internal data class AtSettings(
    val completed: Set<AtTarget> = emptySet(),
    val live: Set<AtTarget> = emptySet(),
    val delete: Set<AtTarget> = emptySet(),
    val likes: Boolean = false,
) {
    fun requested(): Map<String, Set<String>> = buildMap {
        AtTarget.entries.forEach { target ->
            val play = buildSet {
                if (target in completed) add("create")
                if (target in delete) add("delete")
            }
            if (play.isNotEmpty()) put(target.play, play)
            if (target in live) put(target.status, setOf("create", "update", "delete"))
        }
        if (likes) put(ROCKSKY_LIKE, setOf("create", "delete"))
    }
}

internal const val ROCKSKY_LIKE = "app.rocksky.like"
@Serializable internal enum class AtMethod { APP_PASSWORD, OAUTH }

@Serializable
internal data class AtSigner(val key: String, val nonce: String? = null)

@Serializable
internal data class AtIssuer(val origin: String, val par: String, val authorize: String, val token: String, val revoke: String?)

@Serializable
internal data class AtSession(
    val identity: AtIdentity,
    val generation: String,
    val method: AtMethod,
    val access: String,
    val refresh: String,
    val expires: Long,
    val permissions: Map<String, Set<String>> = emptyMap(),
    val issuer: AtIssuer? = null,
    val signer: AtSigner? = null,
    val claimed: Boolean = false,
)

@Serializable
internal data class AtPending(
    val identity: AtIdentity,
    val issuer: AtIssuer,
    val signer: AtSigner,
    val state: String,
    val verifier: String,
    val expires: Long,
    val settings: AtSettings,
    val requested: Map<String, Set<String>>,
)

@Serializable
internal data class AtVaultState(
    val active: AtIdentity? = null,
    val session: AtSession? = null,
    val pending: AtPending? = null,
    val settings: Map<String, AtSettings> = emptyMap(),
    val revision: String = "initial",
)

internal interface AtVaultFiles {
    val lockFile: File
    fun read(): ByteArray?
    fun write(bytes: ByteArray)
}

internal expect fun platformAtVault(allowDesktopFile: Boolean): AtVaultFiles

internal class AtVault(private val factory: (Boolean) -> AtVaultFiles = ::platformAtVault) {
    // FileChannel locks from the same JVM can throw instead of waiting. Serialize them first.
    private companion object { val mutex = Mutex() }

    suspend fun <T> locked(allowDesktopFile: Boolean = false, block: suspend Transaction.() -> T): T =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                val files = storage { factory(allowDesktopFile) }
                storage { RandomAccessFile(files.lockFile, "rw") }.use { file ->
                    storage { file.channel.lock() }.use {
                        val bytes = storage { files.read() }
                        val state = if (bytes == null) AtVaultState() else storage {
                            Json.decodeFromString<AtVaultState>(bytes.decodeToString())
                        }
                        Transaction(files, state).block()
                    }
                }
            }
        }

    internal class Transaction(private val files: AtVaultFiles, initial: AtVaultState) {
        var state: AtVaultState = initial
            private set

        fun save(next: AtVaultState) {
            storage { files.write(Json.encodeToString(next).encodeToByteArray()) }
            state = next
        }
    }
}

private inline fun <T> storage(block: () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    throw AtFailure(AtProblem.STORAGE)
}
