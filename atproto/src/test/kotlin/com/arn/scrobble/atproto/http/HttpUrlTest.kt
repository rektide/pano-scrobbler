package com.arn.scrobble.atproto.http

import io.ktor.http.Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HttpUrlTest {
    @Test fun normalizesDpopTarget() {
        assertEquals("https://example.com/xrpc/com.atproto.repo.createRecord", normalizeHtu("HTTPS://Example.COM:443/xrpc/com.atproto.repo.createRecord?foo=bar#part"))
    }
    @Test fun keepsNondefaultPortAndRootPath() { assertEquals("http://127.0.0.1:8080/", normalizeHtu("http://127.0.0.1:8080")) }
    @Test fun ipv6Brackets() { assertEquals("http://[::1]:8080/callback", normalizeHtu("http://[::1]:8080/callback")) }
    @Test fun rejectsNonHttpAndUserInfo() {
        assertFailsWith<IllegalArgumentException> { normalizeHtu("pano-scrobbler:/callback") }
        assertFailsWith<IllegalArgumentException> { normalizeHtu("https://user@example.com/path") }
    }
    @Test fun proofMatchesEncodedRequestPath() {
        for (url in listOf("https://tokens.example.com/jeton/é?x=1", "https://tokens.example.com/😀",
            "https://tokens.example.com/a%2Fb/%7e", "https://tokens.example.com/a/../token"))
            assertEquals(normalizeHtu(Url(url).toString()), normalizeHtu(url), url)
        assertEquals("https://tokens.example.com/jeton/%C3%A9", normalizeHtu("https://tokens.example.com/jeton/é"))
    }
    @Test fun rejectsInvalidPorts() {
        for (url in listOf("https://tokens.example.com:/token", "https://tokens.example.com:65536/token"))
            assertFailsWith<IllegalArgumentException>(url) { normalizeHtu(url) }
    }
}
