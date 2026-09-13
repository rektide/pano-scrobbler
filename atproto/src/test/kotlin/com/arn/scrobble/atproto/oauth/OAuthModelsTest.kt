package com.arn.scrobble.atproto.oauth

import kotlin.test.Test
import kotlin.test.assertEquals

class OAuthModelsTest {
    @Test
    fun `parses query and fragment callbacks`() {
        val query = OAuthCallback.fromUrl(
            "com.example.app:/callback?code=abc&state=state-1&iss=https%3A%2F%2Fauth.example",
        )
        val fragment = OAuthCallback.fromUrl(
            "https://example.com/callback#code=def&state=state-2&iss=https%3A%2F%2Fauth.example",
        )

        assertEquals("abc", query.code)
        assertEquals("state-1", query.state)
        assertEquals("def", fragment.code)
        assertEquals("state-2", fragment.state)
    }
}
