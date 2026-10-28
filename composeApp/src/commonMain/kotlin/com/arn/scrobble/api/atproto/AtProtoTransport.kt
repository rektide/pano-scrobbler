package com.arn.scrobble.api.atproto

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttpConfig

/** Must run after base policy; setting only Ktor's flag does not constrain the engine. */
internal fun HttpClientConfig<OkHttpConfig>.atProtoCredentialPolicy() {
    followRedirects = false
    expectSuccess = false
    engine {
        config {
            followRedirects(false)
            followSslRedirects(false)
            // Refresh tokens are single-use. Transport retries cannot prove non-consumption.
            retryOnConnectionFailure(false)
        }
    }
}
