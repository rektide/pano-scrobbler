package com.arn.scrobble.api.atproto

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.arn.scrobble.utils.PlatformStuff
import com.arn.scrobble.utils.Stuff
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Only an unfinished attempt lives here, not the returned callback or the completed login. */
internal class AtprotoOAuthAttempts(file: File, scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
    private val data = DataStoreFactory.create(
        serializer = AttemptSerializer,
        corruptionHandler = ReplaceFileCorruptionHandler { Pending() },
        produceFile = { file },
        scope = scope,
    )

    suspend fun save(attempt: AtprotoOAuthAttempt) { data.updateData { Pending(attempt) } }

    suspend fun load(): AtprotoOAuthAttempt? {
        val attempt = data.data.first().attempt ?: return null
        return if (attempt.isActive()) attempt else {
            consume(attempt.state)
            null
        }
    }

    suspend fun consume(state: String): Boolean {
        var matched = false
        data.updateData {
            matched = it.attempt?.state == state
            if (matched) Pending() else it
        }
        return matched
    }

    suspend fun clear() { data.updateData { Pending() } }

    @Serializable
    private class Pending(val attempt: AtprotoOAuthAttempt? = null)

    @OptIn(ExperimentalSerializationApi::class)
    private object AttemptSerializer : Serializer<Pending> {
        override val defaultValue = Pending()

        override suspend fun readFrom(input: InputStream): Pending = try {
            Stuff.myJson.decodeFromStream(input)
        } catch (_: SerializationException) {
            throw CorruptionException("Invalid pending AT Protocol authorization")
        }

        override suspend fun writeTo(t: Pending, output: OutputStream) = Stuff.myJson.encodeToStream(t, output)
    }

    companion object {
        val shared by lazy { AtprotoOAuthAttempts(PlatformStuff.noBackupFilesDir.resolve("atproto-oauth-attempt.json")) }
    }
}
