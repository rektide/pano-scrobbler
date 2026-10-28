package com.arn.scrobble.atproto.identity

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class IdentityRegressionTest {
    private val did = "did:plc:abcdefghijklmnopqrstuvwx"

    @Test
    fun `rejects path port encoded authority and reserved did web before network`() = runTest {
        val client = HttpClient(MockEngine { error("Invalid DID must not cause network access") }) { followRedirects = false }
        for (value in listOf(
            "did:web:example.com:users:alice", "did:web:example.com%3A443", "did:web:example.com:443",
            "did:web:example.com%2Fpath", "did:web:user%40example.com", "did:web:example.com?x=y",
            "did:web:example.com#key", "did:web:localhost", "did:web:localhost%3A1234", "did:web:127.0.0.1",
            "did:web:a.arpa", "did:web:a.local", "did:web:a.onion", "did:web:a.test", "did:webvh:example.com",
        )) assertFailsWith<IllegalArgumentException>(value) { IdentityResolver(client).resolve(value) }
    }

    @Test
    fun `first syntactically valid claim wins with ASCII case normalization`() = runTest {
        val aliases = listOf("https://other.example.com", "at://@wrong.example.com", "at://bad.example.com/", "at://Alice.BSKY.Social", "at://later.example.com")
        val resolver = IdentityResolver(client(document(aliases)))
        assertEquals("alice.bsky.social", resolver.resolve("ALICE.BSKY.SOCIAL").handle)
        assertEquals("alice.bsky.social", resolver.resolve(did).handle)
    }

    @Test
    fun `a later alias cannot back verify a noncanonical requested handle`() = runTest {
        val resolver = IdentityResolver(client(document(listOf("at://first.example.com", "at://alice.bsky.social"))))
        assertFailsWith<IllegalArgumentException> { resolver.resolve("alice.bsky.social") }
    }

    @Test
    fun `failed first claim does not trigger later handle resolution`() = runTest {
        val lookups = mutableListOf<String>()
        val resolver = IdentityResolver(client(document(listOf("at://first.example.com", "at://alice.bsky.social")), lookups) {
            if (it == "first.example.com") "did:plc:zyxwvutsrqponmlkjihgfedc" else did
        })
        assertNull(resolver.resolve(did).handle)
        assertEquals(listOf("first.example.com"), lookups)
    }

    @Test
    fun `a reserved first claim is not replaced by a later resolvable alias`() = runTest {
        val lookups = mutableListOf<String>()
        val resolver = IdentityResolver(client(document(listOf("at://first.invalid", "at://alice.bsky.social")), lookups))
        assertNull(resolver.resolve(did).handle)
        assertEquals(emptyList(), lookups)
        for (tld in listOf("alt", "arpa", "example", "internal", "invalid", "local", "localhost", "onion", "test")) {
            assertFailsWith<IllegalArgumentException>(tld) { resolver.resolve("user.$tld") }
        }
    }

    @Test
    fun `claims do not inherit UI trimming prefixes or Unicode case folding`() {
        for (claim in listOf("at:// alice.bsky.social", "at://alice.bsky.social ", "at://@alice.bsky.social", "at://K.example.com")) {
            assertNull(DidDocument(did, listOf(claim)).claimedHandle(), claim)
        }
        assertFailsWith<IllegalArgumentException> { normalizeHandle("K.example.com") }
    }

    @Test
    fun `DID document identity and first matching PDS are authoritative`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            IdentityResolver(client(document(emptyList()).replace(did, "did:plc:zyxwvutsrqponmlkjihgfedc"))).resolve(did)
        }
        val badFirst = document(emptyList(), "https://pds.example.com/path").replace(
            "}]}", "}, {\"id\":\"#atproto_pds\",\"type\":\"AtprotoPersonalDataServer\",\"serviceEndpoint\":\"https://good.example.com\"}]}",
        )
        assertFailsWith<IllegalArgumentException> { IdentityResolver(client(badFirst)).resolve(did) }
        for (endpoint in listOf("http://pds.example.com", "https://user@pds.example.com", "https://pds.example.com?x=y", "https://pds.example.com#x", "https://pds.example.com:", "https://pds.example.com:65536")) {
            assertFailsWith<IllegalArgumentException>(endpoint) { IdentityResolver(client(document(emptyList(), endpoint))).resolve(did) }
        }
    }

    private fun document(aliases: List<String>, endpoint: String = "https://pds.example.com") =
        """{"id":"$did","alsoKnownAs":[${aliases.joinToString { "\"$it\"" }}],"service":[{"id":"$did#atproto_pds","type":"AtprotoPersonalDataServer","serviceEndpoint":"$endpoint"}]}"""

    private fun client(
        document: String,
        lookups: MutableList<String> = mutableListOf(),
        handleDid: (String) -> String = { did },
    ) = HttpClient(MockEngine { request ->
        val body = if (request.url.host == "public.api.bsky.app") {
            val handle = request.url.parameters["handle"]!!
            lookups += handle
            """{"did":"${handleDid(handle)}"}"""
        } else document
        respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
    }) { followRedirects = false }
}
