package com.arn.scrobble.api.atproto

import com.arn.scrobble.atproto.storage.AtProtoAccountStore
import com.arn.scrobble.atproto.storage.JvmVaultPersistence
import com.arn.scrobble.atproto.storage.VaultCipher
import com.arn.scrobble.utils.PlatformStuff
import java.io.File

internal actual fun createAtProtoAccountStore() = AtProtoAccountStore(
    JvmVaultPersistence(File(PlatformStuff.filesDir, "atproto")),
    VaultCipher.Plaintext,
)
