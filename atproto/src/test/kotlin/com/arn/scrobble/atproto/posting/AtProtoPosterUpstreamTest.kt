package com.arn.scrobble.atproto.posting

import io.github.kikin81.atproto.runtime.XrpcClient
import io.github.kikin81.atproto.runtime.XrpcError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AtProtoPosterUpstreamTest {
    @Test
    fun `fork-backed poster creates both Teal and Rocksky records`() = runTest {
        val requests = mutableListOf<JsonObject>()
        val poster = poster { request ->
            val body = request.bodyJson()
            requests += body
            val collection = body.getValue("collection").jsonPrimitive.content
            jsonResponse(
                """{"uri":"at://did:plc:test/$collection/3k${requests.size}","cid":"bafy${requests.size}"}""",
            )
        }

        val result = poster.post("did:plc:test", play())

        assertTrue(result.isSuccess)
        assertEquals(setOf(AtProtoTarget.TEAL, AtProtoTarget.ROCKSKY), result.created.keys)
        assertEquals(listOf("fm.teal.feed.play", "app.rocksky.scrobble"), requests.map { it.collection() })
        assertEquals("A track", requests[0].record()["trackName"]?.jsonPrimitive?.content)
        assertEquals("A track", requests[1].record()["title"]?.jsonPrimitive?.content)
    }

    @Test
    fun `fork-backed poster preserves partial success when one XRPC write fails`() = runTest {
        val poster = poster { request ->
            val collection = request.bodyJson().collection()
            if (collection == AtProtoTarget.TEAL.collection) {
                jsonResponse(
                    """{"error":"InvalidRequest","message":"Teal rejected"}""",
                    HttpStatusCode.BadRequest,
                )
            } else {
                jsonResponse(
                    """{"uri":"at://did:plc:test/$collection/3kok","cid":"bafyok"}""",
                )
            }
        }

        val result = poster.post("did:plc:test", play())

        assertEquals(setOf(AtProtoTarget.ROCKSKY), result.created.keys)
        assertEquals(setOf(AtProtoTarget.TEAL), result.failures.keys)
        val failure = assertIs<XrpcError.Unknown>(result.failures.getValue(AtProtoTarget.TEAL))
        assertEquals("InvalidRequest", failure.errorName)
        assertEquals("Teal rejected", failure.errorMessage)
    }

    @Test
    fun `fork-backed poster honors targets derived from reduced granted scope`() = runTest {
        val collections = mutableListOf<String>()
        val scope = "atproto repo:app.rocksky.scrobble?action=create"
        val targets = AtProtoTarget.grantedBy(scope)
        val poster = poster(
            config = AtProtoPostingConfig(targets = targets),
        ) { request ->
            val collection = request.bodyJson().collection()
            collections += collection
            jsonResponse(
                """{"uri":"at://did:plc:test/$collection/3konly","cid":"bafyonly"}""",
            )
        }

        val result = poster.post("did:plc:test", play())

        assertTrue(result.isSuccess)
        assertEquals(setOf(AtProtoTarget.ROCKSKY), result.created.keys)
        assertEquals(listOf("app.rocksky.scrobble"), collections)
    }

    private fun poster(
        config: AtProtoPostingConfig = AtProtoPostingConfig(),
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData,
    ): AtProtoPoster {
        val client = XrpcClient(
            baseUrl = "https://pds.test",
            httpClient = HttpClient(MockEngine(handler)),
        )
        return AtProtoPoster(xrpcClient = client, config = config)
    }

    private fun play(): Play = Play(
        track = "A track",
        artist = "An artist",
        album = "An album",
        albumArtist = "An album artist",
        playedAtMillis = 1_700_000_000_123,
        durationMillis = 243_000,
    )

    private fun MockRequestHandleScope.jsonResponse(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ) = respond(
        content = body,
        status = status,
        headers = headersOf(HttpHeaders.ContentType, "application/json"),
    )

    private fun HttpRequestData.bodyJson(): JsonObject =
        Json.parseToJsonElement((body as TextContent).text).jsonObject

    private fun JsonObject.collection(): String = getValue("collection").jsonPrimitive.content

    private fun JsonObject.record(): JsonObject = getValue("record").jsonObject
}
