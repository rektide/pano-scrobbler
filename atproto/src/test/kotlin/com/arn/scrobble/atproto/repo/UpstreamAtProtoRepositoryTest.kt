package com.arn.scrobble.atproto.repo

import com.arn.scrobble.atproto.posting.Play
import com.arn.scrobble.atproto.posting.RockskyScrobbleRecord
import com.arn.scrobble.atproto.posting.TealPlayRecord
import com.arn.scrobble.atproto.posting.toRockskyRecord
import com.arn.scrobble.atproto.posting.toTealRecord
import io.github.kikin81.atproto.runtime.AuthProvider
import io.github.kikin81.atproto.runtime.AuthResponse
import io.github.kikin81.atproto.runtime.AuthResponseAction
import io.github.kikin81.atproto.runtime.XrpcClient
import io.github.kikin81.atproto.runtime.XrpcError
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UpstreamAtProtoRepositoryTest {
    private val json = Json { explicitNulls = false }

    @Test
    fun `sends the exact createRecord body and returns typed strong reference fields`() = runTest {
        val auth = ObservingAuth()
        lateinit var request: HttpRequestData
        val repository = repository(auth) { captured ->
            request = captured
            jsonResponse(
                body = """{"uri":"at://did:plc:test/fm.teal.feed.play/3kexample","cid":"bafyexample"}""",
                nonce = "success-nonce",
            )
        }
        val record = buildJsonObject {
            put("\$type", "fm.teal.feed.play")
            put("trackName", "A track")
        }

        val created = repository.createRecord(
            did = "did:plc:test",
            collection = "fm.teal.feed.play",
            record = record,
        )

        assertEquals("at://did:plc:test/fm.teal.feed.play/3kexample", created.uri)
        assertEquals("bafyexample", created.cid)
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/xrpc/com.atproto.repo.createRecord", request.url.encodedPath)
        assertEquals("", request.url.encodedQuery)
        assertEquals("application/json", request.body.contentType?.withoutParameters().toString())
        assertNull(request.headers["atproto-proxy"])
        assertEquals("DPoP upstream-token", request.headers[HttpHeaders.Authorization])
        assertEquals("proof-1", request.headers["DPoP"])

        val body = request.bodyJson()
        assertEquals(setOf("repo", "collection", "record"), body.keys)
        assertEquals("did:plc:test", body["repo"]?.jsonPrimitive?.content)
        assertEquals("fm.teal.feed.play", body["collection"]?.jsonPrimitive?.content)
        assertEquals(record, body["record"])
        assertFalse("rkey" in body)
        assertFalse("validate" in body)
        assertFalse("swapCommit" in body)
        assertEquals(listOf(200), auth.responses.map { it.status })
        assertEquals("success-nonce", auth.responses.single().header("dpop-nonce"))
    }

    @Test
    fun `preserves upstream XRPC errors without repository wrapping`() = runTest {
        val auth = ObservingAuth()
        val repository = repository(auth) {
            jsonResponse(
                body = """{"error":"InvalidRequest","message":"record rejected"}""",
                status = HttpStatusCode.BadRequest,
                nonce = "error-nonce",
            )
        }

        val failure = assertFailsWith<XrpcError.Unknown> {
            repository.createRecord(
                did = "did:plc:test",
                collection = "app.rocksky.scrobble",
                record = buildJsonObject { put("\$type", "app.rocksky.scrobble") },
            )
        }

        assertEquals("InvalidRequest", failure.errorName)
        assertEquals("record rejected", failure.errorMessage)
        assertEquals(400, failure.status)
        assertEquals("error-nonce", auth.responses.single().header("DPoP-Nonce"))
    }

    @Test
    fun `passes Pano Teal and Rocksky JsonObject records without generated models`() = runTest {
        val capturedRecords = mutableListOf<JsonObject>()
        val repository = repository(ObservingAuth()) { request ->
            val body = request.bodyJson()
            capturedRecords += body.getValue("record").jsonObject
            val collection = body.getValue("collection").jsonPrimitive.content
            jsonResponse(
                body = """{"uri":"at://did:plc:test/$collection/3kexample","cid":"bafy-${capturedRecords.size}"}""",
                nonce = "nonce-${capturedRecords.size}",
            )
        }
        val play = Play(
            track = "A track",
            artist = "An artist",
            album = "An album",
            albumArtist = "An album artist",
            playedAtMillis = 1_700_000_000_123,
            durationMillis = 243_000,
        )
        val teal = json.encodeToJsonElement(
            TealPlayRecord.serializer(),
            play.toTealRecord("com.arn.scrobble/test"),
        ).jsonObject
        val rocksky = json.encodeToJsonElement(
            RockskyScrobbleRecord.serializer(),
            play.toRockskyRecord(),
        ).jsonObject

        repository.createRecord("did:plc:test", "fm.teal.feed.play", teal)
        repository.createRecord("did:plc:test", "app.rocksky.scrobble", rocksky)

        assertEquals(listOf(teal, rocksky), capturedRecords)
        assertEquals("A track", capturedRecords[0]["trackName"]?.jsonPrimitive?.content)
        assertEquals("A track", capturedRecords[1]["title"]?.jsonPrimitive?.content)
    }

    @Test
    fun `delegates response observation and one fresh-auth retry to XrpcClient`() = runTest {
        val auth = ObservingAuth(retryUnauthorized = true)
        val bodies = mutableListOf<JsonObject>()
        var calls = 0
        val repository = repository(auth) { request ->
            calls++
            bodies += request.bodyJson()
            if (calls == 1) {
                jsonResponse(
                    body = """{"error":"use_dpop_nonce"}""",
                    status = HttpStatusCode.Unauthorized,
                    nonce = "challenge-nonce",
                    wwwAuthenticate = """DPoP error="use_dpop_nonce"""",
                )
            } else {
                jsonResponse(
                    body = """{"uri":"at://did:plc:test/fm.teal.feed.play/3kretry","cid":"bafyretry"}""",
                    nonce = "retry-success-nonce",
                )
            }
        }
        val record = buildJsonObject {
            put("\$type", "fm.teal.feed.play")
            put("trackName", "Retry track")
        }

        val created = repository.createRecord("did:plc:test", "fm.teal.feed.play", record)

        assertEquals("bafyretry", created.cid)
        assertEquals(2, calls)
        assertEquals(bodies[0], bodies[1])
        assertEquals(listOf("proof-1", "proof-2"), auth.proofs)
        assertEquals(listOf(401, 200), auth.responses.map { it.status })
        assertEquals("challenge-nonce", auth.responses[0].header("DPoP-Nonce"))
        assertEquals("retry-success-nonce", auth.responses[1].header("DPoP-Nonce"))
    }

    @Test
    fun `rejects mismatched record type before transport`() = runTest {
        var calls = 0
        val repository = repository(ObservingAuth()) {
            calls++
            error("transport must not be called")
        }

        assertFailsWith<IllegalArgumentException> {
            repository.createRecord(
                did = "did:plc:test",
                collection = "fm.teal.feed.play",
                record = buildJsonObject { put("\$type", "app.rocksky.scrobble") },
            )
        }
        assertEquals(0, calls)
    }

    private fun repository(
        auth: AuthProvider,
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData,
    ): UpstreamAtProtoRepository {
        val httpClient = HttpClient(MockEngine(handler))
        val xrpcClient = XrpcClient(
            baseUrl = "https://pds.example.com",
            httpClient = httpClient,
            authProvider = auth,
        )
        return UpstreamAtProtoRepository(xrpcClient)
    }

    private fun MockRequestHandleScope.jsonResponse(
        body: String,
        status: HttpStatusCode = HttpStatusCode.OK,
        nonce: String,
        wwwAuthenticate: String? = null,
    ) = respond(
        content = ByteReadChannel(body),
        status = status,
        headers = headersOf(
            *buildList {
                add(HttpHeaders.ContentType to listOf("application/json"))
                add("DPoP-Nonce" to listOf(nonce))
                if (wwwAuthenticate != null) add(HttpHeaders.WWWAuthenticate to listOf(wwwAuthenticate))
            }.toTypedArray(),
        ),
    )

    private fun HttpRequestData.bodyJson(): JsonObject {
        val content = body as? TextContent ?: error("Unexpected body ${body::class}")
        return Json.parseToJsonElement(content.text).jsonObject
    }
}

private class ObservingAuth(
    private val retryUnauthorized: Boolean = false,
) : AuthProvider {
    private var requestCount = 0
    val proofs = mutableListOf<String>()
    val responses = mutableListOf<AuthResponse>()

    override suspend fun authHeaders(method: String, url: String): Map<String, String> {
        requestCount++
        val proof = "proof-$requestCount"
        proofs += proof
        return mapOf(
            HttpHeaders.Authorization to "DPoP upstream-token",
            "DPoP" to proof,
        )
    }

    override suspend fun observeResponse(response: AuthResponse): AuthResponseAction {
        responses += response
        return if (retryUnauthorized && response.status == 401) {
            AuthResponseAction.RetryWithFreshAuth
        } else {
            AuthResponseAction.Continue
        }
    }
}
