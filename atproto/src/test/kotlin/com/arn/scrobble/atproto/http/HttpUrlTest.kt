package com.arn.scrobble.atproto.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HttpUrlTest {
    @Test
    fun `normalizes a DPoP target URL`() {
        assertEquals(
            "https://example.com/xrpc/com.atproto.repo.createRecord",
            normalizeHtu("HTTPS://Example.COM:443/xrpc/com.atproto.repo.createRecord?foo=bar#part"),
        )
    }

    @Test
    fun `keeps non-default ports and supplies a root path`() {
        assertEquals("http://127.0.0.1:8080/", normalizeHtu("http://127.0.0.1:8080"))
    }

    @Test
    fun `renders IPv6 hosts with brackets`() {
        assertEquals("http://[::1]:8080/callback", normalizeHtu("http://[::1]:8080/callback"))
    }

    @Test
    fun `rejects non-HTTP URLs and user information`() {
        assertFailsWith<IllegalArgumentException> { normalizeHtu("pano-scrobbler:/callback") }
        assertFailsWith<IllegalArgumentException> { normalizeHtu("https://user@example.com/path") }
    }
}
