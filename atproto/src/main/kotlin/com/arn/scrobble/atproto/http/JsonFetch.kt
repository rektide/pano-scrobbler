package com.arn.scrobble.atproto.http

import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

internal suspend fun HttpClient.fetchJson(
    url: String,
    acceptedTypes: Set<String> = setOf("application/json"),
    maxBytes: Int,
    json: Json,
): JsonElement {
    val requestedUrl = Url(url)
    val response = get(requestedUrl) {
        accept(ContentType.Application.Json)
    }

    require(response.call.request.url == requestedUrl) { "Redirects are not allowed during AT Protocol discovery" }
    require(response.status == HttpStatusCode.OK) {
        "AT Protocol discovery failed with HTTP ${response.status.value}"
    }
    val contentType = response.headers[HttpHeaders.ContentType]?.substringBefore(';')?.trim()?.lowercase()
    require(contentType in acceptedTypes) { "AT Protocol discovery returned an unsupported content type" }
    response.headers["Content-Length"]?.toLongOrNull()?.let {
        require(it <= maxBytes) { "AT Protocol discovery document is too large" }
    }

    val body = response.bodyAsText()
    require(body.toByteArray(Charsets.UTF_8).size <= maxBytes) {
        "AT Protocol discovery document is too large"
    }
    return json.parseToJsonElement(body)
}
