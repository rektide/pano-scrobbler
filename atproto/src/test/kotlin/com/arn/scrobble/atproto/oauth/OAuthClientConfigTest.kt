package com.arn.scrobble.atproto.oauth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OAuthClientConfigTest {
    @Test
    fun `accepts a parameterized metadata endpoint`() {
        val config = OAuthClientConfig(
            clientId = "https://example.com/custom/client.json",
            redirectUri = "com.example.app:/oauth/callback",
            scope = "atproto repo:fm.teal.feed.play?action=create",
            responseMode = OAuthResponseMode.FRAGMENT,
        )

        assertEquals("https://example.com/custom/client.json", config.clientId)
    }

    @Test
    fun `rejects an invalid client metadata URL or scope`() {
        assertFailsWith<IllegalArgumentException> {
            OAuthClientConfig("http://example.com/client.json", "https://example.com/callback", "atproto")
        }
        assertFailsWith<IllegalArgumentException> {
            OAuthClientConfig("https://example.com/client.json", "https://example.com/callback", "repo:foo")
        }
    }
}
