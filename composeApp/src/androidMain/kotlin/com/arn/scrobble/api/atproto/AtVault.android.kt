package com.arn.scrobble.api.atproto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.arn.scrobble.utils.AndroidStuff.applicationContext
import java.io.File
import java.io.FileNotFoundException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal actual fun platformAtVault(allowDesktopFile: Boolean): AtVaultFiles = AndroidAtVault()

private class AndroidAtVault : AtVaultFiles {
    private val dir = File(applicationContext.noBackupFilesDir, "atproto").apply { mkdirs() }
    private val file = AtomicFile(File(dir, "authority"))
    override val lockFile = File(dir, "authority.lock")

    private fun key(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        check(create)
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    override fun read(): ByteArray? {
        val bytes = try { file.readFully() } catch (e: FileNotFoundException) {
            // Never replace a vault if its file exists but cannot be read.
            if (file.baseFile.exists() || File(dir, "authority.bak").exists()) throw e
            return null
        }
        check(bytes.size in 29..1_048_576 && bytes[0] == 1.toByte())
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(false), GCMParameterSpec(128, bytes.copyOfRange(1, 13)))
        }.doFinal(bytes, 13, bytes.size - 13)
    }

    override fun write(bytes: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(true)) }
        check(cipher.iv.size == 12)
        val encrypted = byteArrayOf(1) + cipher.iv + cipher.doFinal(bytes)
        val stream = file.startWrite()
        try {
            stream.write(encrypted)
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
            throw e
        }
        check(file.readFully().contentEquals(encrypted))
    }

    private companion object { const val ALIAS = "pano.atproto.authority.v1" }
}
