package com.arn.scrobble.atproto.http

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray

/** A dedicated client must not follow credentials or replay a non-idempotent write. */
internal fun HttpClient.requireProtocolTransport() {
    require(pluginOrNull(HttpRedirect) == null) { "AT Protocol HTTP clients must disable redirects" }
    require(pluginOrNull(HttpRequestRetry) == null) { "AT Protocol HTTP clients must disable automatic retries" }
}

/** Call only inside HttpStatement.execute { }, before Ktor's eager response saving. */
internal suspend fun HttpResponse.readBoundedUtf8(maxBytes: Int): String {
    require(maxBytes in 1..16 * 1024 * 1024) { "Invalid AT Protocol response byte limit" }
    headers[HttpHeaders.ContentLength]?.toLongOrNull()?.let {
        require(it in 0..maxBytes.toLong()) { "AT Protocol response is too large" }
    }
    val bytes = bodyAsChannel().readBuffer(maxBytes.toLong() + 1).readByteArray()
    require(bytes.size <= maxBytes) { "AT Protocol response is too large" }
    return bytes.decodeToString(throwOnInvalidSequence = true)
}
