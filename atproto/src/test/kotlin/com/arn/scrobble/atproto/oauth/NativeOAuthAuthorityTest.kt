package com.arn.scrobble.atproto.oauth

import com.arn.scrobble.atproto.crypto.P256PublicJwk
import com.arn.scrobble.atproto.crypto.toPublicKey
import com.arn.scrobble.atproto.encoding.decodeBase64Url
import com.arn.scrobble.atproto.http.AtProtoHttpClientLease
import com.arn.scrobble.atproto.http.DpopNonceException
import com.arn.scrobble.atproto.storage.AtProtoAccountStore
import com.arn.scrobble.atproto.storage.IncompleteSessionUpdateException
import com.arn.scrobble.atproto.storage.JvmVaultPersistence
import com.arn.scrobble.atproto.storage.StaleAuthorizationException
import com.arn.scrobble.atproto.storage.StaleSessionException
import com.arn.scrobble.atproto.storage.VaultCipher
import com.arn.scrobble.atproto.storage.VaultPersistence
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.security.Signature
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val CLIENT = "https://client.example/oauth-client-metadata.json"
private const val REDIRECT = "example.client:/pano-scrobbler/oauth/callback"
private const val DID = "did:plc:abcdefghijklmnopqrstuvwx"
private const val SCOPE = "atproto repo:fm.teal.feed.play?action=create repo:app.rocksky.scrobble?action=create"
private val config = OAuthClientConfig(CLIENT, REDIRECT, SCOPE)
private fun record(collection: String = "fm.teal.feed.play") = buildJsonObject { put("\$type", collection) }

private class Fixture {
    val root: File = Files.createTempDirectory(File(System.getProperty("pano.test.directory")).also { it.mkdirs() }.toPath(), "oauth-").toFile()
    var now = 1_700_000_000_000L
    var tokenScope = "atproto repo:fm.teal.feed.play?action=create"
    var tokenExpiry = 120L
    var tokenMutation: (JsonObject) -> JsonObject = { it }
    var pds = "https://pds.example"
    var subject = DID
    var tokenRequests = 0
    var resourceRequests = 0
    var parRequests = 0
    var lastPar = emptyMap<String, String>()
    val proofs = mutableListOf<Pair<String, JsonObject>>()
    var intercept: (suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData?)? = null
    val client = HttpClient(MockEngine { request ->
        val url = request.url.toString()
        request.headers["DPoP"]?.let { proofs += url to verifyProof(it) }
        intercept?.invoke(this, request)?.let { return@MockEngine it }
        when {
            url.contains("/xrpc/com.atproto.identity.resolveHandle") -> json("""{"did":"$subject"}""")
            url.startsWith("https://plc.directory/") -> json("""{"id":"$subject","alsoKnownAs":["at://alice.example"],"service":[{"id":"#atproto_pds","type":"AtprotoPersonalDataServer","serviceEndpoint":"$pds"}]}""")
            url.endsWith("/.well-known/oauth-protected-resource") -> json("""{"resource":"$pds","authorization_servers":["https://as.example"]}""")
            url == "https://as.example/.well-known/oauth-authorization-server" -> json(SERVER)
            url == "https://as.example/oauth/par" -> {
                parRequests++
                lastPar = formDecode((request.body as TextContent).text)
                json("""{"request_uri":"urn:ietf:params:oauth:request_uri:fixture","expires_in":300}""", 201, "par-nonce")
            }
            url == "https://as.example/oauth/token" -> {
                tokenRequests++
                json(tokenMutation(tokenJson("access-$tokenRequests", "refresh-$tokenRequests")).toString(), nonce = "token-$tokenRequests")
            }
            url.endsWith("/xrpc/com.atproto.repo.createRecord") -> {
                resourceRequests++
                val collection = Json.parseToJsonElement((request.body as TextContent).text).jsonObject.getValue("collection").jsonPrimitive.content
                json("""{"uri":"at://$DID/$collection/fixture","cid":"bafyrei${"a".repeat(52)}"}""", nonce = "resource-$resourceRequests")
            }
            else -> error("Unexpected fixture URL: $url")
        }
    }) { followRedirects = false }

    fun store(persistence: VaultPersistence = JvmVaultPersistence(root)) = AtProtoAccountStore(persistence, VaultCipher.Plaintext)
    fun authority(store: AtProtoAccountStore = store(), selected: OAuthClientConfig = config) =
        AtProtoAccountAuthority(store, selected, { AtProtoHttpClientLease(client) {} }, EpochClock { now })
    fun callback(state: String = lastPar.getValue("state"), issuer: String = "https://as.example") =
        "$REDIRECT?${formEncode(mapOf("state" to state, "code" to "code-secret", "iss" to issuer))}"
    suspend fun login(): AtProtoAccount {
        authority().beginAuthorization("alice.example")
        return authority().completeAuthorization(callback())
    }
    fun tokenJson(access: String, refresh: String) = buildJsonObject {
        put("access_token", access); put("refresh_token", refresh); put("token_type", "DPoP")
        put("expires_in", tokenExpiry); put("scope", tokenScope); put("sub", DID)
    }
}

private fun MockRequestHandleScope.json(body: String, status: Int = 200, nonce: String? = null): HttpResponseData =
    respond(body, HttpStatusCode.fromValue(status), headersOf(*buildList {
        add(HttpHeaders.ContentType to listOf("application/json"))
        if (nonce != null) add("DPoP-Nonce" to listOf(nonce))
    }.toTypedArray()))

private fun verifyProof(jwt: String): JsonObject {
    val parts = jwt.split('.')
    assertEquals(3, parts.size)
    val header = Json.parseToJsonElement(parts[0].decodeBase64Url().decodeToString()).jsonObject
    assertEquals("dpop+jwt", header.getValue("typ").jsonPrimitive.content)
    assertEquals("ES256", header.getValue("alg").jsonPrimitive.content)
    val public = header.getValue("jwk").jsonObject
    assertFalse("d" in public)
    val key = Json.decodeFromJsonElement<P256PublicJwk>(public).toPublicKey()
    assertTrue(Signature.getInstance("SHA256withECDSAinP1363Format").run {
        initVerify(key)
        update("${parts[0]}.${parts[1]}".toByteArray(Charsets.US_ASCII))
        verify(parts[2].decodeBase64Url())
    })
    return Json.parseToJsonElement(parts[1].decodeBase64Url().decodeToString()).jsonObject
}

private const val SERVER = """{
    "issuer":"https://as.example","authorization_endpoint":"https://as.example/oauth/authorize",
    "token_endpoint":"https://as.example/oauth/token","pushed_authorization_request_endpoint":"https://as.example/oauth/par",
    "response_types_supported":["code"],"grant_types_supported":["authorization_code","refresh_token"],
    "code_challenge_methods_supported":["S256"],"token_endpoint_auth_methods_supported":["none","private_key_jwt"],
    "token_endpoint_auth_signing_alg_values_supported":["ES256"],"scopes_supported":["atproto"],
    "dpop_signing_alg_values_supported":["ES256"],"authorization_response_iss_parameter_supported":true,
    "require_pushed_authorization_requests":true,"client_id_metadata_document_supported":true
}"""

class NativeOAuthAuthorityTest {
    @Test fun onlyExactInvalidTokenChallengeForcesRefreshAndOneSafeRetryEvenWithUnknownExpiry(): Unit = runBlocking {
        for (challenge in listOf(null, "Bearer error=\"invalid_token\"", "DPoPish error=\"invalid_token\"",
            "DPoP error=\"INVALID_TOKEN\"", "DPoP realm=\"error=invalid_token\"",
            "DPoP error=invalid_token, error=invalid_token", "Basic realm=\"a,b\", dPoP ErRoR=invalid_token")) {
            val f = Fixture()
            f.tokenMutation = { JsonObject(it - "expires_in") }
            val account = f.login()
            var calls = 0
            f.intercept = { request ->
                if (request.url.encodedPath.endsWith("createRecord")) {
                    calls++
                    if (calls == 1) respond("{}", HttpStatusCode.Unauthorized, headersOf(*buildList {
                        add("DPoP-Nonce" to listOf("resource-rejected"))
                        challenge?.let { add("WWW-Authenticate" to listOf(it)) }
                    }.toTypedArray())) else {
                        assertEquals("DPoP access-2", request.headers[HttpHeaders.Authorization])
                        null
                    }
                } else null
            }
            if (challenge?.startsWith("Basic") == true) {
                f.authority().createRecord(account.session, "fm.teal.feed.play", record())
                assertEquals(2, calls)
                assertEquals(2, f.tokenRequests)
            } else {
                assertFailsWith<OAuthResponseException> { f.authority().createRecord(account.session, "fm.teal.feed.play", record()) }
                assertEquals(1, calls)
                assertEquals(1, f.tokenRequests)
            }
        }
    }

    @Test fun forcedRefreshNarrowingAndRepeatedInvalidTokenCannotReplayAWriteIndefinitely(): Unit = runBlocking {
        for (narrow in listOf(true, false)) {
            val f = Fixture()
            val account = f.login()
            if (narrow) f.tokenScope = "atproto"
            var calls = 0
            f.intercept = { request ->
                if (request.url.encodedPath.endsWith("createRecord")) {
                    calls++
                    respond("{}", HttpStatusCode.Unauthorized, headersOf("DPoP-Nonce" to listOf("n"),
                        "WWW-Authenticate" to listOf("DPoP error=invalid_token")))
                } else null
            }
            if (narrow) assertFailsWith<IllegalArgumentException> { f.authority().createRecord(account.session, "fm.teal.feed.play", record()) }
            else assertFailsWith<OAuthResponseException> { f.authority().createRecord(account.session, "fm.teal.feed.play", record()) }
            assertEquals(if (narrow) 1 else 2, calls)
            assertEquals(2, f.tokenRequests)
        }
    }

    @Test fun recordRequestAndResultBoundariesRejectMismatchesAndOversizedJson(): Unit = runBlocking {
        val f = Fixture()
        val account = f.login()
        assertFailsWith<IllegalArgumentException> { f.authority().createRecord(account.session, "fm.teal.feed.play", record("app.rocksky.scrobble")) }
        assertFailsWith<IllegalArgumentException> { f.authority().createRecord(account.session, "fm.teal.feed.play", buildJsonObject {
            put("\$type", "fm.teal.feed.play"); put("large", "x".repeat(65_536))
        }) }
        assertEquals(0, f.resourceRequests)
        for ((uri, cid) in listOf("at://$DID/app.rocksky.scrobble/key" to "bafyrei${"a".repeat(52)}",
            "at://$DID/fm.teal.feed.play/../extra" to "bafyrei${"a".repeat(52)}",
            "at://$DID/fm.teal.feed.play/key" to "not-a-cid")) {
            f.intercept = { request ->
                if (request.url.encodedPath.endsWith("createRecord")) json("""{"uri":"$uri","cid":"$cid"}""", nonce = "n") else null
            }
            assertFailsWith<IllegalArgumentException> { f.authority().createRecord(account.session, "fm.teal.feed.play", record()) }
        }
        assertEquals(account, f.authority().currentAccount())
    }

    @Test fun noncesRejectDuplicatesSpacesQuotesBackslashUnicodeAndControls(): Unit = runBlocking {
        for (values in listOf(emptyList(), listOf("a", "b"), listOf(""), listOf("a b"), listOf("a\"b"), listOf("a\\b"), listOf("é"))) {
            val f = Fixture()
            f.intercept = { request ->
                if (request.url.encodedPath == "/oauth/par") respond("{}", HttpStatusCode.Created,
                    headersOf("DPoP-Nonce" to values)) else null
            }
            assertFailsWith<DpopNonceException> { f.authority().beginAuthorization("alice.example") }
            assertNull(f.authority().currentAccount())
        }
    }

    @Test fun boundedErrorBodyFailureRetainsRefreshWhileOversizedSuccessIsQuarantined(): Unit = runBlocking {
        for (status in listOf(200, 503)) {
            val f = Fixture()
            val account = f.login()
            f.now += 120_000
            f.intercept = { request ->
                if (request.url.encodedPath == "/oauth/token") json("x".repeat(65_537), status, "oversized-response") else null
            }
            assertFailsWith<IllegalArgumentException> { f.authority().refresh(account.session) }
            if (status == 200) assertFailsWith<IncompleteSessionUpdateException> { f.authority().currentAccount() }
            else assertEquals(account, f.authority().currentAccount())
        }
    }

    @Test fun nonceChallengeRetriesOnceWithNewProofAndPersistsTheFinalParNonce(): Unit = runBlocking {
        val f = Fixture()
        var parCalls = 0
        var tokenCalls = 0
        f.intercept = { request ->
            when (request.url.encodedPath) {
                "/oauth/par" -> {
                    if (++parCalls == 1) json("""{"error":"use_dpop_nonce"}""", 400, "challenge") else {
                        assertEquals("challenge", f.proofs.last().second["nonce"]?.jsonPrimitive?.content)
                        null
                    }
                }
                "/oauth/token" -> {
                    tokenCalls++
                    assertEquals("par-nonce", f.proofs.last().second["nonce"]?.jsonPrimitive?.content)
                    null
                }
                else -> null
            }
        }
        f.authority().beginAuthorization("alice.example")
        f.authority().completeAuthorization(f.callback())
        assertEquals(2, parCalls)
        assertEquals(1, tokenCalls)
        assertEquals(f.proofs.size, f.proofs.map { it.second.getValue("jti") }.distinct().size)
        assertTrue(f.proofs.all { "ath" !in it.second })
        val g = Fixture()
        var challenged = 0
        g.intercept = { request ->
            if (request.url.encodedPath == "/oauth/par") {
                challenged++
                json("""{"error":"use_dpop_nonce"}""", 400, "same-nonce")
            } else null
        }
        assertFailsWith<OAuthResponseException> { g.authority().beginAuthorization("alice.example") }
        assertEquals(2, challenged)
    }

    @Test fun missingNonceAlwaysRejectsButAnErrorResponseDoesNotQuarantineAnUnconsumedRefresh(): Unit = runBlocking {
        for (status in listOf(200, 429, 503)) {
            val f = Fixture()
            val account = f.login()
            f.now += 120_000
            f.intercept = { request ->
                if (request.url.encodedPath == "/oauth/token") json("{}", status) else null
            }
            assertFailsWith<DpopNonceException> { f.authority().refresh(account.session) }
            if (status == 200) assertFailsWith<IncompleteSessionUpdateException> { f.authority().currentAccount() }
            else assertEquals(account, f.authority().currentAccount())
        }
    }

    @Test fun callbackConsumptionAndCompletionPersistenceMustSucceedBeforeExchangeOrPublication(): Unit = runBlocking {
        for (failConsumption in listOf(true, false)) {
            val f = Fixture()
            f.authority().beginAuthorization("alice.example")
            val files = JvmVaultPersistence(f.root)
            var writes = 0
            val broken = object : VaultPersistence by files {
                override fun write(bytes: ByteArray) {
                    writes++
                    if (writes == (if (failConsumption) 1 else 2)) throw IOException("cannot persist callback transition")
                    files.write(bytes)
                }
            }
            assertFailsWith<IOException> { f.authority(f.store(broken)).completeAuthorization(f.callback()) }
            assertNull(f.authority().currentAccount())
            assertEquals(if (failConsumption) 0 else 1, f.tokenRequests)
            if (!failConsumption) assertFailsWith<AtProtoOAuthException> { f.authority().completeAuthorization(f.callback()) }
        }
    }

    @Test fun simultaneousCallbacksExchangeExactlyOnceAndNewLoginChangesGeneration(): Unit = runBlocking {
        val f = Fixture()
        val old = f.login()
        f.authority().beginAuthorization("alice.example")
        val callback = f.callback()
        val results = listOf(async { runCatching { f.authority().completeAuthorization(callback) } },
            async { runCatching { f.authority().completeAuthorization(callback) } }).map { it.await() }
        val replacement = results.mapNotNull { it.getOrNull() }.single()
        assertNotEquals(old.session, replacement.session)
        assertEquals(2, f.tokenRequests)
        assertFailsWith<StaleSessionException> { f.authority().refresh(old.session) }
        assertEquals(replacement, f.authority().currentAccount())
    }

    @Test fun resourceNonceChallengeAndSuccessfulNonceAreDurableAndNeverConfusedWithTheAsNonce(): Unit = runBlocking {
        val f = Fixture()
        val account = f.login()
        var calls = 0
        f.intercept = { request ->
            if (request.url.encodedPath.endsWith("createRecord")) {
                calls++
                val proof = f.proofs.last().second
                if (calls == 1) {
                    assertNull(proof["nonce"])
                    respond("""{"error":"use_dpop_nonce"}""", HttpStatusCode.Unauthorized,
                        headersOf("DPoP-Nonce" to listOf("resource-challenge"), "WWW-Authenticate" to listOf("DPoP error=\"use_dpop_nonce\"")))
                } else {
                    assertEquals(if (calls == 2) "resource-challenge" else "resource-1", proof["nonce"]?.jsonPrimitive?.content)
                    null
                }
            } else null
        }
        f.authority().createRecord(account.session, "fm.teal.feed.play", record())
        f.authority().createRecord(account.session, "fm.teal.feed.play", record())
        assertEquals(3, calls)
        assertEquals(1, f.tokenRequests)
    }

    @Test fun successfulRefreshReplacesGrantedScopesBeforeAnyRecordRequest(): Unit = runBlocking {
        val f = Fixture()
        val account = f.login()
        f.now += 120_000
        f.tokenScope = "atproto repo:app.rocksky.scrobble?action=create"
        val current = f.authority().refresh(account.session)
        assertEquals(setOf("atproto", "repo:app.rocksky.scrobble?action=create"), current.grantedScopes)
        assertFailsWith<IllegalArgumentException> { f.authority().createRecord(account.session, "fm.teal.feed.play", record()) }
        assertEquals(0, f.resourceRequests)
        f.authority().createRecord(account.session, "app.rocksky.scrobble", record("app.rocksky.scrobble"))
        assertEquals(1, f.resourceRequests)
    }

    @Test fun recreatedAuthorityUsesDurableParNoncePkceAndActualGrantedScope(): Unit = runBlocking {
        val f = Fixture()
        val launch = f.authority().beginAuthorization("@alice.example")
        assertEquals(setOf("client_id", "request_uri"), formDecode(launch.authorizationUrl.substringAfter('?')).keys)
        assertEquals(CLIENT, f.lastPar["client_id"])
        assertEquals(REDIRECT, f.lastPar["redirect_uri"])
        assertEquals(SCOPE, f.lastPar["scope"])
        assertEquals("S256", f.lastPar["code_challenge_method"])
        var verifier: String? = null
        f.intercept = { request ->
            if (request.url.encodedPath == "/oauth/token") {
                val form = formDecode((request.body as TextContent).text)
                verifier = form["code_verifier"]
                assertEquals("authorization_code", form["grant_type"])
                assertEquals("code-secret", form["code"])
                assertEquals(REDIRECT, form["redirect_uri"])
                assertEquals("par-nonce", f.proofs.last().second["nonce"]?.jsonPrimitive?.content)
            }
            null
        }
        val account = f.authority().completeAuthorization(f.callback())
        assertEquals(f.lastPar["code_challenge"], pkceChallenge(assertNotNull(verifier)))
        assertEquals(setOf("atproto", "repo:fm.teal.feed.play?action=create"), account.grantedScopes)
        assertEquals(account, f.authority().currentAccount())
        assertFalse(account.toString().contains("access-1"))
        assertFailsWith<AtProtoOAuthException> { f.authority().completeAuthorization(f.callback()) }
        assertEquals(1, f.tokenRequests)
    }

    @Test fun nativeCallbackRejectsWrongDestinationFragmentDuplicatesAndConflictingResultsWithoutExchange(): Unit = runBlocking {
        val f = Fixture()
        f.authority().beginAuthorization("alice.example")
        val callback = f.callback()
        for (invalid in listOf(callback.replace(REDIRECT, "example.client://pano-scrobbler/oauth/callback"),
            callback.replace(REDIRECT, "$REDIRECT/extra"), "$callback#code=evil", "$callback&%73tate=other",
            "$callback&code=other", "$callback&error=access_denied")) {
            assertFailsWith<IllegalArgumentException> { f.authority().completeAuthorization(invalid) }
        }
        assertFailsWith<java.nio.charset.CharacterCodingException> { f.authority().completeAuthorization("$callback&unknown=%ff") }
        assertEquals(0, f.tokenRequests)
        assertNotNull(f.authority().completeAuthorization(callback))
    }

    @Test fun missingOrWrongIssuerBurnsOnlyTheMatchingState(): Unit = runBlocking {
        for (issuer in listOf(null, "https://evil.example")) {
            val f = Fixture()
            f.authority().beginAuthorization("alice.example")
            val callback = if (issuer == null) f.callback().substringBefore("&iss=") else f.callback(issuer = issuer)
            assertFailsWith<IllegalArgumentException> { f.authority().completeAuthorization(callback) }
            assertFailsWith<AtProtoOAuthException> { f.authority().completeAuthorization(f.callback()) }
            assertEquals(0, f.tokenRequests)
        }
    }

    @Test fun wrongStateDoesNotCancelPendingButExpiryAndDenialAreSingleUse(): Unit = runBlocking {
        val f = Fixture()
        val launch = f.authority().beginAuthorization("alice.example")
        assertFailsWith<AtProtoOAuthException> { f.authority().completeAuthorization(f.callback(state = "unknown")) }
        f.now = launch.expiresAt
        assertFailsWith<AtProtoOAuthException> { f.authority().completeAuthorization(f.callback()) }
        f.now--
        assertFailsWith<AtProtoOAuthException> { f.authority().completeAuthorization(f.callback()) }
        f.authority().beginAuthorization("alice.example")
        val denial = "$REDIRECT?${formEncode(mapOf("state" to f.lastPar.getValue("state"), "iss" to "https://as.example", "error" to "access_denied"))}"
        assertFailsWith<AuthorizationDeniedException> { f.authority().completeAuthorization(denial) }
        assertFailsWith<AtProtoOAuthException> { f.authority().completeAuthorization(denial) }
        assertEquals(0, f.tokenRequests)
    }

    @Test fun invalidTokenPropertiesNeverPublishAnAccount(): Unit = runBlocking {
        val mutations: List<(JsonObject) -> JsonObject> = listOf(
            { JsonObject(it - "sub") }, { JsonObject(it - "scope") },
            { JsonObject(it + ("token_type" to Json.parseToJsonElement("\"Bearer\""))) },
            { JsonObject(it + ("sub" to Json.parseToJsonElement("\"did:plc:zzzzzzzzzzzzzzzzzzzzzzzz\""))) },
            { JsonObject(it + ("scope" to Json.parseToJsonElement("\"repo:fm.teal.feed.play?action=create\""))) },
            { JsonObject(it + ("expires_in" to Json.parseToJsonElement("-1"))) },
            { JsonObject(it + ("expires_in" to Json.parseToJsonElement(Long.MAX_VALUE.toString()))) },
            { JsonObject(it + ("access_token" to Json.parseToJsonElement("\"bad token\""))) },
        )
        for (mutation in mutations) {
            val f = Fixture()
            f.tokenMutation = mutation
            f.authority().beginAuthorization("alice.example")
            assertFailsWith<IllegalArgumentException> { f.authority().completeAuthorization(f.callback()) }
            assertNull(f.authority().currentAccount())
        }
    }

    @Test fun changedDidOrPdsCannotBindACompletedAuthorization(): Unit = runBlocking {
        for (change in listOf<(Fixture) -> Unit>({ it.subject = "did:plc:zzzzzzzzzzzzzzzzzzzzzzzz" }, { it.pds = "https://migrated.example" })) {
            val f = Fixture()
            f.authority().beginAuthorization("alice.example")
            change(f)
            assertFailsWith<IllegalArgumentException> { f.authority().completeAuthorization(f.callback()) }
            assertNull(f.authority().currentAccount())
        }
    }

    @Test fun slowParAndConsumedCallbackCannotResurrectLoggedOutOrReplacedAuthorization(): Unit = runBlocking {
        val f = Fixture()
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        f.intercept = { request ->
            if (request.url.encodedPath == "/oauth/par") { entered.complete(Unit); finish.await() }
            null
        }
        val begin = async { runCatching { f.authority().beginAuthorization("alice.example") } }
        entered.await()
        f.authority().logout()
        finish.complete(Unit)
        assertTrue(begin.await().exceptionOrNull() is StaleAuthorizationException)
        f.intercept = null
        f.authority().beginAuthorization("alice.example")
        val tokenEntered = CompletableDeferred<Unit>()
        val tokenFinish = CompletableDeferred<Unit>()
        f.intercept = { request ->
            if (request.url.encodedPath == "/oauth/token") { tokenEntered.complete(Unit); tokenFinish.await() }
            null
        }
        val complete = async { runCatching { f.authority().completeAuthorization(f.callback()) } }
        tokenEntered.await()
        f.authority().logout()
        tokenFinish.complete(Unit)
        assertTrue(complete.await().exceptionOrNull() is StaleAuthorizationException)
        assertNull(f.authority().currentAccount())
    }

    @Test fun independentAuthoritiesSerializeRefreshUsingLatestRotatingToken(): Unit = runBlocking {
        val f = Fixture()
        val account = f.login()
        f.now += 120_000
        val refreshForms = mutableListOf<Map<String, String>>()
        f.intercept = { request ->
            if (request.url.encodedPath == "/oauth/token") {
                refreshForms += formDecode((request.body as TextContent).text)
                delay(50)
            }
            null
        }
        val a = async { f.authority().refresh(account.session) }
        val b = async { f.authority().refresh(account.session) }
        assertEquals(a.await(), b.await())
        assertEquals(listOf("refresh-1"), refreshForms.map { it["refresh_token"] })
        assertEquals("token-1", f.proofs.first { it.first.endsWith("/token") && it.second["nonce"]?.jsonPrimitive?.content == "token-1" }.second["nonce"]?.jsonPrimitive?.content)
        assertEquals(2, f.tokenRequests)
    }

    @Test fun classifiedRefreshFailuresRetainNonceAndCredentialButInvalidGrantRemovesIt(): Unit = runBlocking {
        for ((status, error) in listOf(429 to "slow_down", 503 to "temporarily_unavailable", 400 to "use_dpop_nonce", 400 to "invalid_grant")) {
            val f = Fixture()
            val account = f.login()
            f.now += 120_000
            f.intercept = { request ->
                if (request.url.encodedPath == "/oauth/token") json("""{"error":"$error"}""", status, "error-nonce") else null
            }
            val failure = assertFailsWith<OAuthResponseException> { f.authority().refresh(account.session) }
            assertEquals(status, failure.status)
            assertEquals(error, failure.error)
            if (error == "invalid_grant") assertNull(f.authority().currentAccount()) else {
                assertEquals(account, f.authority().currentAccount())
                f.intercept = { request ->
                    if (request.url.encodedPath == "/oauth/token") {
                        assertEquals("error-nonce", f.proofs.last().second["nonce"]?.jsonPrimitive?.content)
                        assertEquals("refresh-1", formDecode((request.body as TextContent).text)["refresh_token"])
                    }
                    null
                }
                f.authority().refresh(account.session)
            }
        }
    }

    @Test fun lostRefreshResponseAndInvalidSuccessfulResponseQuarantineRatherThanReplay(): Unit = runBlocking {
        for (lost in listOf(true, false)) {
            val f = Fixture()
            val account = f.login()
            f.now += 120_000
            f.intercept = { request ->
                if (request.url.encodedPath == "/oauth/token") {
                    if (lost) throw IOException("ambiguous refresh response loss")
                    json("""{"access_token":"new-but-invalid"}""", nonce = "next")
                } else null
            }
            if (lost) assertFailsWith<IOException> { f.authority().refresh(account.session) }
            else assertFailsWith<IllegalArgumentException> { f.authority().refresh(account.session) }
            assertFailsWith<IncompleteSessionUpdateException> { f.authority().currentAccount() }
            assertFailsWith<IncompleteSessionUpdateException> { f.authority().refresh(account.session) }
            f.authority().logout()
            assertNull(f.authority().currentAccount())
        }
    }

    @Test fun cancellationAfterRefreshStartsFinishesPublicationAndLogoutWaits(): Unit = runBlocking {
        val f = Fixture()
        val account = f.login()
        f.now += 120_000
        val entered = CompletableDeferred<Unit>()
        val finish = CompletableDeferred<Unit>()
        f.intercept = { request ->
            if (request.url.encodedPath == "/oauth/token") { entered.complete(Unit); finish.await() }
            null
        }
        val refresh = launch { f.authority().refresh(account.session) }
        entered.await()
        refresh.cancel()
        val logout = async { f.authority().logout() }
        delay(100)
        assertFalse(logout.isCompleted)
        finish.complete(Unit)
        refresh.join()
        logout.await()
        assertNull(f.authority().currentAccount())
        assertFailsWith<StaleSessionException> { f.authority().refresh(account.session) }
    }

    @Test fun failedRotatedCredentialPublicationNeverReturnsVolatileSuccess(): Unit = runBlocking {
        val f = Fixture()
        val account = f.login()
        f.now += 120_000
        val delegate = JvmVaultPersistence(f.root)
        var writes = 0
        val broken = object : VaultPersistence by delegate {
            override fun write(bytes: ByteArray) {
                if (++writes == 2) throw IOException("disk full after refresh")
                delegate.write(bytes)
            }
        }
        assertFailsWith<IOException> { f.authority(f.store(broken)).refresh(account.session) }
        assertFailsWith<IncompleteSessionUpdateException> { f.authority().currentAccount() }
    }

    @Test fun opaqueTokensAndResourceNoncesSurviveOrdinaryFailuresWithoutQuarantine(): Unit = runBlocking {
        val f = Fixture()
        val account = f.login()
        f.intercept = { request ->
            if (request.url.encodedPath.endsWith("createRecord")) {
                assertEquals("DPoP access-1", request.headers[HttpHeaders.Authorization])
                assertEquals(hashAccessToken("access-1"), f.proofs.last().second["ath"]?.jsonPrimitive?.content)
                json("""{"error":"RateLimitExceeded"}""", 429, "resource-error")
            } else null
        }
        assertFailsWith<OAuthResponseException> { f.authority().createRecord(account.session, "fm.teal.feed.play", record()) }
        assertEquals(account, f.authority().currentAccount())
        f.intercept = { request ->
            if (request.url.encodedPath.endsWith("createRecord")) {
                assertEquals("resource-error", f.proofs.last().second["nonce"]?.jsonPrimitive?.content)
                throw IOException("ordinary request connection lost")
            } else null
        }
        assertFailsWith<IOException> { f.authority().createRecord(account.session, "fm.teal.feed.play", record()) }
        assertEquals(account, f.authority().currentAccount())
        assertFailsWith<IllegalArgumentException> { f.authority().createRecord(account.session, "app.rocksky.scrobble", record("app.rocksky.scrobble")) }
        f.authority().logout()
        assertFailsWith<StaleSessionException> { f.authority().createRecord(account.session, "fm.teal.feed.play", record()) }
    }
}
