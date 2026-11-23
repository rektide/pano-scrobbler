package com.arn.scrobble

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.arn.scrobble.api.AccountType
import com.arn.scrobble.api.UserAccountSerializable
import com.arn.scrobble.api.UserCached
import com.arn.scrobble.api.atproto.AtprotoAppPasswordSession
import com.arn.scrobble.api.atproto.AtprotoSessions
import com.arn.scrobble.api.lastfm.ApiException
import com.arn.scrobble.api.withScrobbleAccount
import com.arn.scrobble.db.PanoDb
import com.arn.scrobble.pref.ImExporter
import com.arn.scrobble.pref.MainPrefs
import com.arn.scrobble.utils.DesktopStuff
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AtprotoMainPrefsTest {
    @Test
    fun appPasswordLoginReopensForBothDestinationsAndRotatesOnlyTheSession() = withPrefsFile { file ->
        file.writeText("""{"scrobblerEnabled":false}""")
        file.open { prefs ->
            assertFalse(prefs.data.first().scrobblerEnabled)
            assertNull(prefs.data.first().atprotoSession)
            AtprotoSessions(prefs).connect(appSession(), user, destinations)
        }
        val json = Json.parseToJsonElement(file.readText()).jsonObject
        val accounts = json.getValue("scrobbleAccounts").jsonArray.toString()
        assertFalse("synthetic-" in accounts)
        assertEquals(setOf("type", "did", "pds", "accessJwt", "refreshJwt"), json.getValue("atprotoSession").jsonObject.keys)

        val before = file.open { prefs ->
            val accountsBefore = prefs.data.first().scrobbleAccounts
            for (account in accountsBefore) {
                assertEquals(DID, account.authKey)
                assertEquals(PDS, account.apiRoot)
                val restored = assertIs<AtprotoAppPasswordSession>(AtprotoSessions(prefs).restore(account))
                assertEquals("synthetic-access", restored.accessJwt)
                assertEquals("synthetic-refresh", restored.refreshJwt)
            }
            AtprotoSessions(prefs).replaceSession(appSession(access = "synthetic-rotated"))
            accountsBefore
        }
        file.open { prefs ->
            assertEquals(before, prefs.data.first().scrobbleAccounts)
            val sessions = AtprotoSessions(prefs)
            for (account in before)
                assertEquals("synthetic-rotated", assertIs<AtprotoAppPasswordSession>(sessions.restore(account)).accessJwt)
            assertNull(sessions.restore(before.first().copy(authKey = OTHER_DID)))
            assertNull(sessions.restore(before.first().copy(apiRoot = "https://stale.example")))
            assertFailsWith<IllegalStateException> { sessions.replaceSession(appSession(did = OTHER_DID)) }
        }
    }

    @Test
    fun retiredOAuthSessionRequiresReconnectWithoutResettingPrefs() = withPrefsFile { file ->
        val unrelated = UserAccountSerializable(AccountType.GNUFM, user, "existing-auth", "https://other.example")
        val before = file.open { prefs ->
            val sessions = AtprotoSessions(prefs)
            sessions.connect(appSession(), user, destinations)
            prefs.updateData { p ->
                p.copy(scrobblerEnabled = false, currentAccountType = AccountType.ROCKSKY, scrobbleAccounts = p.scrobbleAccounts.map {
                    if (it.type == AccountType.TEAL) it.copy(canScrobble = false) else it
                } + unrelated)
            }
        }
        val legacy = Json.parseToJsonElement("""{
            "type":"oauth","did":"$DID","pds":"$PDS","issuer":"https://issuer.example",
            "clientId":"https://client.example/metadata.json","scopes":["atproto"],
            "expiresAt":"2026-01-01T12:00:00Z","accessToken":"retired-access",
            "refreshToken":"retired-refresh","dpop":"retired-private-key"
        }""")
        val saved = Json.parseToJsonElement(file.readText()).jsonObject
        file.writeText(JsonObject(saved + ("atprotoSession" to legacy)).toString())
        file.open { prefs ->
            val p = prefs.data.first()
            assertEquals(before.copy(atprotoSession = null), p)
            assertEquals(DID, p.atprotoConnection?.did)
            assertTrue(p.atprotoConnection?.needsReconnect == true)
            val sessions = AtprotoSessions(prefs)
            assertTrue(p.scrobbleAccounts.all { sessions.restore(it) == null })
            sessions.connect(appSession(), user, destinations)
        }
        assertFalse("retired-" in file.readText())
        file.open { prefs ->
            val p = prefs.data.first()
            assertEquals(before.scrobbleAccounts, p.scrobbleAccounts)
            assertEquals(AccountType.ROCKSKY, p.currentAccountType)
            assertFalse(p.scrobblerEnabled)
            assertEquals(false, p.atprotoConnection?.needsReconnect)
        }
    }

    @Test
    fun switchDisableAndDisconnectAreDurable() = withPrefsFile { file ->
        val unrelated = UserAccountSerializable(AccountType.GNUFM, user, "existing-auth", "https://other.example")
        val old = file.open { prefs ->
            prefs.updateData { it.copy(scrobbleAccounts = listOf(unrelated)) }
            AtprotoSessions(prefs).connect(appSession(), user, destinations)
            prefs.data.first().scrobbleAccounts.single { it.type == AccountType.TEAL }
        }
        file.open { prefs -> AtprotoSessions(prefs).connect(appSession(did = OTHER_DID), user, destinations) }
        file.open { prefs ->
            val sessions = AtprotoSessions(prefs)
            val entries = prefs.data.first().scrobbleAccounts.filter { it.type.isAtproto }
            assertEquals(destinations, entries.map { it.type }.toSet())
            assertTrue(entries.all { it.authKey == OTHER_DID })
            assertNull(sessions.restore(old))
            sessions.disable(AccountType.TEAL)
            assertNull(sessions.restore(entries.single { it.type == AccountType.TEAL }))
        }
        file.open { prefs ->
            val sessions = AtprotoSessions(prefs)
            val rocksky = prefs.data.first().scrobbleAccounts.single { it.type.isAtproto }
            assertEquals(AccountType.ROCKSKY, rocksky.type)
            assertEquals(OTHER_DID, sessions.restore(rocksky)?.did)
            sessions.connect(appSession(did = OTHER_DID), user, destinations)
            sessions.disconnect()
        }
        file.open { prefs ->
            assertNull(prefs.data.first().atprotoSession)
            assertEquals(listOf(unrelated), prefs.data.first().scrobbleAccounts)
            assertFailsWith<IllegalStateException> { AtprotoSessions(prefs).replaceSession(appSession(did = OTHER_DID)) }
        }
    }

    @Test
    fun duplicateRootIsExemptOnlyForTheAtPairAndFailureLeavesPrefsIntact() = withPrefsFile { file ->
        val otherService = UserAccountSerializable(AccountType.GNUFM, user, "other-key", PDS)
        file.open { prefs ->
            val sessions = AtprotoSessions(prefs)
            sessions.connect(appSession(), user, destinations)
            val entries = prefs.data.first().scrobbleAccounts
            assertFailsWith<ApiException> { entries.withScrobbleAccount(otherService) }
            assertFailsWith<ApiException> { listOf(otherService).withScrobbleAccount(otherService.copy(type = AccountType.PLEROMA)) }
            assertEquals(1, listOf(otherService).withScrobbleAccount(otherService.copy(canScrobble = false)).size)
            prefs.updateData { it.copy(scrobbleAccounts = entries + otherService.copy(apiRoot = "https://occupied.example")) }
            assertFailsWith<ApiException> {
                sessions.connect(appSession(OTHER_DID, pds = "https://occupied.example"), user, destinations)
            }
        }
        file.open { prefs ->
            val p = prefs.data.first()
            assertEquals(DID, p.atprotoSession?.did)
            assertEquals(setOf(DID), p.scrobbleAccounts.filter { it.type.isAtproto }.map { it.authKey }.toSet())
        }
    }

    @Test
    fun publicExportOmitsLoginAndPublicImportCannotOverwriteIt() = withPrefsFile { file ->
        val session = appSession()
        file.open { prefs ->
            AtprotoSessions(prefs).connect(session, user, destinations)
            // Loading PanoDb initializes its desktop singleton before Room builds the test database.
            DesktopStuff.parseCmdlineArgs(arrayOf("--data-dir", file.parent))
            val db = Room.inMemoryDatabaseBuilder<PanoDb>().setDriver(BundledSQLiteDriver()).build()
            try {
                val exporter = ImExporter(db, prefs)
                val output = ByteArrayOutputStream()
                assertTrue(exporter.export(output))
                val exported = output.toString(Charsets.UTF_8.name())
                for (secret in listOf("atprotoSession", "scrobbleAccounts", DID, PDS, session.accessJwt, session.refreshJwt))
                    assertFalse(secret in exported)

                val injected = """{"pano_version":1,"settings":{"scrobblerEnabled":false,"atprotoSession":null,"scrobbleAccounts":[]}}"""
                assertEquals(setOf(ImExporter.ImportTypes.settings), exporter.createImportTypes(injected.byteInputStream()))
                exporter.import(setOf(ImExporter.ImportTypes.settings), ImExporter.WriteMode.replace_all)
            } finally {
                db.close()
            }
        }
        file.open { prefs ->
            val imported = prefs.data.first()
            assertFalse(imported.scrobblerEnabled)
            assertEquals(DID, imported.atprotoSession?.did)
            assertEquals(destinations, imported.scrobbleAccounts.map { it.type }.toSet())
            val restored = assertIs<AtprotoAppPasswordSession>(AtprotoSessions(prefs).restore(imported.scrobbleAccounts.first()))
            assertEquals(session.accessJwt, restored.accessJwt)
            assertEquals(session.refreshJwt, restored.refreshJwt)
        }
    }

    private fun appSession(did: String = DID, access: String = "synthetic-access", pds: String = PDS) =
        AtprotoAppPasswordSession(did, pds, access, "synthetic-refresh")

    private fun withPrefsFile(block: suspend (File) -> Unit) = runBlocking {
        val dir = createTempDirectory("atproto-prefs-test-").toFile()
        try {
            block(dir.resolve(MainPrefs.FILE_NAME))
        } finally {
            check(dir.deleteRecursively())
        }
    }

    private suspend fun <T> File.open(block: suspend (DataStore<MainPrefs>) -> T): T {
        val job = Job()
        val store = DataStoreFactory.create(MainPrefs.dataStoreSerializer, scope = CoroutineScope(Dispatchers.IO + job)) { this }
        return try {
            block(store)
        } finally {
            job.cancelAndJoin()
        }
    }

    private companion object {
        const val DID = "did:web:one.example"
        const val OTHER_DID = "did:web:two.example"
        const val PDS = "https://pds.example"
        val destinations = setOf(AccountType.TEAL, AccountType.ROCKSKY)
        val user = UserCached("one.example", "", "Example", "", 0)
    }
}
