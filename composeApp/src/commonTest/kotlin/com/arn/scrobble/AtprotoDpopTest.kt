package com.arn.scrobble

import com.arn.scrobble.api.atproto.atprotoDpop
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.SignedJWT
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AtprotoDpopTest {
    @Test
    fun signsFreshProofsWithOnlyThePublicKeyOnTheWire() {
        val key = ECKeyGenerator(Curve.P_256).generate()
        val url = "https://pds.example:443/xrpc/method?ignore=1#ignore".toHttpUrl()
        val first = SignedJWT.parse(key.atprotoDpop("GET", url, nonce = null))
        val second = SignedJWT.parse(key.atprotoDpop("GET", url, nonce = "server-nonce", accessToken = "abc"))

        for (proof in listOf(first, second)) {
            val jwk = proof.header.jwk as ECKey
            assertFalse(jwk.isPrivate)
            assertEquals(key.toPublicJWK(), jwk)
            assertEquals(JWSAlgorithm.ES256, proof.header.algorithm)
            assertEquals("dpop+jwt", proof.header.type.toString())
            assertTrue(proof.verify(ECDSAVerifier(jwk)))
            assertEquals("https://pds.example/xrpc/method", proof.jwtClaimsSet.getStringClaim("htu"))
            assertEquals("GET", proof.jwtClaimsSet.getStringClaim("htm"))
            assertNotNull(proof.jwtClaimsSet.issueTime)
            assertNotNull(proof.jwtClaimsSet.jwtid)
        }
        assertNotEquals(first.jwtClaimsSet.jwtid, second.jwtClaimsSet.jwtid)
        assertNull(first.jwtClaimsSet.getStringClaim("nonce"))
        assertNull(first.jwtClaimsSet.getStringClaim("ath"))
        assertEquals("server-nonce", second.jwtClaimsSet.getStringClaim("nonce"))
        assertEquals("ungWv48Bz-pBQUDeXa4iI7ADYaOWF3qctBD_YfIAFa0", second.jwtClaimsSet.getStringClaim("ath"))
    }
}
