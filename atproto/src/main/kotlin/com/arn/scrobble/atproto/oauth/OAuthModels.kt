package com.arn.scrobble.atproto.oauth

import com.arn.scrobble.atproto.crypto.P256PrivateJwk
import com.arn.scrobble.atproto.identity.ResolvedIdentity
import kotlinx.serialization.Serializable
import java.net.URI

@Serializable
data class PendingAuthorization(
    val state: String,
    val expectedIdentity: ResolvedIdentity,
    val authorizationServer: AuthorizationServerMetadata,
    val verifier: String,
    val dpopKey: P256PrivateJwk,
    val expiresAt: Long,
) {
    override fun toString(): String =
        "PendingAuthorization(state=$state, expectedIdentity=$expectedIdentity, " +
            "authorizationServer=$authorizationServer, verifier=<redacted>, dpopKey=$dpopKey, expiresAt=$expiresAt)"
}

@Serializable
data class AtProtoToken(
    val accessToken: String,
    val refreshToken: String? = null,
    val scope: String,
    val expiresAt: Long? = null,
) {
    override fun toString(): String =
        "AtProtoToken(accessToken=<redacted>, refreshToken=${if (refreshToken == null) "null" else "<redacted>"}, " +
            "scope=$scope, expiresAt=$expiresAt)"
}

@Serializable
data class AtProtoSession(
    val did: String,
    val handle: String? = null,
    val pds: String,
    val authorizationServer: AuthorizationServerMetadata,
    val dpopKey: P256PrivateJwk,
    val token: AtProtoToken,
)

data class AuthorizationLaunch(
    val authorizationUrl: String,
    val expiresAt: Long,
)

data class OAuthCallback(
    val state: String?,
    val code: String?,
    val issuer: String?,
    val error: String?,
    val errorDescription: String?,
) {
    override fun toString(): String =
        "OAuthCallback(state=$state, code=${if (code == null) "null" else "<redacted>"}, " +
            "issuer=$issuer, error=$error, errorDescription=$errorDescription)"

    companion object {
        fun fromUrl(url: String): OAuthCallback {
            val uri = URI(url)
            val query = uri.rawQuery?.let(::formDecode).orEmpty()
            val fragment = uri.rawFragment?.let(::formDecode).orEmpty()
            val values = query + fragment

            return OAuthCallback(
                state = values["state"],
                code = values["code"],
                issuer = values["iss"],
                error = values["error"],
                errorDescription = values["error_description"],
            )
        }
    }
}

interface AuthorizationStateStore {
    /** Stores a state without replacing an existing entry with the same random state. */
    suspend fun put(authorization: PendingAuthorization)

    /** Atomically returns and deletes a state so callbacks cannot be replayed. */
    suspend fun take(state: String): PendingAuthorization?
}

interface AtProtoSessionStore {
    suspend fun get(did: String): AtProtoSession?

    /** Atomically persists a complete token rotation before returning. */
    suspend fun put(session: AtProtoSession)

    suspend fun delete(did: String)
}

open class AtProtoOAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

class OAuthResponseException(
    val status: Int,
    val error: String?,
    message: String,
) : AtProtoOAuthException(message)
