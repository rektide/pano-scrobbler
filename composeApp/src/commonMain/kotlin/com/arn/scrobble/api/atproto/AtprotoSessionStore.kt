package com.arn.scrobble.api.atproto

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.time.Instant

/** A completed login to the account's PDS, independent of the music service using it. */
sealed interface AtprotoSession {
    val did: String
    val pds: String
}

class AtprotoSessionStorageException internal constructor(message: String) : Exception(message)

/** Protected local sessions outside Pano's exports. Throws if this platform has no protected store. */
expect val atprotoSessionStore: AtprotoSessionStore

/** Teal and Rocksky share one login per DID for this Pano client, even across auth modes. */
class AtprotoSessionStore internal constructor(
    private val store: DataStore<AtprotoSessionSnapshot>,
) {
    /** Atomically replaces the old login only after a completed login can be stored. */
    suspend fun save(session: AtprotoSession) = sessionStorageOperation("save") {
        val record = when (session) {
            is AtprotoAppPasswordSession -> StoredAtprotoSession.AppPassword(
                session.pds, session.accessJwt, session.refreshJwt,
            )
            is AtprotoOAuthSession -> {
                require(session.clientId == ATPROTO_CLIENT_ID)
                StoredAtprotoSession.OAuth(
                    session.pds, session.issuer, session.clientId, session.scopes.toSet(),
                    session.expiresAt, session.accessToken, session.refreshToken,
                    session.dpop.privateJwkForStorage(),
                )
            }
        }
        store.updateData { AtprotoSessionSnapshot(it.sessions + (session.did to record)) }
        Unit
    }

    /** Returns null only for an absent login. A read or key failure is not a disconnect. */
    suspend fun restore(did: String): AtprotoSession? = sessionStorageOperation("restore") {
        when (val record = store.data.first().sessions[did]) {
            is StoredAtprotoSession.AppPassword -> AtprotoAppPasswordSession(
                did, record.pds, record.accessJwt, record.refreshJwt,
            )
            is StoredAtprotoSession.OAuth -> {
                require(record.clientId == ATPROTO_CLIENT_ID)
                val key = ECKey.parse(record.privateJwk)
                require(key.curve == Curve.P_256 && key.d != null)
                AtprotoOAuthSession(
                    did, record.pds, record.issuer, record.scopes.toSet(), record.expiresAt,
                    record.accessToken, record.refreshToken, AtprotoOAuthDpop(key), record.clientId,
                )
            }
            null -> null
        }
    }

    /** Removes the local login for both music services without contacting the provider. */
    suspend fun disconnect(did: String) = sessionStorageOperation("disconnect") {
        store.updateData { if (did in it.sessions) AtprotoSessionSnapshot(it.sessions - did) else it }
        Unit
    }
}

private inline fun <T> sessionStorageOperation(operation: String, block: () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    // Serialization and key parsing errors can contain serialized credentials. Do not chain them.
    throw AtprotoSessionStorageException("Could not $operation the local AT Protocol session")
}

@Serializable
internal class AtprotoSessionSnapshot(val sessions: Map<String, StoredAtprotoSession> = emptyMap())

@Serializable
internal sealed interface StoredAtprotoSession {
    @Serializable
    @SerialName("app_password")
    class AppPassword(val pds: String, val accessJwt: String, val refreshJwt: String) : StoredAtprotoSession

    @Serializable
    @SerialName("oauth")
    class OAuth(
        val pds: String,
        val issuer: String,
        val clientId: String,
        val scopes: Set<String>,
        val expiresAt: Instant,
        val accessToken: String,
        val refreshToken: String?,
        val privateJwk: String,
    ) : StoredAtprotoSession
}

/** Encrypts the whole snapshot, including account identifiers. Key creation is confined to writes. */
internal class AtprotoSessionSerializer(private val key: (createIfMissing: Boolean) -> SecretKey) :
    Serializer<AtprotoSessionSnapshot> {
    override val defaultValue = AtprotoSessionSnapshot()

    override suspend fun readFrom(input: InputStream): AtprotoSessionSnapshot {
        val bytes = input.readBytes()
        if (bytes.size < IV_BYTES + TAG_BITS / 8)
            throw CorruptionException("Invalid encrypted AT Protocol session file")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        cipher.updateAAD(SESSION_FORMAT)
        return Json.decodeFromString(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES).decodeToString())
    }

    override suspend fun writeTo(t: AtprotoSessionSnapshot, output: OutputStream) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(true))
        check(cipher.iv.size == IV_BYTES)
        cipher.updateAAD(SESSION_FORMAT)
        val encrypted = cipher.doFinal(Json.encodeToString(t).encodeToByteArray())
        output.write(cipher.iv)
        output.write(encrypted)
    }

    private companion object {
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        val SESSION_FORMAT = "pano-atproto-sessions-v1".encodeToByteArray()
    }
}
