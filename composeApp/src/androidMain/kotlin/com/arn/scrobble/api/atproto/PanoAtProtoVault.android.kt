package com.arn.scrobble.api.atproto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import android.system.OsConstants
import com.arn.scrobble.atproto.storage.AesGcmVaultCipher
import com.arn.scrobble.atproto.storage.AtProtoAccountStore
import com.arn.scrobble.atproto.storage.VaultPersistence
import com.arn.scrobble.atproto.storage.readVaultBytes
import com.arn.scrobble.utils.AndroidStuff.applicationContext
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal actual fun createAtProtoAccountStore(): AtProtoAccountStore {
    val directory = File(applicationContext.noBackupFilesDir, "atproto")
    if (!directory.mkdirs() && !directory.isDirectory) throw IOException("Cannot create AT Protocol vault directory")
    val files = AndroidVaultPersistence(directory)
    // Called by the cipher only under the shared OS lock, including first key creation.
    val cipher = AesGcmVaultCipher {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = keyStore.getKey(VAULT_KEY_ALIAS, null)
        if (existing != null) {
            existing as? SecretKey ?: throw IOException("Invalid AT Protocol vault key")
        } else {
            // Never silently generate a new key over an existing encrypted vault.
            if (files.hasData()) throw IOException("AT Protocol vault key is missing")
            KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
                init(KeyGenParameterSpec.Builder(
                    VAULT_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setKeySize(256)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build())
            }.generateKey()
        }
    }
    return AtProtoAccountStore(files, cipher)
}

private const val VAULT_KEY_ALIAS = "com.arn.scrobble.atproto.vault.v1"

/** Android API 24+: explicit fsync + atomic same-directory rename, without swallowed I/O errors. */
internal class AndroidVaultPersistence(private val directory: File) : VaultPersistence {
    private val data = File(directory, "credentials.bin")
    override val lockFile = File(directory, "credentials.lock")

    fun hasData() = data.exists()

    override fun read(): ByteArray? = try {
        data.inputStream().use { it.readVaultBytes() }
    } catch (cause: FileNotFoundException) {
        if (data.exists()) throw cause else null
    }

    override fun write(bytes: ByteArray) {
        val temporary = File(directory, "credentials.new")
        try {
            FileOutputStream(temporary).use {
                it.write(bytes)
                it.fd.sync()
            }
            Os.rename(temporary.absolutePath, data.absolutePath)
            val directoryFd = Os.open(directory.absolutePath, OsConstants.O_RDONLY, 0)
            try {
                Os.fsync(directoryFd)
            } finally {
                Os.close(directoryFd)
            }
        } finally {
            // Only an uncommitted temporary file is removed; committed/corrupt state is never reset.
            if (temporary.exists() && !temporary.delete()) throw IOException("Cannot remove AT Protocol vault temporary file")
        }
    }
}
