package com.arn.scrobble.atproto.identity

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IdentityResolverTest {
    @Test
    fun `resolves and bidirectionally verifies a handle`() = runTest {
        val did = "did:plc:abcdefghijklmnopqrstuvwxyz24".take(32)
        val client = HttpClient(MockEngine { request ->
            when (request.url.host) {
                "public.api.bsky.app" -> respondJson("{\"did\":\"$did\"}")
                "plc.directory" -> respondJson(didDocument(did, "alice.bsky.social"), didContentType = true)
                else -> error("Unexpected URL ${request.url}")
            }
        })

        val identity = IdentityResolver(client).resolve("Alice.Bsky.Social")

        assertEquals(did, identity.did)
        assertEquals("alice.bsky.social", identity.handle)
        assertEquals("https://pds.example.com", identity.pds)
    }

    @Test
    fun `rejects a handle not linked from the DID document`() = runTest {
        val did = "did:plc:abcdefghijklmnopqrstuvwxyz24".take(32)
        val client = HttpClient(MockEngine { request ->
            when (request.url.host) {
                "public.api.bsky.app" -> respondJson("{\"did\":\"$did\"}")
                "plc.directory" -> respondJson(didDocument(did, "mallory.example"), didContentType = true)
                else -> error("Unexpected URL ${request.url}")
            }
        })

        assertFailsWith<IllegalArgumentException> {
            IdentityResolver(client).resolve("alice.bsky.social")
        }
    }

    @Test
    fun `maps path-based did web identifiers to did json`() = runTest {
        var requestedPath = ""
        val did = "did:web:example.com:users:alice"
        val client = HttpClient(MockEngine { request ->
            if (request.url.host == "example.com") {
                requestedPath = request.url.encodedPath
                respondJson(didDocument(did, "alice.example.com"), didContentType = true)
            } else {
                respondJson("{\"did\":\"$did\"}")
            }
        })

        val identity = IdentityResolver(client).resolve(did)

        assertEquals("/users/alice/did.json", requestedPath)
        assertEquals(did, identity.did)
    }

    private fun didDocument(did: String, handle: String) = """
        {
          "id":"$did",
          "alsoKnownAs":["at://$handle"],
          "service":[{
            "id":"#atproto_pds",
            "type":"AtprotoPersonalDataServer",
            "serviceEndpoint":"https://pds.example.com"
          }]
        }
    """.trimIndent()

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.respondJson(
        body: String,
        didContentType: Boolean = false,
    ) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(
            HttpHeaders.ContentType,
            if (didContentType) "application/did+ld+json" else "application/json",
        ),
    )
}
