package com.arn.scrobble.atproto.oauth

import java.net.URI

enum class OAuthResponseMode(val value: String) {
    QUERY("query"),
    FRAGMENT("fragment"),
}

data class OAuthClientConfig(
    val clientId: String,
    val redirectUri: String,
    val scope: String,
    val responseMode: OAuthResponseMode = OAuthResponseMode.QUERY,
) {
    init {
        val clientUri = URI(clientId)
        require(clientUri.scheme == "https" && clientUri.host != null && clientUri.userInfo == null) {
            "AT Protocol client metadata must have an HTTPS URL"
        }
        require(clientUri.port == -1 && clientUri.rawFragment == null) {
            "AT Protocol client metadata URL must not contain a port or fragment"
        }
        require(clientUri.rawPath.isNotEmpty() && clientUri.rawPath != "/") {
            "AT Protocol client metadata URL must contain a document path"
        }

        val redirect = URI(redirectUri)
        require(redirect.isAbsolute && redirect.rawFragment == null) { "OAuth redirect URI is invalid" }
        if (redirect.scheme != "https") {
            val expectedScheme = clientUri.host.split('.').asReversed().joinToString(".")
            require(redirect.scheme == expectedScheme) {
                "Native OAuth redirect scheme must reverse the client metadata hostname"
            }
            require(redirect.rawAuthority == null && redirect.rawPath.startsWith('/')) {
                "Native OAuth redirect URI must use one slash followed by a path"
            }
        }

        val scopes = scope.split(Regex("\\s+")).filter { it.isNotEmpty() }
        require(scopes.firstOrNull() == "atproto") { "OAuth scope must start with atproto" }
        require(scopes.size == scopes.distinct().size) { "OAuth scope must not contain duplicates" }
    }
}
