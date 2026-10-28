package com.arn.scrobble.atproto.http

import java.net.URI
import java.util.Locale

internal fun normalizeHtu(value: String): String {
    val uri = URI(value)
    val scheme = uri.scheme?.lowercase(Locale.ROOT)
    require(scheme == "http" || scheme == "https") { "DPoP URLs must use HTTP or HTTPS" }
    require(uri.userInfo == null) { "DPoP URLs must not contain user information" }
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

internal fun httpOrigin(value: String): String {
    val normalized = URI(normalizeHtu(value))
    return "${normalized.scheme}://${normalized.rawAuthority}"
}
