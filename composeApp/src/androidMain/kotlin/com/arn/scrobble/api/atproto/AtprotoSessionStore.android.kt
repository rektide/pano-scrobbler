package com.arn.scrobble.api.atproto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.datastore.core.MultiProcessDataStoreFactory
import com.arn.scrobble.utils.AndroidStuff
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

actual val atprotoSessionStore: AtprotoSessionStore by lazy {
    AtprotoSessionStore(
        MultiProcessDataStoreFactory.create(
            serializer = AtprotoSessionSerializer(::atprotoSessionKey),
            // The UI and :bgScrobbler use the same store. Exclude it and its sidecars from backup and transfer.
            produceFile = { AndroidStuff.applicationContext.noBackupFilesDir.resolve("atproto-sessions.v1") },
        )
    )
}

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val KEY_ALIAS = "atproto-sessions-v1"

private fun atprotoSessionKey(createIfMissing: Boolean): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    val existing = keyStore.getKey(KEY_ALIAS, null)
    if (existing != null) return existing as SecretKey
    // DataStore serializes writes across processes, including creation of the first key.
    check(createIfMissing) { "AT Protocol session key unavailable" }
    return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply {
        init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
    }.generateKey()
}
