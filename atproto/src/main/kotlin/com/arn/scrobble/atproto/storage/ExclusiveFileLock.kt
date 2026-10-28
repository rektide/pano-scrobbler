package com.arn.scrobble.atproto.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

/** The lock file is stable: never rename or delete it when replacing vault data. */
class ExclusiveFileLock(private val file: File) {
    suspend fun <T> withLock(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        RandomAccessFile(file, "rw").use { handle ->
            val channel = handle.channel
            var lock: FileLock? = null
            try {
                while (lock == null) {
                    lock = try {
                        channel.tryLock()
                    } catch (_: OverlappingFileLockException) {
                        // Another instance in this JVM holds it; another process returns null.
                        null
                    }
                    if (lock == null) delay(25)
                }
                currentCoroutineContext().ensureActive()
                block()
            } finally {
                lock?.release()
            }
        }
    }
}
