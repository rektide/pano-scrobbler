package com.arn.scrobble

import com.arn.scrobble.api.atproto.*
import com.arn.scrobble.api.lastfm.ScrobbleData
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.SocketException
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.test.*

class AtProtocolTest {
    @Test fun callbackRejectsTampering() {
        val raw = "$AT_CALLBACK?code=test&state=opaque&iss=https%3A%2F%2Fauth.example.com"
        assertEquals("https://auth.example.com", parseAtCallback(raw)["iss"])
        for (bad in listOf("$raw&s%74ate=other", "$raw&code=other", "$raw&iss=invalid", "$raw#x", raw.replace(":/", "://"))) {
            assertEquals(AtProblem.CALLBACK, assertFailsWith<AtFailure> { parseAtCallback(bad) }.problem)
        }
    }

    @Test fun pdsIdentityAndPartialGrants() {
        val doc = Json.parseToJsonElement("""{"id":"$DID","alsoKnownAs":["at://music.example.com"],"service":[{"id":"#atproto_pds","type":"AtprotoPersonalDataServer","serviceEndpoint":"https://pds.example.com"}]}""").jsonObject
        assertEquals("https://pds.example.com/", validateDocument(doc, DID, "music.example.com").pds)
        assertFailsWith<AtFailure> { validateDocument(doc, DID, "someone.example.com") }
        val request = AtSettings(completed = AtTarget.entries.toSet(), delete = setOf(AtTarget.TEAL)).requested()
        val grant = parseScope("atproto repo:fm.teal.feed.play?action=create", request)
        assertEquals(setOf("create"), grant[AtTarget.TEAL.play])
        assertEquals(emptySet(), grant[AtTarget.ROCKSKY.play])
    }

    @Test fun frozenShapesAndDistinctTidInSameMillisecond() {
        val ids = AtTid { NOW }
        val a = ids.next(); val b = ids.next()
        assertTrue(a < b && a.matches(Regex("[234567a-j][234567a-z]{12}")))
        val teal = listen.playRecord(AtTarget.TEAL)
        val rock = listen.playRecord(AtTarget.ROCKSKY)
        assertEquals(182L, teal.number("duration"))
        assertEquals(182_320L, rock.number("duration"))
        assertEquals("曲", teal.text("trackName"))
        assertEquals(NOW, instantMs(rock.text("createdAt")))
        assertEquals(AtProblem.INPUT, assertFailsWith<AtFailure> { listen.copy(albumArtist = null).playRecord(AtTarget.ROCKSKY) }.problem)
        assertEquals(182_320L, listen.copy(album = null).statusRecord(AtTarget.ROCKSKY, NOW, NOW + 100_000).obj("track")?.number("durationMs"))
        assertNull(validLive(listen.statusRecord(AtTarget.TEAL, NOW, NOW + 100_000), AtTarget.TEAL, NOW + 100_001))
    }

    @Test fun opaqueCursorSparsePagesAndRefresh() = runBlocking {
        val hits = mutableListOf<String>()
        val pager = AtPageWalk("start", Int::toString) { key ->
            hits += key
            when (key) {
                "start" -> AtReadPage((1..50).toList(), "unpredictable-token-1")
                "unpredictable-token-1" -> AtReadPage((50..100).toList(), "unpredictable-token-2")
                "unpredictable-token-2" -> AtReadPage((101..107).toList(), null)
                else -> error("invented cursor")
            }
        }
        assertEquals((101..107).toList(), pager.page(3).values)
        assertEquals(50, pager.page(2).values.size)
        assertEquals(3, hits.size)
        assertEquals(50, pager.page(1, refresh = true).values.size)
        val repeated = AtPageWalk("start", Int::toString) { AtReadPage(emptyList(), "start") }
        assertFailsWith<AtFailure> { repeated.page(1) }
        val sparse = AtPageWalk("start", Int::toString) { AtReadPage(emptyList(), "next") }
        assertEquals("next", sparse.page(1).next)
    }

    @Test fun oneShotNoRedirectOrStatusReissue() = runBlocking {
        for (code in listOf(302, 307, 401, 408, 503)) {
            Loopback { Reply(code, "{}", "Location: /elsewhere\r\nRetry-After: 0\r\n") }.use { server ->
                val response = server.wire().post("${server.url}/credential".toHttpUrl(), "test credential".toRequestBody(),
                    mapOf("Authorization" to "Bearer ordinary-fixture-token"))
                assertEquals(code, response.code)
                assertEquals(listOf("/credential"), server.requests.map { it.path })
            }
        }
    }

    @Test fun proxyAuthenticationCannotReceiveCredentialBody() = runBlocking {
        Loopback { Reply(407, "{}", "Proxy-Authenticate: Basic realm=fixture\r\n") }.use { server ->
            val proxy = Proxy(Proxy.Type.HTTP, java.net.InetSocketAddress("127.0.0.1", server.url.toHttpUrl().port))
            val wire = AtWire { OkHttpClient.Builder().proxy(proxy) }
            assertFailsWith<java.io.IOException> {
                wire.post("https://pds.example.com/credential".toHttpUrl(), "ordinary-fixture-password".toRequestBody(),
                    mapOf("Authorization" to "Bearer ordinary-fixture-token"))
            }
            assertEquals(1, server.requests.size)
            assertEquals("pds.example.com:443", server.requests.single().path)
            assertTrue(server.requests.single().body.isEmpty())
            assertTrue(server.requests.single().headers.none { it.contains("ordinary-fixture") })
        }
    }

    @Test fun coldVaultAndConcurrentAppPasswordRefresh() = runBlocking {
        val root = Files.createTempDirectory("at-authority-fixture").toFile()
        try {
            val files = DiskFixture(root)
            var now = NOW
            Loopback { request ->
                when (request.path) {
                    "/xrpc/com.atproto.server.createSession" -> Reply(200, passwordTokens("first"))
                    "/xrpc/com.atproto.server.refreshSession" -> Reply(200, passwordTokens("second"))
                    else -> Reply(200, "{}")
                }
            }.use { server ->
                val wire = server.wire(rewrite = true)
                fun authority() = AtAuthority(AtVault { files }, wire, wire, { AtIdentity(DID, "https://pds.example.com/") }, { now }, {})
                val first = authority()
                first.appPassword(DID, "test-test-test-test", AtSettings(completed = AtTarget.entries.toSet()), false)
                val restarted = authority()
                assertEquals(DID, restarted.account().identity?.did)
                now += 2_000_000
                AtTarget.entries.map { target -> async {
                    val address = AtAddress(DID, target.play, "fixture")
                    restarted.write(DID, target.play, AtMutation.CREATE, address.body { put("record", listen.playRecord(target)) })
                } }.awaitAll()
                assertEquals(1, server.requests.count { it.path.endsWith("refreshSession") })
                assertEquals(2, server.requests.count { it.path.endsWith("createRecord") })
                assertTrue(server.requests.filter { it.path.endsWith("createRecord") }.all { req ->
                    req.headers.any { it.equals("Authorization: Bearer second-access", true) }
                })
                val persisted = Json.decodeFromString<AtVaultState>(files.read()!!.decodeToString())
                files.write(Json.encodeToString(persisted.copy(session = persisted.session!!.copy(claimed = true))).encodeToByteArray())
                assertNull(authority().account().method)
                assertEquals(AtProblem.RECONNECT, assertFailsWith<AtFailure> {
                    val addr = AtAddress(DID, AtTarget.TEAL.play, "fixture")
                    authority().write(DID, addr.collection, AtMutation.CREATE, addr.body())
                }.problem)
                assertEquals(1, server.requests.count { it.path.endsWith("refreshSession") })
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun oauthParNonceRestartSingleUseAndProofBoundPartialGrant() = runBlocking {
        val root = Files.createTempDirectory("at-oauth-fixture").toFile()
        try {
            val files = DiskFixture(root)
            var approvedState: String? = null
            var challenge: String? = null
            val proofs = mutableListOf<String>()
            val requested = AtSettings(completed = AtTarget.entries.toSet())
            Loopback { req ->
                fun proof(): SignedJWT = SignedJWT.parse(req.headers.single { it.startsWith("DPoP:", true) }.substringAfter(':').trim()).also {
                    check(it.verify(ECDSAVerifier(it.header.jwk.toECKey())))
                    check(it.header.type.type == "dpop+jwt" && !it.header.jwk.isPrivate)
                    check(it.jwtClaimsSet.getStringClaim("htm") == "POST")
                    check(it.jwtClaimsSet.jwtid !in proofs)
                    proofs += it.jwtClaimsSet.jwtid
                }
                val params = "http://fixture.invalid/?${req.body}".toHttpUrl()
                when (req.path) {
                    "/pano-scrobbler/oauth-client-metadata.json" -> Reply(200, """{"client_id":"$AT_CLIENT","redirect_uris":["$AT_CALLBACK"],"application_type":"native","dpop_bound_access_tokens":true,"token_endpoint_auth_method":"none","response_types":["code"],"grant_types":["authorization_code","refresh_token"],"scope":"${scope(requested.requested())}"}""")
                    "/.well-known/oauth-protected-resource" -> Reply(200, """{"resource":"https://pds.example.com","authorization_servers":["https://as.example.com"]}""")
                    "/.well-known/oauth-authorization-server" -> Reply(200, """{"issuer":"https://as.example.com","pushed_authorization_request_endpoint":"https://as.example.com/par","authorization_endpoint":"https://as.example.com/authorize","token_endpoint":"https://as.example.com/token","require_pushed_authorization_requests":true,"authorization_response_iss_parameter_supported":true,"client_id_metadata_document_supported":true,"scopes_supported":["atproto"],"token_endpoint_auth_methods_supported":["none"],"dpop_signing_alg_values_supported":["ES256"],"code_challenge_methods_supported":["S256"],"response_types_supported":["code"],"grant_types_supported":["authorization_code","refresh_token"]}""")
                    "/par" -> {
                        val jwt = proof()
                        check(jwt.jwtClaimsSet.getStringClaim("htu") == "https://as.example.com/par")
                        if (jwt.jwtClaimsSet.getStringClaim("nonce") != "as-nonce") Reply(400, """{"error":"use_dpop_nonce"}""", "DPoP-Nonce: as-nonce\r\n")
                        else {
                            approvedState = params.queryParameter("state")
                            challenge = params.queryParameter("code_challenge")
                            Reply(201, """{"request_uri":"urn:ietf:params:oauth:request_uri:fixture","expires_in":90}""", "DPoP-Nonce: as-nonce\r\n")
                        }
                    }
                    "/token" -> {
                        val jwt = proof()
                        check(jwt.jwtClaimsSet.getStringClaim("nonce") == "as-nonce")
                        check(jwt.jwtClaimsSet.getStringClaim("ath") == null)
                        check(atHash(params.queryParameter("code_verifier")!!) == challenge)
                        Reply(200, """{"sub":"$DID","token_type":"DPoP","access_token":"fixture-access","refresh_token":"fixture-refresh","expires_in":600,"scope":"atproto repo:fm.teal.feed.play?action=create"}""", "DPoP-Nonce: as-next\r\n")
                    }
                    "/xrpc/com.atproto.repo.createRecord" -> {
                        val jwt = proof()
                        check(jwt.jwtClaimsSet.getStringClaim("ath") == atHash("fixture-access"))
                        check(req.headers.any { it.equals("Authorization: DPoP fixture-access", true) })
                        if (jwt.jwtClaimsSet.getStringClaim("nonce") == "resource-nonce") Reply(200, "{}", "DPoP-Nonce: resource-nonce\r\n")
                        else Reply(401, """{"error":"use_dpop_nonce"}""", "DPoP-Nonce: resource-nonce\r\n")
                    }
                    else -> Reply(404, "{}")
                }
            }.use { server ->
                val wire = server.wire(true)
                fun authority() = AtAuthority(AtVault { files }, wire, wire, { AtIdentity(DID, "https://pds.example.com/") }, { NOW }, {})
                val browser = authority().beginBrowser(DID, requested, false)
                assertEquals("as.example.com", browser.toHttpUrl().host)
                val callback = "$AT_CALLBACK?code=fixture&state=$approvedState&iss=https%3A%2F%2Fas.example.com"
                authority().finishBrowser(callback)
                assertFailsWith<AtFailure> { authority().finishBrowser(callback) }
                val a = AtAddress(DID, AtTarget.TEAL.play, "fixture")
                assertEquals(200, authority().write(DID, a.collection, AtMutation.CREATE, a.body()).code)
                val b = AtAddress(DID, AtTarget.ROCKSKY.play, "fixture")
                assertEquals(AtProblem.PERMISSION, assertFailsWith<AtFailure> { authority().write(DID, b.collection, AtMutation.CREATE, b.body()) }.problem)
                assertEquals(2, server.requests.count { it.path == "/par" })
                assertEquals(1, server.requests.count { it.path == "/token" })
                assertEquals(2, server.requests.count { it.path.endsWith("createRecord") })
            }
        } finally { root.deleteRecursively() }
    }

    private class DiskFixture(dir: File) : AtVaultFiles {
        override val lockFile = File(dir, "lock")
        private val state = File(dir, "state")
        override fun read() = state.takeIf(File::exists)?.readBytes()
        override fun write(bytes: ByteArray) = state.writeBytes(bytes)
    }

    private data class Request(val path: String, val headers: List<String>, val body: String)
    private data class Reply(val status: Int, val body: String, val headers: String = "")
    private class Loopback(handler: (Request) -> Reply) : AutoCloseable {
        private val server = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
        val url = "http://127.0.0.1:${server.localPort}"
        val requests = CopyOnWriteArrayList<Request>()
        private val serving = thread(isDaemon = true) {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: SocketException) { break }
                socket.use {
                    val input = it.getInputStream().buffered()
                    fun line(): String? {
                        val bytes = java.io.ByteArrayOutputStream()
                        while (true) {
                            val b = input.read()
                            if (b == -1) return null
                            if (b == 10) return bytes.toString(Charsets.UTF_8.name()).trimEnd('\r')
                            bytes.write(b)
                        }
                    }
                    val requestLine = line() ?: return@use
                    val headers = generateSequence(::line).takeWhile(String::isNotEmpty).toList()
                    val length = headers.firstOrNull { line -> line.startsWith("Content-Length:", true) }?.substringAfter(':')?.trim()?.toInt() ?: 0
                    val body = ByteArray(length)
                    var read = 0
                    while (read < length) { val n = input.read(body, read, length - read); if (n < 0) break; read += n }
                    val request = Request(requestLine.split(' ')[1], headers, body.decodeToString())
                    requests += request
                    val reply = handler(request)
                    val bytes = reply.body.encodeToByteArray()
                    it.getOutputStream().apply {
                        write("HTTP/1.1 ${reply.status} Test\r\nContent-Length: ${bytes.size}\r\nContent-Type: application/json\r\nConnection: close\r\n${reply.headers}\r\n".encodeToByteArray())
                        write(bytes); flush()
                    }
                }
            }
        }

        fun wire(rewrite: Boolean = false) = AtWire {
            OkHttpClient.Builder().proxy(Proxy.NO_PROXY).apply {
                if (rewrite) addInterceptor { chain ->
                    val request = chain.request()
                    val local = url.toHttpUrl().newBuilder().encodedPath(request.url.encodedPath).encodedQuery(request.url.encodedQuery).build()
                    chain.proceed(request.newBuilder().url(local).build())
                }
            }
        }
        override fun close() { server.close(); serving.join(1000) }
    }

    companion object {
        private const val DID = "did:plc:aaaaaaaaaaaaaaaaaaaaaaaa"
        private const val NOW = 1_700_000_000_000L
        private val listen = ScrobbleData("Artist", "曲", "Album", NOW, albumArtist = "Album Artist", duration = 182_320, appId = null)
        private fun passwordTokens(prefix: String) = buildJsonObject {
            put("did", DID); put("accessJwt", "$prefix-access"); put("refreshJwt", "$prefix-refresh")
        }.toString()
    }
}
