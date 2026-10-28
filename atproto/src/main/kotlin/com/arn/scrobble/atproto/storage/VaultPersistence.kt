package com.arn.scrobble.atproto.storage

import java.io.File
import java.io.IOException
import java.io.InputStream

/** Platform storage supplies atomic replacement; called only while [lockFile] is locked. */
interface VaultPersistence {
    val lockFile: File
    fun read(): ByteArray?
    fun write(bytes: ByteArray)
}

interface VaultCipher {
    fun encrypt(plaintext: ByteArray): ByteArray
    fun decrypt(ciphertext: ByteArray): ByteArray

    /** Only for a genuinely user-private desktop file, not Android credentials. */
    object Plaintext : VaultCipher {
        override fun encrypt(plaintext: ByteArray) = plaintext
        override fun decrypt(ciphertext: ByteArray) = ciphertext
    }
}

const val MAX_VAULT_BYTES = 4 * 1024 * 1024

fun InputStream.readVaultBytes(): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) return output.toByteArray()
        if (output.size() + count > MAX_VAULT_BYTES) throw IOException("AT Protocol vault is too large")
        output.write(buffer, 0, count)
    }
}

class VaultCorruptionException(cause: Exception) : IOException("Cannot decode AT Protocol credential vault", cause)
class StaleAuthorizationException : IllegalStateException("AT Protocol authorization is no longer current")
class StaleSessionException : IllegalStateException("AT Protocol session is no longer current")
class IncompleteSessionUpdateException : IllegalStateException(
    "AT Protocol session update did not finish durably; authorize again",
)
