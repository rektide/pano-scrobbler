package com.arn.scrobble.atproto.http

import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthenticationChallengeTest {
    @Test
    fun `finds an exact DPoP error across multiple challenges and header lines`() {
        for (value in listOf(
            "DPoP error=use_dpop_nonce",
            "dPoP ERROR = \"use_dpop_nonce\"",
            "Bearer realm=\"login\", DPoP error=\"use_dpop_nonce\"",
            "DPoP realm=\"commas, and \\\"quotes\\\"\", error=\"use_dpop_nonce\", Basic realm=other",
        )) {
            assertTrue(headersOf(HttpHeaders.WWWAuthenticate, value).hasDpopError("use_dpop_nonce"), value)
        }
        assertTrue(headersOf(HttpHeaders.WWWAuthenticate, listOf(
            "Basic realm=first", "DPoP error=\"use_dpop_nonce\"",
        )).hasDpopError("use_dpop_nonce"))
    }

    @Test
    fun `rejects spoofed ambiguous or malformed challenge errors`() {
        for (value in listOf(
            "DPoPish error=\"use_dpop_nonce\"",
            "Bearer error=\"use_dpop_nonce\"",
            "DPoP error=\"USE_DPOP_NONCE\"",
            "DPoP error_description=\"use_dpop_nonce\"",
            "DPoP realm=\"error=\\\"use_dpop_nonce\\\"\"",
            "DPoP error=\"use_dpop_nonce\", error=\"invalid_token\"",
            "DPoP error=\"invalid_token\", ERROR=\"use_dpop_nonce\"",
            "DPoP error=\"use_dpop_nonce",
            "DPoP error=",
        )) {
            assertFalse(headersOf(HttpHeaders.WWWAuthenticate, value).hasDpopError("use_dpop_nonce"), value)
        }
    }
}
