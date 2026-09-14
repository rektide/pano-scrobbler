package com.arn.scrobble.api.atproto

import com.arn.scrobble.BuildKonfig
import com.arn.scrobble.atproto.oauth.OAuthClientConfig
import com.arn.scrobble.atproto.oauth.OAuthResponseMode
import com.arn.scrobble.atproto.posting.AtProtoTarget

object PanoAtProtoConfig {
    val clientMetadataUrl: String
        get() = BuildKonfig.ATPROTO_OAUTH_CLIENT_ID

    val redirectUri: String
        get() = BuildKonfig.ATPROTO_OAUTH_REDIRECT_URI

    val scope = "atproto " + AtProtoTarget.entries.joinToString(" ") {
        "repo:${it.collection}?action=create"
    }

    val oauth by lazy {
        OAuthClientConfig(
            clientId = clientMetadataUrl,
            redirectUri = redirectUri,
            scope = scope,
            responseMode = OAuthResponseMode.QUERY,
        )
    }
}
