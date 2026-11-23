package com.arn.scrobble.api.atproto

// Desktop preferences have no platform-backed key protection. Do not fall back to a file key.
actual val atprotoSessionStore: AtprotoSessionStore
    get() = throw AtprotoSessionStorageException("Protected AT Protocol session storage is unavailable on desktop")
