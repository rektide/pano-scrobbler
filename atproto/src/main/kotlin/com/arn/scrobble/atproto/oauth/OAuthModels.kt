package com.arn.scrobble.atproto.oauth

import com.arn.scrobble.atproto.crypto.P256PrivateJwk
import com.arn.scrobble.atproto.identity.ResolvedIdentity
import com.arn.scrobble.atproto.storage.SessionHandle
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URI

/** This client is native/public; identity changes intentionally select a different vault namespace. */
data class OAuthClientConfig(val clientId: String, val redirectUri: String, val scope: String) {
    init {
        val client = URI(clientId)
        require(client.scheme == "https" && client.host != null && client.userInfo == null && client.port == -1 &&
            client.rawFragment == null && client.rawQuery == null && client.rawPath.length > 1) { "Invalid native OAuth client ID" }
        val redirect = URI(redirectUri)
        require(redirect.scheme == client.host.split('.').asReversed().joinToString(".") &&
            redirect.rawAuthority == null && redirect.rawPath.startsWith('/') && !redirect.rawPath.startsWith("//") &&
            redirect.rawQuery == null && redirect.rawFragment == null) { "Native callback must match the client identity" }
        require(scope.split(' ').firstOrNull() == "atproto" && scope.split(' ').all { it.isNotEmpty() && it.none(Char::isWhitespace) }) {
            "OAuth scope must start with atproto and use single spaces"
        }
        require(scope.split(' ').distinct().size == scope.split(' ').size) { "Duplicate OAuth scope" }
    }
}

data class AuthorizationLaunch(val authorizationUrl: String, val expiresAt: Long) {
    override fun toString() = "AuthorizationLaunch(authorizationUrl=<redacted>, expiresAt=$expiresAt)"
}

/** Safe to publish as account metadata. Does not contain tokens, PKCE or signing keys. */
data class AtProtoAccount(
    val session: SessionHandle,
    val handle: String?,
    val pds: String,
    val grantedScopes: Set<String>,
)

@Serializable
internal data class PendingAuthorization(
    val clientId: String,
    val redirectUri: String,
    val expectedIdentity: ResolvedIdentity,
    val authorizationServer: AuthorizationServerMetadata,
    val verifier: String,
    val dpopKey: P256PrivateJwk,
    val nonces: Map<String, String>,
) {
    override fun toString() = "PendingAuthorization(<redacted>)"
}

@Serializable
internal data class StoredSession(
    val clientId: String,
    val did: String,
    val handle: String?,
    val pds: String,
    val authorizationServer: AuthorizationServerMetadata,
    val dpopKey: P256PrivateJwk,
    val token: AtProtoToken,
    val nonces: Map<String, String>,
) {
    override fun toString() = "StoredSession(did=$did, credentials=<redacted>)"
}

@Serializable
internal data class AtProtoToken(val accessToken: String, val refreshToken: String?, val scope: String, val expiresAt: Long) {
    override fun toString() = "AtProtoToken(<redacted>)"
}

@Serializable
internal data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long,
    val scope: String,
    @SerialName("sub") val subject: String,
) {
    fun token(now: Long, expectedDid: String, previousRefreshToken: String? = null): AtProtoToken {
        require(subject == expectedDid) { "Token subject does not match the authorized account" }
        require(tokenType.equals("DPoP", ignoreCase = true)) { "Token response is not DPoP-bound" }
        require(accessToken.isNotBlank() && accessToken.all { it.code in 0x21..0x7e }) { "Invalid access token" }
        require(refreshToken == null || refreshToken.isNotBlank()) { "Invalid refresh token" }
        require(expiresIn > 0 && now >= 0 && expiresIn <= (Long.MAX_VALUE - now) / 1000) { "Invalid token expiry" }
        val scopes = scope.split(' ')
        require(scopes.all { it.isNotEmpty() && it.all { char -> char.code == 0x21 || char.code in 0x23..0x5b || char.code in 0x5d..0x7e } } &&
            "atproto" in scopes && scopes.size == scopes.distinct().size) { "Invalid granted OAuth scope" }
        return AtProtoToken(accessToken, refreshToken ?: previousRefreshToken, scope, now + expiresIn * 1000)
    }

    override fun toString() = "TokenResponse(<redacted>)"
}

open class AtProtoOAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

class OAuthResponseException(val status: Int, val error: String?) :
    AtProtoOAuthException("OAuth request failed (HTTP $status, ${error?.takeIf { it.matches(Regex("[a-z_]{1,64}")) } ?: "unclassified"})")

class AuthorizationDeniedException : AtProtoOAuthException("Authorization was not approved")
