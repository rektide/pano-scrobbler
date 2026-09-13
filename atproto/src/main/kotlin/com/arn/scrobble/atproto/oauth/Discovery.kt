package com.arn.scrobble.atproto.oauth

import com.arn.scrobble.atproto.http.fetchJson
import com.arn.scrobble.atproto.http.httpOrigin
import com.arn.scrobble.atproto.identity.IdentityResolver
import com.arn.scrobble.atproto.identity.ResolvedIdentity
import io.ktor.client.HttpClient
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.net.URI

@Serializable
internal data class DiscoveredAccount(
    val identity: ResolvedIdentity,
    val authorizationServer: AuthorizationServerMetadata,
)

@Serializable
internal data class AuthorizationServerMetadata(
    val issuer: String,
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("pushed_authorization_request_endpoint") val pushedAuthorizationRequestEndpoint: String,
    @SerialName("revocation_endpoint") val revocationEndpoint: String? = null,
    @SerialName("introspection_endpoint") val introspectionEndpoint: String? = null,
    @SerialName("protected_resources") val protectedResources: List<String>? = null,
    @SerialName("response_types_supported") val responseTypesSupported: List<String> = emptyList(),
    @SerialName("grant_types_supported") val grantTypesSupported: List<String> = emptyList(),
    @SerialName("code_challenge_methods_supported") val codeChallengeMethodsSupported: List<String> = emptyList(),
    @SerialName("token_endpoint_auth_methods_supported") val tokenEndpointAuthMethodsSupported: List<String> = emptyList(),
    @SerialName("scopes_supported") val scopesSupported: List<String> = emptyList(),
    @SerialName("dpop_signing_alg_values_supported") val dpopSigningAlgorithmsSupported: List<String> = emptyList(),
    @SerialName("authorization_response_iss_parameter_supported")
    val authorizationResponseIssuerParameterSupported: Boolean = false,
    @SerialName("require_pushed_authorization_requests") val requiresPushedAuthorizationRequests: Boolean = false,
    @SerialName("client_id_metadata_document_supported") val clientIdMetadataDocumentSupported: Boolean = false,
)

internal class OAuthDiscovery(
    private val httpClient: HttpClient,
    private val identityResolver: IdentityResolver = IdentityResolver(httpClient),
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun resolve(identifier: String): DiscoveredAccount {
        val identity = identityResolver.resolve(identifier)
        return resolve(identity)
    }

    suspend fun resolve(identity: ResolvedIdentity): DiscoveredAccount {
        val resource = canonicalHttpsOrigin(identity.pds)
        val protectedResourceUrl = "$resource/.well-known/oauth-protected-resource"
        val resourceElement = httpClient.fetchJson(
            url = protectedResourceUrl,
            maxBytes = 16 * 1024,
            json = json,
        )
        val resourceMetadata = json.decodeFromString<ProtectedResourceMetadata>(resourceElement.toString())

        require(resourceMetadata.resource == resource) { "Protected resource metadata does not match the PDS" }
        require(resourceMetadata.authorizationServers.size == 1) {
            "Protected resource metadata must declare exactly one authorization server"
        }

        val authorizationServerOrigin = canonicalHttpsOrigin(resourceMetadata.authorizationServers.single())
        val serverElement = httpClient.fetchJson(
            url = "$authorizationServerOrigin/.well-known/oauth-authorization-server",
            maxBytes = 32 * 1024,
            json = json,
        )
        val server = json.decodeFromString<AuthorizationServerMetadata>(serverElement.toString())
        validateAuthorizationServer(server, authorizationServerOrigin, resource)

        return DiscoveredAccount(identity = identity, authorizationServer = server)
    }
}

@Serializable
private data class ProtectedResourceMetadata(
    val resource: String,
    @SerialName("authorization_servers") val authorizationServers: List<String> = emptyList(),
)

private fun validateAuthorizationServer(
    server: AuthorizationServerMetadata,
    expectedIssuer: String,
    resource: String,
) {
    require(server.issuer == expectedIssuer) { "Authorization server issuer does not match its metadata origin" }
    requireHttpsUrl(server.authorizationEndpoint, "authorization endpoint")
    requireHttpsUrl(server.tokenEndpoint, "token endpoint")
    requireHttpsUrl(server.pushedAuthorizationRequestEndpoint, "PAR endpoint")
    server.revocationEndpoint?.let { requireHttpsUrl(it, "revocation endpoint") }
    server.introspectionEndpoint?.let { requireHttpsUrl(it, "introspection endpoint") }
    require("code" in server.responseTypesSupported) { "Authorization server does not support code responses" }
    require("authorization_code" in server.grantTypesSupported) {
        "Authorization server does not support authorization-code grants"
    }
    require("refresh_token" in server.grantTypesSupported) {
        "Authorization server does not support refresh-token grants"
    }
    require("S256" in server.codeChallengeMethodsSupported) { "Authorization server does not support PKCE S256" }
    require("none" in server.tokenEndpointAuthMethodsSupported) {
        "Authorization server does not support public clients"
    }
    require("atproto" in server.scopesSupported) { "Authorization server does not support the atproto scope" }
    require("ES256" in server.dpopSigningAlgorithmsSupported) { "Authorization server does not support ES256 DPoP" }
    require(server.authorizationResponseIssuerParameterSupported) {
        "Authorization server does not support callback issuer parameters"
    }
    require(server.requiresPushedAuthorizationRequests) { "Authorization server does not require PAR" }
    require(server.clientIdMetadataDocumentSupported) {
        "Authorization server does not support client metadata documents"
    }
    server.protectedResources?.let {
        require(resource in it) { "Authorization server does not list the PDS as a protected resource" }
    }
}

private fun requireHttpsUrl(value: String, label: String) {
    val uri = URI(value)
    require(uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.rawFragment == null) {
        "Authorization server $label must be an HTTPS URL"
    }
}

private fun canonicalHttpsOrigin(value: String): String {
    val uri = URI(value)
    require(uri.scheme == "https" && uri.host != null && uri.userInfo == null) { "OAuth origins must use HTTPS" }
    require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") {
        "OAuth origins must not contain a path"
    }
    require(uri.rawQuery == null && uri.rawFragment == null) { "OAuth origins must not contain a query or fragment" }
    return httpOrigin(value)
}
