package com.arn.scrobble.atproto.oauth

import com.arn.scrobble.atproto.crypto.P256PrivateJwk
import com.arn.scrobble.atproto.crypto.toPublicKey
import com.arn.scrobble.atproto.encoding.decodeBase64Url
import kotlinx.serialization.json.Json
import java.security.Signature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DpopProofTest {
    @Test fun signsWireCompatibleProofsWithAnExistingStoredKey() {
        val key = Json.decodeFromString<P256PrivateJwk>(
            """{"kty":"EC","crv":"P-256","x":"axfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpY","y":"T-NC4v4af5uO5-tKfA-eFivOM1drMV7Oy7ZAaDe_UfU","d":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAE","alg":"ES256"}""",
        )
        var proofCount = 0
        val factory = DpopProofFactory(key, EpochClock { 1_700_000_000_999L }) { "proof-${++proofCount}" }
        val url = "https://EXAMPLE.com:443/xrpc/example?ignored=1#ignored"
        val first = factory.create("post", url)
        val retry = factory.create("post", url, nonce = "server-nonce", accessToken = "access-token")
        val publicKey = key.publicJwk().toPublicKey()

        for ((index, proof) in listOf(first, retry).withIndex()) {
            val segments = proof.split('.')
            assertEquals(3, segments.size)
            val (header, payload, signature) = segments
            assertEquals(
                Json.parseToJsonElement("""{"typ":"dpop+jwt","alg":"ES256","jwk":{"kty":"EC","crv":"P-256","x":"${key.x}","y":"${key.y}"}}"""),
                Json.parseToJsonElement(header.decodeBase64Url().decodeToString()),
            )
            val claims = """"jti":"proof-${index + 1}","htm":"POST","htu":"https://example.com/xrpc/example","iat":1700000000"""
            val extras = if (index == 0) "" else
                ""","nonce":"server-nonce","ath":"Pxa-1wifRlPl7yG_0oJNfzqq7MelmOfonFgOFgapzFI"""
            assertEquals(
                Json.parseToJsonElement("{$claims$extras}"),
                Json.parseToJsonElement(payload.decodeBase64Url().decodeToString()),
            )
            val signatureBytes = signature.decodeBase64Url()
            assertEquals(64, signatureBytes.size)
            // Verify independently of Nimbus using the JVM's native JOSE-format support.
            assertTrue(Signature.getInstance("SHA256withECDSAinP1363Format").run {
                initVerify(publicKey)
                update("$header.$payload".toByteArray(Charsets.US_ASCII))
                verify(signatureBytes)
            })
        }
    }
}
