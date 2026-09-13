package com.arn.scrobble.atproto.http

import com.arn.scrobble.atproto.oauth.DpopProofFactory
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.statement.bodyAsText
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
    private val nonces = ConcurrentHashMap<String, String>()

    fun get(origin: String): String? = nonces[origin]

    fun set(origin: String, nonce: String) {
        nonces[origin] = nonce
    }
}

internal class DpopHttp(
    private val httpClient: HttpClient,
    private val proofFactory: DpopProofFactory,
    private val nonces: DpopNonceStore = DpopNonceStore(),
) {
    suspend fun request(
        url: String,
        method: HttpMethod,
        server: DpopServer,
        accessToken: String? = null,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): BufferedHttpResponse {
        val origin = httpOrigin(url)

        repeat(2) { attempt ->
            val requestNonce = nonces.get(origin)
            val proof = proofFactory.create(
                method = method.value,
                url = url,
                nonce = requestNonce,
                accessToken = accessToken,
            )
            val response = httpClient.request {
                configure()
                this.url.takeFrom(url)
                this.method = method
                headers.remove("DPoP")
                headers.append("DPoP", proof)
                if (accessToken != null) {
                    headers.remove(HttpHeaders.Authorization)
                    headers.append(HttpHeaders.Authorization, "DPoP $accessToken")
                }
            }
            val buffered = BufferedHttpResponse(
                status = response.status,
                headers = response.headers,
                body = response.bodyAsText(),
            )
            val responseNonce = response.headers["DPoP-Nonce"]

            if (!responseNonce.isNullOrEmpty()) {
                nonces.set(origin, responseNonce)
            }

            val shouldRetry = attempt == 0 &&
                responseNonce != null &&
                responseNonce != requestNonce &&
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
        headers[HttpHeaders.WWWAuthenticate]
            ?.startsWith("DPoP", ignoreCase = true) == true &&
        headers[HttpHeaders.WWWAuthenticate]
            ?.contains("error=\"use_dpop_nonce\"", ignoreCase = true) == true
}
