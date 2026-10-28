package com.arn.scrobble.api.atproto

import com.arn.scrobble.atproto.storage.AtProtoAccountStore

/** Internal foundation only; login and posting will share this authority. */
internal object PanoAtProtoVault {
    val accounts by lazy { createAtProtoAccountStore() }
}

internal expect fun createAtProtoAccountStore(): AtProtoAccountStore
