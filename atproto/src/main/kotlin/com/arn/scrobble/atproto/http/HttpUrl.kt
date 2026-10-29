package com.arn.scrobble.atproto.http

import java.net.URI
import java.util.Locale

internal fun normalizeHtu(value: String): String {
    val uri = URI(URI(value).toASCIIString())
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    require(scheme == "http" || scheme == "https") { "DPoP URLs must use HTTP or HTTPS" }
    require(uri.userInfo == null) { "DPoP URLs must not contain user information" }
    require(uri.port in -1..65535 && uri.rawAuthority?.endsWith(':') != true) { "Invalid HTTP URL port" }
    val host = requireNotNull(uri.host) { "DPoP URLs must have a host" }.lowercase(Locale.ROOT)
    val renderedHost = when {
        host.startsWith('[') && host.endsWith(']') -> host
        ':' in host -> "[$host]"
        else -> host
    }
    val port = when {
        uri.port == -1 -> ""
        scheme == "http" && uri.port == 80 -> ""
        scheme == "https" && uri.port == 443 -> ""
        else -> ":${uri.port}"
    }
    return "$scheme://$renderedHost$port${uri.rawPath.ifEmpty { "/" }}"
}

internal fun requireHttpsUrl(value: String, label: String): URI {
    require(value.length <= 8 * 1024) { "$label URL is too long" }
    val uri = URI(value)
    require(uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.rawFragment == null) {
        "$label must be an HTTPS URL without credentials or fragments"
    }
    require(uri.port in -1..65535 && uri.rawAuthority?.endsWith(':') != true) { "Invalid $label port" }
    return uri
}

internal fun requireHttpsOrigin(value: String, label: String, canonical: Boolean = false): String {
    val uri = requireHttpsUrl(value, label)
    require(uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") { "$label must not contain a path" }
    require(uri.rawQuery == null) { "$label must not contain a query" }
    val origin = httpOrigin(value)
    if (canonical) require(value == origin) { "$label must be a canonical HTTPS origin" }
    return origin
}

internal fun httpOrigin(value: String): String {
    val normalized = URI(normalizeHtu(value))
    return "${normalized.scheme}://${normalized.rawAuthority}"
}
