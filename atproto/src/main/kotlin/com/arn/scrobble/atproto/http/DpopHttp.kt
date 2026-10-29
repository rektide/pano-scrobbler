package com.arn.scrobble.atproto.http

import com.arn.scrobble.atproto.oauth.DpopProofFactory
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.takeFrom
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

internal enum class DpopServer { AUTHORIZATION, RESOURCE }

/** A response was received, but the protocol-mandated nonce was not usable. */
internal class DpopNonceException(val status: Int) : Exception("DPoP response is missing an unambiguous server nonce")

/** Instance is confined to one locked operation/authorization. No global account nonce cache. */
internal class DpopNonceStore(
    initial: Map<String, String> = emptyMap(),
    private val changed: (Map<String, String>) -> Unit = {},
) {
    private val values = initial.toMutableMap()
    fun snapshot(): Map<String, String> = values.toMap()
    fun get(server: DpopServer, url: String): String? = values[key(server, url)]
    fun set(server: DpopServer, url: String, nonce: String) {
        if (values.put(key(server, url), nonce) != nonce) changed(snapshot())
    }
    private fun key(server: DpopServer, url: String) = "${server.name}:${httpOrigin(url)}"
}

internal class DpopHttp(
    private val httpClient: HttpClient,
    private val proofFactory: DpopProofFactory,
    private val nonces: DpopNonceStore,
) {
    suspend fun request(
        url: String,
        method: HttpMethod,
        server: DpopServer,
        accessToken: String? = null,
        configure: HttpRequestBuilder.() -> Unit = {},
    ): BufferedHttpResponse {
        require(URI(url).scheme == "https" && URI(url).rawFragment == null) { "DPoP requests require HTTPS without a fragment" }
        repeat(2) { attempt ->
            val nonce = nonces.get(server, url)
            val proof = proofFactory.create(method.value, url, nonce, accessToken)
            val response = httpClient.boundedRequest(64 * 1024, onHeaders = { status, headers ->
                val received = headers.getAll("DPoP-Nonce")
                if (received?.size != 1 || received.single().length !in 1..4096 || received.single().any {
                        it != '\u0021' && it !in '\u0023'..'\u005B' && it !in '\u005D'..'\u007E'
                    })
                    throw DpopNonceException(status.value)
                nonces.set(server, url, received.single())
            }) {
                configure()
                this.url.takeFrom(url)
                this.method = method
                headers.remove("DPoP")
                headers.append("DPoP", proof)
                headers.remove(HttpHeaders.Authorization)
                if (accessToken != null) headers.append(HttpHeaders.Authorization, "DPoP $accessToken")
            }
            if (attempt != 0 || !response.isNonceChallenge(server)) return response
        }
        error("DPoP retry budget exhausted")
    }
}

private fun BufferedHttpResponse.isNonceChallenge(server: DpopServer): Boolean = when (server) {
    DpopServer.AUTHORIZATION -> status.value == 400 && runCatching {
        Json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
    }.getOrNull() == "use_dpop_nonce"
    DpopServer.RESOURCE -> status.value == 401 && headers.hasDpopError("use_dpop_nonce")
}
