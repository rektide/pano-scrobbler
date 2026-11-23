package com.arn.scrobble

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.arn.scrobble.api.atproto.*
import com.arn.scrobble.api.lastfm.ScrobbleData
import com.arn.scrobble.db.PanoDb
import com.arn.scrobble.db.RoomAtEnrollment
import com.arn.scrobble.db.AtDeliveryDao
import com.arn.scrobble.db.AtDelivery
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

class AtOutboxTest {
    @Test fun firstCommitWithoutResponseAndPartialTargetRetrySurviveRestart(): Unit = runBlocking {
        val root = Files.createTempDirectory("pano-at-outbox").toFile()
        val dbFile = File(root, "room.sqlite")
        fun db() = Room.databaseBuilder<PanoDb>(name = dbFile.absolutePath)
            .setDriver(BundledSQLiteDriver()).setQueryCoroutineContext(Dispatchers.IO).build()
        val created = ConcurrentHashMap<String, JsonObject>()
        val tealPosts = AtomicInteger()
        val rockPosts = AtomicInteger()
        val did = "did:plc:aaaaaaaaaaaaaaaaaaaaaaaa"
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val url = "http://127.0.0.1:${server.address.port}".toHttpUrl()
        val cid = "bafyreigqm2p2dftaihocdoxneyk7x6j5iijmahh2c7apug47zrhb6tdd3u"
        server.createContext("/") { req ->
            try {
                fun send(code: Int, response: String) {
                    val bytes = response.encodeToByteArray()
                    req.responseHeaders.set("Content-Type", "application/json")
                    req.sendResponseHeaders(code, bytes.size.toLong())
                    req.responseBody.write(bytes)
                }
                when (req.requestURI.path.substringAfterLast('/')) {
                    "com.atproto.server.createSession" -> send(200, """{"did":"$did","accessJwt":"fixture-access","refreshJwt":"fixture-refresh"}""")
                    "com.atproto.repo.createRecord" -> {
                        check(req.requestHeaders.getFirst("Authorization") == "Bearer fixture-access")
                        val body = Json.parseToJsonElement(req.requestBody.readAllBytes().decodeToString()).jsonObject
                        val collection = body.text("collection")!!
                        val uri = "at://$did/$collection/${body.text("rkey")}"
                        if (collection == AtTarget.TEAL.play) {
                            val call = tealPosts.incrementAndGet()
                            created[uri] = body.obj("record")!!
                            if (call > 1) send(200, """{"uri":"$uri","cid":"$cid"}""") // first write commits then drops response
                        } else if (rockPosts.incrementAndGet() == 1) send(503, """{"error":"Unavailable"}""")
                        else {
                            created[uri] = body.obj("record")!!
                            send(200, """{"uri":"$uri","cid":"$cid"}""")
                        }
                    }
                    "com.atproto.repo.getRecord" -> {
                        check(req.requestHeaders.getFirst("Authorization") == null)
                        val params = url.newBuilder().encodedQuery(req.requestURI.rawQuery).build()
                        val uri = "at://$did/${params.queryParameter("collection")}/${params.queryParameter("rkey")}"
                        val existing = created[uri]
                        if (existing == null) send(400, """{"error":"RecordNotFound"}""")
                        else send(200, """{"uri":"$uri","cid":"$cid","value":$existing}""")
                    }
                    else -> send(404, "{}")
                }
            } finally { req.close() }
        }
        server.start()
        try {
            val wire = AtWire { OkHttpClient.Builder().proxy(Proxy.NO_PROXY).addInterceptor { chain ->
                val req = chain.request()
                val dest = url.newBuilder().encodedPath(req.url.encodedPath).encodedQuery(req.url.encodedQuery).build()
                chain.proceed(req.newBuilder().url(dest).build())
            } }
            val vault = AtVault { object : AtVaultFiles {
                override val lockFile = File(root, "lock")
                private val file = File(root, "session")
                override fun read() = file.takeIf(File::exists)?.readBytes()
                override fun write(bytes: ByteArray) = file.writeBytes(bytes)
            } }
            var now = 1_700_000_000_000L
            lateinit var deliveryDao: AtDeliveryDao
            val enrollment = RoomAtEnrollment { deliveryDao }
            fun auth() = AtAuthority(vault, wire, wire, { AtIdentity(did, "https://pds.example.com/") }, { now }, {}, enrollment)
            val track = ScrobbleData("Artist", "Same song", "Album", now, albumArtist = "Artist", duration = 182_320, appId = null)
            var expectedTid: String? = null
            val firstDb = db()
            try {
                val room = firstDb
                deliveryDao = room.atDeliveryDao()
                val first = auth()
                first.appPassword(did, "test-test-test-test", AtSettings(completed = AtTarget.entries.toSet()), false)
                val before = AtOutbox({ room.atDeliveryDao() }, first, wire, {}, { now })
                assertTrue(before.submit(track))
                assertEquals(0, created.size) // foreground only stages and schedules
                assertTrue(before.drain())
                val pending = before.pending(did).single()
                assertEquals(AtTarget.ROCKSKY.name, pending.target)
                expectedTid = pending.tid
                assertEquals("RETRY", pending.state)
                assertEquals(1, created.size)
            } finally { firstDb.close() }
            val restartedDb = db()
            try {
                val room = restartedDb
                deliveryDao = room.atDeliveryDao()
                now += 120_000
                val after = AtOutbox({ room.atDeliveryDao() }, auth(), wire, {}, { now })
                assertFalse(after.drain())
                assertTrue(created.keys.all { it.endsWith("/$expectedTid") })
                assertEquals(2, created.size)
                assertEquals(1, tealPosts.get())
                assertEquals(2, rockPosts.get())
                assertTrue(after.submit(track)) // identical metadata and actual play timestamp, different event
                assertFalse(after.drain())
                assertEquals(4, created.size)
                assertEquals(2, created.keys.map { it.substringAfterLast('/') }.toSet().size)
                assertFalse(deliveryDao.containsEvent(did, expectedTid!!)) // acknowledged rows pruned

                var deniedVaultCalls = 0
                val denied = AtAuthority(AtVault { deniedVaultCalls++; throw SecurityException() }, wire, wire,
                    { AtIdentity(did, "https://pds.example.com/") }, { now }, {}, enrollment)
                val scheduled = mutableListOf<Boolean>()
                val deniedOutbox = AtOutbox({ deliveryDao }, denied, wire, { scheduled += it }, { now })
                assertTrue(deniedOutbox.submit(track))
                assertTrue(deniedOutbox.hasRunnable())
                assertEquals(0, deniedVaultCalls)
                assertEquals(listOf(false), scheduled)
                assertTrue(deniedOutbox.drain()) // worker retries; staging did not read the inaccessible vault
                assertEquals(AtProblem.STORAGE, denied.account().problem)
                assertEquals(2, deniedOutbox.pending(did).size)
                assertFalse(after.drain())

                // Emulate a v23 row already at the exact same ms/clockId and then restart creation twice.
                val collisionMicros = (now + 1000) * 1000
                val block = AtDelivery(did, "", "TEAL", now, "{}", null, state = "RECONNECT")
                val existing = atTidAt(collisionMicros, 42)
                deliveryDao.stage(listOf(block.copy(tid = existing)))
                val a = deliveryDao.stageEvent(listOf(block), collisionMicros, 42)
                val b = deliveryDao.stageEvent(listOf(block), collisionMicros, 42)
                assertEquals(3, setOf(existing, a, b).size)
                assertFalse(after.hasRunnable()) // all three are paused
                auth().appPassword(did, "test-test-test-test", AtSettings(), false) // fixture does not enqueue in connected hook
                assertTrue(after.hasRunnable())
                assertTrue(after.pending(did).all { it.state == "READY" })
            } finally { restartedDb.close() }
        } finally { server.stop(0); root.deleteRecursively() }
    }
}
