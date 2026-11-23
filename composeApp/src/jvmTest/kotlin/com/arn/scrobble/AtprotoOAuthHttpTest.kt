package com.arn.scrobble

import com.arn.scrobble.api.atproto.ATPROTO_CLIENT_ID
import com.arn.scrobble.api.atproto.ATPROTO_REDIRECT_URI
import com.arn.scrobble.api.atproto.AtprotoIdentity
import com.arn.scrobble.api.atproto.AtprotoOAuth
import com.arn.scrobble.api.atproto.AtprotoOAuthAttempts
import com.arn.scrobble.api.atproto.AtprotoOAuthAttempt
import com.arn.scrobble.api.atproto.AtprotoOAuthPermission
import com.arn.scrobble.api.atproto.AtprotoOAuthSession
import com.arn.scrobble.api.atproto.createPlay
import com.arn.scrobble.api.atproto.discoverAtprotoOAuth
import com.arn.scrobble.api.atproto.rotateOAuth
import com.arn.scrobble.api.lastfm.ApiException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.util.Base64URL
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.http.formUrlEncode
import io.ktor.http.parseQueryString
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import java.net.InetAddress
import java.net.InetSocketAddress
import kotlin.io.path.createTempDirectory
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AtprotoOAuthHttpTest {
    @Test
    fun rotatesAndSignsBothRepositoryWritesWithResourceNonceAndAth() = runBlocking {
        Fixture().use { fixture ->
            val original = fixture.start(setOf(AtprotoOAuthPermission.TEAL_SCROBBLES, AtprotoOAuthPermission.ROCKSKY_SCROBBLES))
                .complete(fixture.callback())
            val rotated = fixture.rotate(original)
            assertEquals("refresh_token", fixture.posts("/token").last().form["grant_type"])
            assertEquals("fixture-refresh", fixture.posts("/token").last().form["refresh_token"])
            val collections = listOf("fm.teal.feed.play", "app.rocksky.scrobble")
            collections.forEach { collection ->
                assertEquals("at://${fixture.identity.did}/$collection/3jzfcijpj2z2a", fixture.write(rotated, collection))
            }
            val requests = fixture.posts("/xrpc/com.atproto.repo.createRecord")
            assertEquals(listOf(null, "pds-challenge", "pds-success"), requests.map { it.proof.jwtClaimsSet.getStringClaim("nonce") })
            assertEquals(3, requests.map { it.proof.jwtClaimsSet.jwtid }.toSet().size)
            requests.forEach { request ->
                assertEquals("DPoP fixture-access", request.authorization)
                assertEquals("${fixture.pds}/xrpc/com.atproto.repo.createRecord", request.proof.jwtClaimsSet.getStringClaim("htu"))
                assertEquals(Base64URL.encode(MessageDigest.getInstance("SHA-256").digest("fixture-access".encodeToByteArray())).toString(),
                    request.proof.jwtClaimsSet.getStringClaim("ath"))
                assertTrue(request.proof.verify(ECDSAVerifier(request.proof.header.jwk as ECKey)))
            }
        }
    }

    @Test
    fun startsFromAHandleVerifiedByItsDidDocument() = runBlocking {
        Fixture().use { fixture ->
            val pending = fixture.startFromHandle(" @Alice.Example ")
            val resolved = fixture.requests.first()
            assertEquals("/xrpc/com.atproto.identity.resolveHandle", resolved.path)
            assertEquals("handle=alice.example", resolved.query)
            assertEquals("/${fixture.identity.did}", fixture.requests[1].path)
            assertEquals("alice.example", fixture.posts("/par").single().form["login_hint"])
            assertEquals(fixture.identity.did, pending.complete(fixture.callback()).did)
        }
    }

    @Test
    fun resumesAfterReopeningPendingFileAndConsumesOnlyMatchingCallback() = runBlocking {
        val directory = createTempDirectory().toFile()
        val file = directory.resolve("pending.json")
        try {
            Fixture().use { fixture ->
                val first = fixture.start(setOf(AtprotoOAuthPermission.ROCKSKY_SCROBBLES))
                val originalKey = fixture.posts("/par").single().proof.header.jwk
                val oldJob = Job()
                try {
                    AtprotoOAuthAttempts(file, CoroutineScope(Dispatchers.IO + oldJob)).save(first.attempt)
                } finally {
                    oldJob.cancelAndJoin()
                }
                val newJob = Job()
                try {
                    val store = AtprotoOAuthAttempts(file, CoroutineScope(Dispatchers.IO + newJob))
                    val attempt = requireNotNull(store.load())
                    assertEquals(first.authorizationUrl, attempt.authorizationUrl)
                    assertEquals("alice.example", attempt.handle)
                    assertEquals(setOf(AtprotoOAuthPermission.ROCKSKY_SCROBBLES), attempt.permissions)
                    assertFailsWith<ApiException> {
                        fixture.resume(attempt).complete(fixture.callback(iss = fixture.pds)) { store.consume(attempt.state) }
                    }
                    assertEquals(attempt.state, store.load()?.state)
                    assertTrue(fixture.posts("/token").isEmpty())
                    fixture.challengeToken = true
                    val callback = fixture.callback()
                    val session = fixture.resume(attempt).complete(callback) { store.consume(attempt.state) }
                    assertEquals(fixture.identity.did, session.did)
                    assertNull(store.load())
                    assertTrue(fixture.posts("/token").all { it.proof.header.jwk == originalKey })
                    assertFailsWith<ApiException> {
                        fixture.resume(attempt).complete(callback) { store.consume(attempt.state) }
                    }
                    assertEquals(2, fixture.posts("/token").size)
                    assertTrue("controlled-code" !in file.readText())
                } finally {
                    newJob.cancelAndJoin()
                }
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun authenticatesWithScopedParPkceAndRotatingDpopNonce() = runBlocking {
        Fixture().use { fixture ->
            fixture.challengePar = true
            fixture.challengeToken = true
            val pending = fixture.start(setOf(AtprotoOAuthPermission.TEAL_SCROBBLES))
            val authorize = pending.authorizationUrl.toHttpUrl()
            assertEquals("${fixture.issuer}/authorize", authorize.newBuilder().query(null).build().toString())
            assertEquals(setOf("client_id", "request_uri"), authorize.queryParameterNames)
            assertEquals(ATPROTO_CLIENT_ID, authorize.queryParameter("client_id"))
            assertEquals("urn:ietf:params:oauth:request_uri:test", authorize.queryParameter("request_uri"))

            val callback = fixture.callback()
            val results = List(2) { async { runCatching { pending.complete(callback) } } }.map { it.await() }
            val session = results.single { it.isSuccess }.getOrThrow()
            assertTrue(results.single { it.isFailure }.exceptionOrNull() is ApiException)
            assertEquals(fixture.identity.did, session.did)
            assertEquals(fixture.issuer, session.issuer)
            assertEquals(fixture.pds, session.pds)
            assertEquals(setOf("atproto", "repo:fm.teal.feed.play?action=create"), session.scopes)
            assertEquals("fixture-access", session.accessToken)
            assertEquals("fixture-refresh", session.refreshToken)

            val par = fixture.posts("/par")
            val token = fixture.posts("/token")
            assertEquals(2, par.size)
            assertEquals(2, token.size)
            val form = par.first().form
            assertEquals("S256", form["code_challenge_method"])
            assertEquals(ATPROTO_REDIRECT_URI, form["redirect_uri"])
            assertEquals("atproto repo:fm.teal.feed.play?action=create", form["scope"])
            assertEquals("alice.example", form["login_hint"])
            assertEquals("authorization_code", token.first().form["grant_type"])
            assertEquals("controlled-code", token.first().form["code"])
            val verifier = requireNotNull(token.first().form["code_verifier"])
            assertTrue(verifier.length >= 43)
            assertEquals(Base64URL.encode(MessageDigest.getInstance("SHA-256").digest(verifier.encodeToByteArray())).toString(),
                form["code_challenge"])
            assertEquals(par[0].body, par[1].body)
            assertEquals(token[0].body, token[1].body)
            assertEquals(listOf(null, "par-challenge", "par-success", "token-challenge"),
                (par + token).map { it.proof.jwtClaimsSet.getStringClaim("nonce") })
            val proofs = (par + token).map { it.proof }
            val publicKey = proofs.first().header.jwk as ECKey
            assertTrue(!publicKey.isPrivate)
            assertEquals(4, proofs.map { it.jwtClaimsSet.jwtid }.toSet().size)
            for ((request, proof) in (par + token).zip(proofs)) {
                assertEquals("POST", request.method)
                assertEquals("application/x-www-form-urlencoded", request.contentType)
                assertEquals(JWSAlgorithm.ES256, proof.header.algorithm)
                assertEquals("dpop+jwt", proof.header.type.toString())
                assertEquals(publicKey, proof.header.jwk)
                assertTrue(proof.verify(ECDSAVerifier(publicKey)))
                assertEquals("POST", proof.jwtClaimsSet.getStringClaim("htm"))
                assertEquals("${fixture.issuer}${request.path}", proof.jwtClaimsSet.getStringClaim("htu"))
                assertNull(proof.jwtClaimsSet.getStringClaim("ath"))
            }
            val resourceUrl = "${fixture.pds}/xrpc/com.atproto.repo.createRecord?ignored=1".toHttpUrl()
            val resourceProof = SignedJWT.parse(session.dpop.proof("POST", resourceUrl, session.accessToken))
            assertNull(resourceProof.jwtClaimsSet.getStringClaim("nonce"))
            assertEquals(resourceUrl.newBuilder().query(null).build().toString(), resourceProof.jwtClaimsSet.getStringClaim("htu"))
            assertEquals(Base64URL.encode(MessageDigest.getInstance("SHA-256").digest("fixture-access".encodeToByteArray())).toString(),
                resourceProof.jwtClaimsSet.getStringClaim("ath"))
            assertEquals("token-success", SignedJWT.parse(session.dpop.proof("POST", "${fixture.issuer}/token".toHttpUrl()))
                .jwtClaimsSet.getStringClaim("nonce"))

            fixture.start()
            val otherProof = fixture.posts("/par").last().proof
            assertNotEquals(publicKey, otherProof.header.jwk)
            assertNull(otherProof.jwtClaimsSet.getStringClaim("nonce"))
        }
    }

    @Test
    fun rejectsWrongCallbackBindingAndDenialConsumesAttempt() = runBlocking {
        Fixture().use { fixture ->
            val pending = fixture.start()
            val callback = fixture.callback()
            for (wrong in listOf(
                callback.replace(ATPROTO_REDIRECT_URI, "io.github.rektide://pano-scrobbler/oauth/callback"),
                callback.replace("/oauth/callback", "/oauth/%63allback"),
                callback.replace(fixture.state(), "wrong-state"),
                callback.replace("state=", "STATE="),
                fixture.callback(iss = fixture.pds),
                "$callback&code=duplicate",
                "$callback&state=${fixture.state()}",
                "$callback&error=access_denied",
                "$callback#fragment",
            )) assertFailsWith<ApiException> { pending.complete(wrong) }
            val denied = fixture.callback(code = null) + "&error=access_denied&error_description=do-not-echo"
            assertEquals("AT Protocol authorization was not granted",
                assertFailsWith<ApiException> { pending.complete(denied) }.message)
            assertFailsWith<ApiException> { pending.complete(callback) }
            assertTrue(fixture.posts("/token").isEmpty())
        }
    }

    @Test
    fun refusesWrongResourceIssuerAndEndpointBeforePosting() = runBlocking {
        Fixture().use { fixture ->
            fixture.claimedResource = fixture.issuer
            assertFailsWith<ApiException> { fixture.start() }
            assertTrue(fixture.requests.none { it.path == "/.well-known/oauth-authorization-server" })
            fixture.claimedResource = fixture.pds
            fixture.claimedIssuer = fixture.pds
            assertFailsWith<ApiException> { fixture.start() }
            fixture.claimedIssuer = fixture.issuer
            fixture.tokenEndpoint = "${fixture.pds}/token"
            assertFailsWith<ApiException> { fixture.start() }
            fixture.tokenEndpoint = "${fixture.issuer}/token"
            fixture.supportsIssuer = false
            assertFailsWith<ApiException> { fixture.start() }
            assertTrue(fixture.posts("/par").isEmpty())
        }
    }

    @Test
    fun rejectsWrongDidBearerIncompleteOrExpandedScopeAndRedactsMalformedJson() = runBlocking {
        Fixture().use { fixture ->
            for (response in listOf(
                fixture.tokenJson(sub = "did:web:other.example"),
                fixture.tokenJson(type = "Bearer"),
                fixture.tokenJson(scope = "atproto"),
                fixture.tokenJson(scope = "atproto repo:fm.teal.feed.play?action=create transition:generic"),
                fixture.tokenJson().dropLast(1),
            )) {
                fixture.tokenBody = response
                val pending = fixture.start(setOf(AtprotoOAuthPermission.TEAL_SCROBBLES))
                val error = assertFailsWith<ApiException> { pending.complete(fixture.callback()) }
                assertNull(error.cause)
                assertTrue("fixture-" !in error.message.orEmpty())
                assertFailsWith<ApiException> { pending.complete(fixture.callback()) }
            }
            assertEquals(5, fixture.posts("/token").size)
        }
    }

    @Test
    fun rejectsRedirectAndAutomaticReplayAndLimitsNonceRetry() = runBlocking {
        Fixture().use { fixture ->
            fixture.parStatus = 307
            assertEquals(307, assertFailsWith<ApiException> { fixture.start() }.code)
            assertTrue(fixture.requests.none { it.path == "/redirect-target" })
            fixture.parStatus = 503
            assertEquals(503, assertFailsWith<ApiException> { fixture.start() }.code)
            assertEquals(2, fixture.posts("/par").size)
            fixture.parStatus = 400
            assertEquals(400, assertFailsWith<ApiException> { fixture.start() }.code)
            assertEquals(4, fixture.posts("/par").size)
            fixture.parStatus = null
            fixture.sendSuccessNonce = false
            assertEquals("Missing AT Protocol DPoP nonce", assertFailsWith<ApiException> { fixture.start() }.message)
        }
    }

    private class Fixture : AutoCloseable {
        private val loopback = InetAddress.getByName("127.0.0.1")
        private val identityHosts = setOf("public.api.bsky.app", "plc.directory")
        private val certificate = HeldCertificate.Builder().commonName("issuer.example")
            .addSubjectAlternativeName("issuer.example").addSubjectAlternativeName("pds.example")
            .apply { identityHosts.forEach { addSubjectAlternativeName(it) } }.build()
        private val server = HttpsServer.create(InetSocketAddress(loopback, 0), 0)
        val issuer = "https://issuer.example:${server.address.port}"
        val pds = "https://pds.example:${server.address.port}"
        val identity = AtprotoIdentity("did:plc:abcdefghijklmnopqrstuvwx", pds)
        val requests = CopyOnWriteArrayList<Request>()
        private val client = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build().let { tls ->
            HttpClient(OkHttp) {
                engine {
                    dns = okhttp3.Dns { host ->
                        check(host in identityHosts || host in setOf("issuer.example", "pds.example"))
                        listOf(loopback)
                    }
                    config {
                        sslSocketFactory(tls.sslSocketFactory(), tls.trustManager)
                        addInterceptor { chain ->
                            val request = chain.request()
                            if (request.url.host in identityHosts) {
                                val url = request.url.newBuilder().port(server.address.port).build()
                                chain.proceed(request.newBuilder().url(url).build())
                            } else {
                                chain.proceed(request)
                            }
                        }
                    }
                }
            }
        }
        private val oauth = AtprotoOAuth { client }
        var claimedResource = pds
        var claimedIssuer = issuer
        var tokenEndpoint = "$issuer/token"
        var supportsIssuer = true
        var challengePar = false
        var challengeToken = false
        var parStatus: Int? = null
        var sendSuccessNonce = true
        var tokenBody: String? = null

        init {
            val tls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
            server.httpsConfigurator = HttpsConfigurator(tls.sslContext())
            server.createContext("/") { it.use(::handle) }
            server.start()
        }

        suspend fun start(permissions: Set<AtprotoOAuthPermission> = emptySet()) = oauth.start("alice.example", permissions)

        suspend fun startFromHandle(handle: String) = oauth.start(handle, emptySet())

        fun resume(attempt: AtprotoOAuthAttempt) = oauth.resume(attempt)

        suspend fun rotate(session: AtprotoOAuthSession) =
            client.rotateOAuth(session, requireNotNull(session.refreshToken), client.discoverAtprotoOAuth(session.pds))

        suspend fun write(session: AtprotoOAuthSession, collection: String) =
            client.createPlay(session, collection, "3jzfcijpj2z2a", buildJsonObject {}).uri

        fun posts(path: String) = requests.filter { it.path == path && it.method == "POST" }

        fun state() = requireNotNull(posts("/par").last().form["state"])

        fun callback(iss: String = issuer, code: String? = "controlled-code"): String =
            ATPROTO_REDIRECT_URI + "?" + buildList {
                add("iss" to iss)
                add("state" to state())
                if (code != null) add("code" to code)
            }.formUrlEncode()

        fun tokenJson(sub: String = identity.did, type: String = "DPoP", scope: String = "atproto"): String =
            """{"sub":"$sub","token_type":"$type","scope":"$scope","access_token":"fixture-access",
                "refresh_token":"fixture-refresh","expires_in":300}"""

        private fun handle(exchange: HttpExchange) {
            val request = Request(exchange.requestURI.path, exchange.requestURI.rawQuery, exchange.requestMethod,
                exchange.requestHeaders.getFirst("Content-Type"), exchange.requestHeaders.getFirst("DPoP"),
                exchange.requestBody.readBytes().decodeToString(), exchange.requestHeaders.getFirst("Authorization"))
            requests.add(request)
            when (request.path) {
                "/xrpc/com.atproto.identity.resolveHandle" ->
                    exchange.json(200, """{"did":"${identity.did}"}""")
                "/${identity.did}" -> exchange.json(200, """{
                    "id":"${identity.did}","alsoKnownAs":["at://alice.example"],"service":[
                    {"id":"#atproto_pds","type":"AtprotoPersonalDataServer","serviceEndpoint":"$pds"}]
                }""")
                "/.well-known/oauth-protected-resource" ->
                    exchange.json(200, """{"resource":"$claimedResource","authorization_servers":["$issuer"]}""")
                "/.well-known/oauth-authorization-server" -> exchange.json(200, """{
                    "issuer":"$claimedIssuer","authorization_endpoint":"$issuer/authorize",
                    "pushed_authorization_request_endpoint":"$issuer/par","token_endpoint":"$tokenEndpoint",
                    "scopes_supported":["atproto"],"response_types_supported":["code"],
                    "code_challenge_methods_supported":["S256"],"dpop_signing_alg_values_supported":["ES256"],
                    "grant_types_supported":["authorization_code","refresh_token"],
                    "token_endpoint_auth_methods_supported":["none","private_key_jwt"],
                    "token_endpoint_auth_signing_alg_values_supported":["ES256"],
                    "authorization_response_iss_parameter_supported":$supportsIssuer,
                    "client_id_metadata_document_supported":true,"require_pushed_authorization_requests":true
                }""")
                "/par" -> when {
                    parStatus == 307 -> {
                        exchange.responseHeaders.add("Location", "/redirect-target")
                        exchange.sendResponseHeaders(307, -1)
                    }
                    parStatus == 503 -> {
                        exchange.responseHeaders.add("Retry-After", "0")
                        exchange.sendResponseHeaders(503, -1)
                    }
                    parStatus == 400 || (challengePar && posts("/par").size == 1) ->
                        exchange.json(400, """{"error":"use_dpop_nonce"}""", "par-challenge")
                    else -> exchange.json(201, """{"request_uri":"urn:ietf:params:oauth:request_uri:test","expires_in":60}""",
                        "par-success".takeIf { sendSuccessNonce })
                }
                "/token" -> if (challengeToken && posts("/token").size == 1) {
                    exchange.json(400, """{"error":"use_dpop_nonce"}""", "token-challenge")
                } else {
                    val scope = requireNotNull(posts("/par").last().form["scope"])
                        .replace("action=create&action=delete", "action=delete&action=create")
                    exchange.json(200, tokenBody ?: tokenJson(scope = scope), "token-success")
                }
                "/xrpc/com.atproto.repo.createRecord" -> if (posts(request.path).size == 1) {
                    exchange.json(401, """{"error":"use_dpop_nonce"}""", "pds-challenge")
                } else {
                    val collection = if ("fm.teal.feed.play" in request.body) "fm.teal.feed.play" else "app.rocksky.scrobble"
                    exchange.json(200, """{"uri":"at://${identity.did}/$collection/3jzfcijpj2z2a","cid":"fixture"}""", "pds-success")
                }
                else -> exchange.sendResponseHeaders(404, -1)
            }
        }

        override fun close() {
            client.close()
            server.stop(0)
        }
    }

    private class Request(
        val path: String,
        val query: String?,
        val method: String,
        val contentType: String?,
        private val dpop: String?,
        val body: String,
        val authorization: String?,
    ) {
        val form get() = parseQueryString(body.replace("+", "%20"))
        val proof get() = SignedJWT.parse(requireNotNull(dpop))
    }
}

private fun HttpExchange.json(status: Int, body: String, nonce: String? = null) {
    responseHeaders.set("Content-Type", "application/json")
    if (nonce != null) responseHeaders.set("DPoP-Nonce", nonce)
    val bytes = body.encodeToByteArray()
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}
