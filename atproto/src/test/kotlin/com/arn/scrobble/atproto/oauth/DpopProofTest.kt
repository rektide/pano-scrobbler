package com.arn.scrobble.atproto.oauth

import com.arn.scrobble.atproto.crypto.generateP256PrivateJwk
import com.arn.scrobble.atproto.crypto.verifyEs256
import com.arn.scrobble.atproto.encoding.decodeBase64Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DpopProofTest {
    @Test
    fun `creates a signed resource proof with normalized claims`() {
        val key = generateP256PrivateJwk()
        val factory = DpopProofFactory(
            key = key,
            clock = Clock.fixed(Instant.ofEpochSecond(1_714_657_900), ZoneOffset.UTC),
            nextJti = { "fixed-jti" },
        )

        val proof = factory.create(
            method = "post",
            url = "https://PDS.example.com:443/xrpc/com.atproto.repo.createRecord?ignored=yes",
            nonce = "server-nonce",
            accessToken = "abcdef.ghijkl",
        )
        val segments = proof.split('.')
        val header = decodeJson(segments[0])
        val claims = decodeJson(segments[1])

        assertEquals("dpop+jwt", header["typ"]?.jsonPrimitive?.content)
        assertEquals("ES256", header["alg"]?.jsonPrimitive?.content)
        assertFalse("d" in requireNotNull(header["jwk"]).jsonObject)
        assertEquals("POST", claims["htm"]?.jsonPrimitive?.content)
        assertEquals(
            "https://pds.example.com/xrpc/com.atproto.repo.createRecord",
            claims["htu"]?.jsonPrimitive?.content,
        )
        assertEquals("1714657900", claims["iat"]?.jsonPrimitive?.content)
        assertEquals("server-nonce", claims["nonce"]?.jsonPrimitive?.content)
        assertEquals(hashAccessToken("abcdef.ghijkl"), claims["ath"]?.jsonPrimitive?.content)
        assertTrue(
            verifyEs256(
                "${segments[0]}.${segments[1]}".toByteArray(),
                segments[2].decodeBase64Url(),
                key.publicJwk(),
            ),
        )
    }

    @Test
    fun `omits nonce and ath for an initial authorization request`() {
        val proof = DpopProofFactory(generateP256PrivateJwk()).create(
            method = "POST",
            url = "https://auth.example.com/oauth/par",
        )
        val claims = decodeJson(proof.split('.')[1])

        assertFalse("nonce" in claims)
        assertFalse("ath" in claims)
    }

    private fun decodeJson(segment: String) = Json.parseToJsonElement(
        segment.decodeBase64Url().toString(Charsets.UTF_8),
    ).jsonObject
}
