package com.arn.scrobble.atproto.http

import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.plugins.HttpRedirect
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.pluginOrNull
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import java.io.ByteArrayOutputStream

internal data class BufferedHttpResponse(val status: HttpStatusCode, val headers: Headers, val body: String) {
    override fun toString() = "BufferedHttpResponse(status=$status, body=<redacted>)"
}

/** Carries only a received status across the bounded-reader seam, never a body or credential. */
internal class HttpResponseBodyException(val status: Int, val failure: Exception) : Exception("AT Protocol response body could not be read", failure)

/** Streaming execute, not request()/bodyAsText(): enforce limits before allocating the whole body. */
internal suspend fun HttpClient.boundedRequest(
    maxBytes: Int,
    onHeaders: (HttpStatusCode, Headers) -> Unit = { _, _ -> },
    configure: HttpRequestBuilder.() -> Unit,
): BufferedHttpResponse {
    require(pluginOrNull(HttpRedirect) == null && pluginOrNull(HttpRequestRetry) == null) {
        "AT Protocol transport must disable redirects and automatic retries"
    }
    return prepareRequest {
    expectSuccess = false
    configure()
}.execute { response ->
    onHeaders(response.status, response.headers)
    try {
        response.headers["Content-Length"]?.toLongOrNull()?.let {
            require(it in 0..maxBytes.toLong()) { "AT Protocol response exceeds its size limit" }
        }
        val input = response.bodyAsChannel()
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = input.readAvailable(buffer, 0, minOf(buffer.size, maxBytes + 1 - output.size()))
            if (count == -1) break
            output.write(buffer, 0, count)
            require(output.size() <= maxBytes) { "AT Protocol response exceeds its size limit" }
        }
        BufferedHttpResponse(response.status, response.headers, output.toByteArray().decodeToString(throwOnInvalidSequence = true))
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        throw HttpResponseBodyException(response.status.value, cause)
    }
}
}
