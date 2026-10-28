package com.arn.scrobble.atproto.oauth

// Evidence-only probes of the OLD checkpoint, not conformance regression tests.
// Run as a test source in /tmp/opencode/pano-protocol-baseline; do not carry
// these acceptance-of-invalid-input assertions into the clean implementation.
// Session: opencode://ses_e7b3ccec5ffeuJpOmxDoerRcX6.

import com.arn.scrobble.atproto.crypto.P256PrivateJwk
import com.arn.scrobble.atproto.crypto.P256PublicJwk
import com.arn.scrobble.atproto.encoding.encodeBase64Url
import com.arn.scrobble.atproto.posting.AtProtoTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtocolMigrationProbeTest {
    @Test
    fun `old callback parser accepts duplicates and fragment overrides`() {
        val callback = OAuthCallback.fromUrl(
            "io.github.kawaiidango:/pano-scrobbler/oauth/callback" +
                "?state=first&state=second&code=query-code&iss=https%3A%2F%2Fauth.example.com" +
                "#state=fragment-state&code=fragment-code",
        )
        assertEquals("fragment-state", callback.state)
        assertEquals("fragment-code", callback.code)
        assertEquals("https://auth.example.com", callback.issuer)
    }

    @Test
    fun `old token conversion overflows a positive expiry into an expired token`() {
        val clock = object : EpochClock { override fun millis() = 1_000L }
        val token = TokenResponse(
            accessToken = "fixture-access-token",
            tokenType = "DPoP",
            expiresIn = Long.MAX_VALUE,
            scope = "atproto",
            subject = "did:plc:abcdefghijklmnopqrstuvwx",
        ).toToken(clock)
        assertTrue(token.expiresAt!! < clock.millis())
    }

    @Test
    fun `old JWK constructors accept an off-curve point and zero private scalar`() {
        val zero = ByteArray(32).encodeBase64Url()
        val public = P256PublicJwk("EC", "P-256", zero, zero)
        val private = P256PrivateJwk("EC", "P-256", zero, zero, zero, "ES256")
        assertEquals(zero, public.x)
        assertEquals(zero, private.d)
    }

    @Test
    fun `old PKCE helper hashes invalid verifiers after lossy ASCII replacement`() {
        assertEquals(pkceChallenge("?".repeat(43)), pkceChallenge("é".repeat(43)))
    }

    @Test
    fun `old grant parser misses valid named and encoded permissions but accepts invalid actions`() {
        assertTrue(AtProtoTarget.grantedBy("atproto repo?collection=fm.teal.feed.play&action=create").isEmpty())
        assertTrue(AtProtoTarget.grantedBy("atproto repo:fm.teal.feed.play?action=%63reate").isEmpty())
        assertEquals(
            setOf(AtProtoTarget.TEAL),
            AtProtoTarget.grantedBy("atproto repo:fm.teal.feed.play?action=create&action=not-an-action"),
        )
    }
}
