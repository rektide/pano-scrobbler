package com.arn.scrobble.atproto.http

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

internal suspend fun HttpClient.fetchJson(
    url: String,
    acceptedTypes: Set<String> = setOf("application/json"),
    maxBytes: Int,
    json: Json,
): JsonElement {
    val response = boundedRequest(maxBytes) {
        url(url)
        method = HttpMethod.Get
        accept(ContentType.Application.Json)
    }
    require(response.status == HttpStatusCode.OK) { "AT Protocol discovery failed with HTTP ${response.status.value}" }
    require(response.headers["Content-Type"]?.substringBefore(';')?.trim()?.lowercase() in acceptedTypes) {
        "AT Protocol discovery returned an unsupported content type"
    }
    requireUnambiguousDiscoveryJson(response.body)
    return json.parseToJsonElement(response.body).also {
        require(it is JsonObject) { "AT Protocol discovery must return a JSON object" }
    }
}
