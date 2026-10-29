package com.arn.scrobble.atproto.http

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.prepareGet
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal suspend fun HttpClient.fetchJson(
    url: String,
    acceptedTypes: Set<String> = setOf("application/json"),
    maxBytes: Int,
    json: Json,
): JsonElement {
    requireProtocolTransport()
    val requestedUrl = Url(url)
    return prepareGet(requestedUrl) {
        accept(ContentType.Application.Json)
        expectSuccess = false
    }.execute { response ->
        require(response.call.request.url == requestedUrl) { "Redirects are not allowed during AT Protocol discovery" }
        require(response.status == HttpStatusCode.OK) {
            "AT Protocol discovery failed with HTTP ${response.status.value}"
        }
        val contentType = response.headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim()?.lowercase()
        require(contentType in acceptedTypes) { "AT Protocol discovery returned an unsupported content type" }
        val text = response.readBoundedUtf8(maxBytes)
        requireUnambiguousDiscoveryJson(text)
        json.parseToJsonElement(text).also {
            require(it is JsonObject) { "AT Protocol discovery must return a JSON object" }
        }
    }
}
