package com.arn.scrobble.api.atproto

import com.arn.scrobble.BuildKonfig
import com.arn.scrobble.atproto.oauth.OAuthClientConfig
import com.arn.scrobble.atproto.oauth.OAuthResponseMode
import com.arn.scrobble.atproto.posting.AtProtoTarget

object PanoAtProtoConfig {
    val scope = "atproto " + AtProtoTarget.entries.joinToString(" ") {
        "repo:${it.collection}?action=create"
    }

    val oauth by lazy {
        OAuthClientConfig(
            clientId = BuildKonfig.ATPROTO_OAUTH_CLIENT_ID,
            redirectUri = BuildKonfig.ATPROTO_OAUTH_REDIRECT_URI,
            scope = scope,
            responseMode = OAuthResponseMode.QUERY,
        )
    }
}
