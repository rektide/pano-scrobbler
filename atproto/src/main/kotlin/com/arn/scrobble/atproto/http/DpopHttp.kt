package com.arn.scrobble.atproto.http

import com.arn.scrobble.atproto.oauth.DpopProofFactory
import io.ktor.client.HttpClient
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.prepareRequest
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.takeFrom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap
import java.net.URI

internal enum class DpopServer {
    AUTHORIZATION,
    RESOURCE,
}

internal data class BufferedHttpResponse(
    val status: HttpStatusCode,
    val headers: Headers,
    val body: String,
)

internal class DpopNonceStore {
    private val nonces = ConcurrentHashMap<Pair<DpopServer, String>, String>()

    fun get(origin: String, server: DpopServer): String? = nonces[server to origin]

    fun set(origin: String, server: DpopServer, nonce: String) {
        nonces[server to origin] = nonce
    }
}

internal class DpopHttp(
    private val httpClient: HttpClient,
    private val proofFactory: DpopProofFactory,
    private val nonces: DpopNonceStore = DpopNonceStore(),
    private val maxResponseBytes: Int = 64 * 1024,
) {
    suspend fun request(
        url: String,
        method: HttpMethod,
        server: DpopServer,
        accessToken: String? = null,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): BufferedHttpResponse {
        httpClient.requireProtocolTransport()
        require(URI(url).scheme == "https" && URI(url).rawFragment == null) { "DPoP requests require an HTTPS URL without a fragment" }
        val origin = httpOrigin(url)

        repeat(2) { attempt ->
            val requestNonce = nonces.get(origin, server)
            val proof = proofFactory.create(
                method = method.value,
                url = url,
                nonce = requestNonce,
                accessToken = accessToken,
            )
            val buffered = httpClient.prepareRequest {
                configure()
                expectSuccess = false
                this.url.takeFrom(url)
                this.method = method
                headers.remove("DPoP")
                headers.append("DPoP", proof)
                headers.remove(HttpHeaders.Authorization)
                if (accessToken != null) {
                    headers.append(HttpHeaders.Authorization, "DPoP $accessToken")
                }
            }.execute { response ->
                val nonce = response.headers.requireDpopNonce()
                nonces.set(origin, server, nonce)
                BufferedHttpResponse(
                    status = response.status,
                    headers = response.headers,
                    body = response.readBoundedUtf8(maxResponseBytes),
                )
            }

            val shouldRetry = attempt == 0 &&
                buffered.isUseDpopNonceError(server)
            if (!shouldRetry) return buffered
        }

        error("DPoP retry budget exhausted")
    }
}

private fun BufferedHttpResponse.isUseDpopNonceError(server: DpopServer): Boolean = when (server) {
    DpopServer.AUTHORIZATION -> status == HttpStatusCode.BadRequest &&
        runCatching {
            Json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
        }.getOrNull() == "use_dpop_nonce"

    DpopServer.RESOURCE -> status == HttpStatusCode.Unauthorized &&
        headers.hasDpopError("use_dpop_nonce")
}

private fun Headers.requireDpopNonce(): String {
    val nonce = getAll("DPoP-Nonce")?.singleOrNull()
    require(nonce != null && nonce.length in 1..4096 && nonce.all {
        it == '\u0021' || it in '\u0023'..'\u005B' || it in '\u005D'..'\u007E'
    }) { "DPoP responses must contain one valid DPoP-Nonce header" }
    return nonce
}
