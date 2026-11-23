package com.arn.scrobble

import androidx.datastore.core.DataStoreFactory
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.arn.scrobble.api.AccountType
import com.arn.scrobble.api.ScrobbleEvent
import com.arn.scrobble.api.UserCached
import com.arn.scrobble.api.atproto.AtprotoAppPasswordSession
import com.arn.scrobble.api.atproto.AtprotoPendingPlays
import com.arn.scrobble.api.atproto.AtprotoScrobblable
import com.arn.scrobble.api.atproto.AtprotoSessions
import com.arn.scrobble.api.atproto.atprotoMask
import com.arn.scrobble.api.atproto.playCollection
import com.arn.scrobble.api.lastfm.ScrobbleData
import com.arn.scrobble.db.AccountBitmaskConverter.accountTypesToBitMask
import com.arn.scrobble.db.PanoDb
import com.arn.scrobble.db.PendingScrobble
import com.arn.scrobble.media.PlayingTrackInfo
import com.arn.scrobble.media.ScrobbleQueue
import com.arn.scrobble.pref.MainPrefs
import com.arn.scrobble.utils.DesktopStuff
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AtprotoPendingPlaysTest {
    @Test
    fun qualifiedPlaybackGoesThroughPendingToBothCollectionsAndBackToRecents() = fixture { fixture, path, prefs, http ->
        AtprotoSessions(prefs).connect(fixture.session(did), user, types)
        Room.databaseBuilder<PanoDb>(path).setDriver(BundledSQLiteDriver()).build().use { db ->
            val pending = AtprotoPendingPlays(db.getPendingScrobblesDao(), prefs) { http }
            val completed = CompletableDeferred<ScrobbleData>()
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            try {
                val track = PlayingTrackInfo("pano.fixture", "listen", null)
                track.putOriginals(play.artist, play.track, requireNotNull(play.album), requireNotNull(play.albumArtist),
                    1, requireNotNull(play.duration), null, null, emptyMap())
                track.prepareForScrobbling()
                track.putPreprocessedData(track.toScrobbleData(false), loved = false, additionalMetadataFetched = true)
                val start = track.playStartTime
                ScrobbleQueue(scope, submit = {
                    assertTrue(pending.enqueue(it))
                    completed.complete(it)
                }, submitNowPlayingEnabled = { false }).scrobble(track, delay = 0)
                assertEquals(start, withTimeout(5000) { completed.await() }.timestamp)
                assertFalse(pending.drain(10))
                assertEquals(2, fixture.posts.size)
                for (account in prefs.data.first().scrobbleAccounts) {
                    val latest = AtprotoScrobblable(account) { http }.getRecents(1).getOrThrow().entries.single()
                    assertEquals(start, latest.date)
                    assertEquals(play.track, latest.name)
                }
            } finally {
                scope.cancel()
            }
        }
    }

    @Test
    fun room21MigrationKeepsLegacyPendingRowWithoutInventingAtprotoOwnership() = fixture { _, path, _, _ ->
        val directory = sequenceOf(File("schemas"), File("composeApp/schemas")).first { it.isDirectory }
        val schema = Json.parseToJsonElement(directory.resolve("com.arn.scrobble.db.PanoDb/21.json").readText())
            .jsonObject.getValue("database").jsonObject
        val driver = BundledSQLiteDriver()
        driver.open(path).use { db ->
            fun exec(sql: String) { db.prepare(sql).use { it.step() } }
            for (entity in schema.getValue("entities").jsonArray) {
                val obj = entity.jsonObject
                val name = obj.getValue("tableName").jsonPrimitive.content
                val tables = listOf(obj.getValue("createSql").jsonPrimitive.content) +
                    obj["indices"]?.jsonArray.orEmpty().map { it.jsonObject.getValue("createSql").jsonPrimitive.content }
                tables.forEach { exec(it.replace("\${TABLE_NAME}", name)) }
            }
            schema.getValue("setupQueries").jsonArray.forEach { exec(it.jsonPrimitive.content) }
            exec("PRAGMA user_version = 21")
            exec("INSERT INTO PendingScrobbles (_id,event,services,lastFailedTimestamp,artist,track,timestamp,canForceRetry) VALUES (1,'scrobble',1,2,'Legacy artist','Legacy song',3,1)")
        }
        Room.databaseBuilder<PanoDb>(path).setDriver(driver).build().use { db ->
            val dao = db.getPendingScrobblesDao()
            val legacy = dao.legacyScrobblesForSubmission(atprotoMask, 10).single()
            assertEquals(setOf(AccountType.LASTFM), legacy.services)
            assertEquals("Legacy song", legacy.scrobbleData.track)
            assertTrue(legacy.canForceRetry)
            assertNull(legacy.atprotoDid)
            assertNull(legacy.atprotoTid)
            assertTrue(dao.atprotoScrobblesForSubmission(did, atprotoMask, 10).isEmpty())
        }
    }

    @Test
    fun lostRockskyResponseReconcilesAfterRestartAndDoesNotTransferToAnotherDid() = fixture { fixture, path, prefs, http ->
        val sessions = AtprotoSessions(prefs)
        sessions.connect(fixture.session(did), user, types)
        fixture.dropRocksky.set(true)

        Room.databaseBuilder<PanoDb>(path).setDriver(BundledSQLiteDriver()).build().use { db ->
            val dao = db.getPendingScrobblesDao()
            val sender = AtprotoPendingPlays(dao, prefs) { http }
            assertTrue(sender.enqueue(play))
            assertTrue(sender.enqueue(play))
            val queued = dao.allScrobblesForSubmission(10)
            assertEquals(2, queued.size)
            assertNotEquals(queued[0].atprotoTid, queued[1].atprotoTid)
            assertTrue(sender.drain(10))
            val pending = dao.allScrobblesForSubmission(10)
            assertEquals(2, pending.size)
            assertTrue(pending.all { it.services == setOf(AccountType.ROCKSKY) && it.tealCid == cid && it.atprotoDid == did })
            assertEquals(3, fixture.posts.size)
        }

        prefs.updateData { it.copy(atprotoSession = null) }
        Room.databaseBuilder<PanoDb>(path).setDriver(BundledSQLiteDriver()).build().use { db ->
            val dao = db.getPendingScrobblesDao()
            val sender = AtprotoPendingPlays(dao, prefs) { http }
            assertFalse(sender.drain(10))
            assertEquals(3, fixture.posts.size)
            assertTrue(dao.allScrobblesForSubmission(10).all { it.atprotoDid == did })

            sessions.connect(fixture.session(otherDid), user, types)
            assertFalse(sender.drain(10))
            assertEquals(3, fixture.posts.size)
            sessions.connect(fixture.session(did), user, types)
            assertFalse(sender.drain(10))
            assertEquals(0, dao.count())
            assertEquals(4, fixture.posts.size)
            val accounts = prefs.data.first().scrobbleAccounts
            for (account in accounts) {
                val rows = AtprotoScrobblable(account) { http }.getRecents(1).getOrThrow().entries
                assertEquals(2, rows.size)
                assertEquals(2, rows.map { it.atprotoUri }.toSet().size)
                assertTrue(rows.all { it.date == play.timestamp && it.name == play.track })
                assertTrue(rows.all { it.duration == if (account.type == AccountType.TEAL) 182_000L else 182_320L })
            }
        }
        assertEquals(0, fixture.privateReads.get())
    }

    @Test
    fun unavailableDoesNotCreateAndConflictDoesNotOverwriteOrEraseLegacyBit() = fixture { fixture, path, prefs, http ->
        AtprotoSessions(prefs).connect(fixture.session(did), user, types)
        Room.databaseBuilder<PanoDb>(path).setDriver(BundledSQLiteDriver()).build().use { db ->
            val dao = db.getPendingScrobblesDao()
            val sender = AtprotoPendingPlays(dao, prefs) { http }
            assertTrue(sender.enqueue(play))
            val event = dao.allScrobblesForSubmission(10).single()
            dao.update(event.copy(services = event.services + AccountType.LASTFM))
            fixture.failReads.set(true)
            assertTrue(sender.drain(10))
            assertEquals(0, fixture.posts.size)
            fixture.failReads.set(false)
            val teal = AccountType.TEAL.playCollection
            fixture.records["at://$did/$teal/${event.atprotoTid}"] = Json.parseToJsonElement("""{"trackName":"Unrelated"}""").jsonObject
            assertFalse(sender.drain(10))
            val mixed = dao.legacyScrobblesForSubmission(atprotoMask, 10).single()
            assertEquals(setOf(AccountType.LASTFM, AccountType.TEAL), mixed.services)
            assertEquals(accountTypesToBitMask(setOf(AccountType.TEAL)), mixed.atprotoBlocked)
            assertNotNull(mixed.rockskyCid)
            dao.acknowledgeServices(mixed._id, accountTypesToBitMask(setOf(AccountType.LASTFM)))
            assertEquals(setOf(AccountType.TEAL), dao.allScrobblesForSubmission(10).single().services)
            assertEquals(1, fixture.posts.size)
            assertFalse(sender.drain(10))
            dao.delete(mixed)
            assertEquals(0, dao.count())
        }
    }

    @Test
    fun appPasswordExpiryRotatesOnceWithoutLosingAnotherDestination() = fixture { fixture, path, prefs, http ->
        AtprotoSessions(prefs).connect(fixture.session(did, "expired"), user, types)
        Room.databaseBuilder<PanoDb>(path).setDriver(BundledSQLiteDriver()).build().use { db ->
            val sender = AtprotoPendingPlays(db.getPendingScrobblesDao(), prefs) { http }
            assertTrue(sender.enqueue(play.copy(albumArtist = null)))
            assertFalse(sender.drain(10))
            val pending = db.getPendingScrobblesDao().allScrobblesForSubmission(10).single()
            assertEquals(setOf(AccountType.ROCKSKY), pending.services)
            assertNotNull(pending.tealCid)
            assertEquals(1, fixture.rotations.get())
            assertEquals("active", prefs.data.first().atprotoSession?.accessJwt)
            assertNull(prefs.data.first().atprotoRotation)
        }
    }

    private inline fun PanoDb.use(block: (PanoDb) -> Unit) = try {
        block(this)
    } finally {
        close()
    }

    private fun fixture(block: suspend (Fixture, String, androidx.datastore.core.DataStore<MainPrefs>, HttpClient) -> Unit) = runBlocking {
        val dir = createTempDirectory("at-play-").toFile()
        DesktopStuff.parseCmdlineArgs(arrayOf("--data-dir", dir.path))
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val prefs = DataStoreFactory.create(serializer = MainPrefs.dataStoreSerializer, scope = scope) { dir.resolve("prefs.json") }
        val fixture = Fixture()
        try {
            HttpClient(OkHttp) { followRedirects = false }.use { http ->
                block(fixture, dir.resolve("pano.db").path, prefs, http)
            }
        } finally {
            fixture.server.stop(0)
            scope.cancel()
            dir.deleteRecursively()
        }
    }

    private class Fixture {
        val dropRocksky = AtomicBoolean(false)
        val failReads = AtomicBoolean(false)
        val privateReads = AtomicInteger()
        val rotations = AtomicInteger()
        val posts = ConcurrentLinkedQueue<String>()
        val records = ConcurrentHashMap<String, JsonObject>()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { server ->
            server.createContext("/xrpc/") { request ->
                val auth = request.requestHeaders.getFirst("Authorization")
                val q = request.requestURI.rawQuery?.split('&')?.associate {
                    it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8)
                }.orEmpty()
                if (request.requestMethod == "GET" && (auth != null || request.requestHeaders.containsKey("DPoP")))
                    privateReads.incrementAndGet()
                when (request.requestURI.path.substringAfterLast('.')) {
                    "getRecord" -> {
                        val uri = "at://${q["repo"]}/${q["collection"]}/${q["rkey"]}"
                        if (failReads.get()) request.respond(503, """{"error":"Unavailable"}""")
                        else records[uri]?.let { request.respond(200, """{"uri":"$uri","cid":"$cid","value":$it}""") }
                            ?: request.respond(400, """{"error":"RecordNotFound"}""")
                    }
                    "listRecords" -> {
                        val rows = records.filterKeys { it.startsWith("at://${q["repo"]}/${q["collection"]}/") }.map { (uri, value) ->
                            """{"uri":"$uri","cid":"$cid","value":$value}"""
                        }
                        request.respond(200, """{"records":[${rows.joinToString(",") }]}""")
                    }
                    "createRecord" -> {
                        val payload = Json.parseToJsonElement(request.requestBody.readAllBytes().decodeToString()).jsonObject
                        val collection = payload.getValue("collection").jsonPrimitive.content
                        val uri = "at://${payload.getValue("repo").jsonPrimitive.content}/$collection/${payload.getValue("rkey").jsonPrimitive.content}"
                        posts += uri
                        when {
                            auth == "Bearer expired" -> request.respond(400, """{"error":"ExpiredToken"}""")
                            auth != "Bearer active" -> request.respond(401, "{}")
                            records.putIfAbsent(uri, payload.getValue("record").jsonObject) != null ->
                                request.respond(400, """{"error":"RecordAlreadyExists"}""")
                            collection == AccountType.ROCKSKY.playCollection && dropRocksky.getAndSet(false) -> request.close()
                            else -> request.respond(200, """{"uri":"$uri","cid":"$cid"}""")
                        }
                    }
                    "refreshSession" -> {
                        rotations.incrementAndGet()
                        if (auth == "Bearer fixture-refresh")
                            request.respond(200, """{"did":"$did","accessJwt":"active","refreshJwt":"rotated"}""")
                        else request.respond(401, "{}")
                    }
                    else -> request.respond(404, "{}")
                }
            }
            server.start()
        }

        fun session(owner: String, access: String = "active") =
            AtprotoAppPasswordSession(owner, "http://127.0.0.1:${server.address.port}", access, "fixture-refresh")

        private fun HttpExchange.respond(status: Int, value: String) {
            val bytes = value.encodeToByteArray()
            responseHeaders.set("Content-Type", "application/json")
            sendResponseHeaders(status, bytes.size.toLong())
            responseBody.use { it.write(bytes) }
        }
    }

    private companion object {
        const val did = "did:plc:abcdefghijklmnopqrstuvwx"
        const val otherDid = "did:plc:bcdefghijklmnopqrstuvwxy"
        const val cid = "bafyreihtjrpllq4g265etmj5uo5chx24pbi4mnawz4vr4zugasbv62ed5u"
        val user = UserCached("fixture.example", "", "", "", 0)
        val types = setOf(AccountType.TEAL, AccountType.ROCKSKY)
        val play = ScrobbleData("Fixture & Artist", "Étude", "Fixture album", 1_756_245_123_456, albumArtist = "Fixture ensemble", duration = 182_320, appId = null)
    }
}
