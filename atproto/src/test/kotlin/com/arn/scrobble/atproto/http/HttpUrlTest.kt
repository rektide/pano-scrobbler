package com.arn.scrobble.atproto.http

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import io.ktor.http.Url

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

    @Test
    fun `proof target matches the encoded request URL path`() {
        for (url in listOf(
            "https://tokens.example.com/jeton/é?x=1", "https://tokens.example.com/😀",
            "https://tokens.example.com/a%2Fb/%7e", "https://tokens.example.com/a/../token",
        )) assertEquals(normalizeHtu(Url(url).toString()), normalizeHtu(url), url)
        assertEquals("https://tokens.example.com/jeton/%C3%A9", normalizeHtu("https://tokens.example.com/jeton/é"))
    }

    @Test
    fun `rejects invalid and ambiguous URL ports`() {
        for (url in listOf("https://tokens.example.com:/token", "https://tokens.example.com:65536/token")) {
            assertFailsWith<IllegalArgumentException>(url) { normalizeHtu(url) }
        }
    }
}
