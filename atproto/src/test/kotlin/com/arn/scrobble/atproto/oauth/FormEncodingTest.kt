package com.arn.scrobble.atproto.oauth

import kotlin.test.Test
import kotlin.test.assertEquals

class FormEncodingTest {
    @Test
    fun `round trips OAuth form fields`() {
        val values = linkedMapOf(
            "scope" to "atproto repo:fm.teal.feed.play?action=create",
            "redirect_uri" to "https://app.example/callback?from=pano",
            "missing" to null,
        )

        val encoded = formEncode(values)

        assertEquals(
            mapOf(
                "scope" to "atproto repo:fm.teal.feed.play?action=create",
                "redirect_uri" to "https://app.example/callback?from=pano",
            ),
            formDecode(encoded),
        )
    }
}
