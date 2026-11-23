package com.arn.scrobble.api.atproto

import com.arn.scrobble.api.lastfm.ApiException
import com.arn.scrobble.utils.Stuff
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import io.ktor.http.isSuccess
import io.ktor.utils.io.readBuffer
import kotlinx.io.readString
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.serializer
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.InetAddress
import java.net.UnknownHostException

class AtprotoOAuthServer(
    val issuer: String,
    val authorization: HttpUrl,
    val par: HttpUrl,
    val token: HttpUrl,
)

suspend fun HttpClient.discoverAtprotoOAuth(pds: String): AtprotoOAuthServer {
    val pdsOrigin = oauthOrigin(pds) ?: throw ApiException(-1, "Invalid AT Protocol resource origin")
    val resource = oauthMetadata<ProtectedResource>(pdsOrigin, "oauth-protected-resource")
    if (oauthOrigin(resource.resource) != pdsOrigin)
        throw ApiException(-1, "AT Protocol resource does not match the account's PDS")
    val issuer = resource.authorizationServers.singleOrNull()?.let(::oauthOrigin)
        ?: throw ApiException(-1, "AT Protocol resource did not name one authorization server")
    val server = oauthMetadata<AuthorizationServer>(issuer, "oauth-authorization-server")
    if (server.issuer != issuer.oauthOrigin() || !server.parRequired || !server.issuerInResponse ||
        !server.clientMetadataSupported || server.registerRequestUri == false ||
        "atproto" !in server.scopes || "code" !in server.responseTypes || "S256" !in server.pkceMethods ||
        "ES256" !in server.dpopAlgorithms || !server.grants.containsAll(listOf("authorization_code", "refresh_token")) ||
        !server.tokenAuthMethods.containsAll(listOf("none", "private_key_jwt")) ||
        "ES256" !in server.tokenAuthAlgorithms || "none" in server.tokenAuthAlgorithms
    ) throw ApiException(-1, "Authorization server does not support the required AT Protocol OAuth profile")

    fun endpoint(raw: String): HttpUrl = oauthUrl(raw)?.takeIf { it.oauthOrigin() == server.issuer }
        ?: throw ApiException(-1, "Invalid AT Protocol OAuth endpoint")

    // Keep the browser and all credential-bearing requests on the verified issuer origin.
    return AtprotoOAuthServer(server.issuer, endpoint(server.authorization), endpoint(server.par), endpoint(server.token))
}

private suspend inline fun <reified T> HttpClient.oauthMetadata(origin: HttpUrl, document: String): T =
    prepareGet(origin.newBuilder().addPathSegments(".well-known/$document").build().toString()) {
        accept(ContentType.Application.Json)
    }.execute { response ->
        if (response.status != HttpStatusCode.OK)
            throw ApiException(response.status.value, "AT Protocol OAuth discovery failed")
        response.readOAuthJson(serializer<T>())
    }

suspend fun <T : Any> HttpClient.oauthPost(
    url: HttpUrl,
    dpop: AtprotoOAuthDpop,
    serializer: DeserializationStrategy<T>,
    parameters: List<Pair<String, String>>,
): T {
    val form = parameters.formUrlEncode()
    repeat(2) { attempt ->
        val result = preparePost(url.toString()) {
            header("DPoP", dpop.proof("POST", url))
            accept(ContentType.Application.Json)
            setBody(AtprotoPostContent(form, ContentType.Application.FormUrlEncoded))
        }.execute { response ->
            val nonce = response.headers.getAll("DPoP-Nonce")?.singleOrNull()
                ?.takeIf { it.length in 1..4096 && it.all { char -> char in '!'..'~' && char != ',' } }
            if (response.status.isSuccess()) {
                if (nonce == null) throw ApiException(-1, "Missing AT Protocol DPoP nonce")
                dpop.rememberNonce(url, nonce)
                return@execute response.readOAuthJson(serializer)
            }
            if (attempt == 0 && nonce != null && response.status.value in listOf(400, 401) &&
                response.readOAuthJson(OAuthError.serializer()).error == "use_dpop_nonce"
            ) {
                dpop.rememberNonce(url, nonce)
                return@execute null
            }
            throw ApiException(response.status.value, "AT Protocol OAuth request failed")
        }
        if (result != null) return result
    }
    error("Unreachable OAuth nonce retry")
}

private suspend fun <T> HttpResponse.readOAuthJson(serializer: DeserializationStrategy<T>): T {
    val invalid = "Invalid AT Protocol OAuth response"
    if (contentType()?.withoutParameters() != ContentType.Application.Json)
        throw ApiException(-1, invalid)
    val limit = 128 * 1024L
    val body = bodyAsChannel().readBuffer(limit + 1)
    if (body.size > limit) throw ApiException(-1, invalid)
    return try {
        Stuff.myJson.decodeFromString(serializer, body.readString())
    } catch (_: SerializationException) {
        // Token responses can contain credentials even when JSON decoding fails.
        throw ApiException(-1, invalid)
    }
}

private fun oauthOrigin(raw: String): HttpUrl? {
    val uri = parseOAuthUri(raw) ?: return null
    if (uri.rawPath !in listOf("", "/") || uri.port == 443) return null
    return oauthUrl(raw)
}

private fun oauthUrl(raw: String): HttpUrl? {
    val uri = parseOAuthUri(raw) ?: return null
    if (uri.scheme != "https" || uri.host?.isPublicAtprotoHost() != true || uri.rawUserInfo != null ||
        uri.rawQuery != null || uri.rawFragment != null || (uri.port != -1 && uri.port !in 1..65535)
    ) return null
    return raw.toHttpUrlOrNull()
}

fun HttpUrl.oauthOrigin(): String = newBuilder().encodedPath("/")
    .query(null).fragment(null).build().toString().removeSuffix("/")

fun String.isPublicAtprotoHost(): Boolean {
    val host = trimEnd('.').lowercase()
    return '.' in host && host.substringAfterLast('.').any { it in 'a'..'z' } &&
        !host.endsWith(".local") && !host.endsWith(".localhost") && !host.endsWith(".internal") &&
        !host.endsWith(".lan") && !host.endsWith(".home.arpa") && ':' !in host
}

val atprotoPublicDns = Dns { host ->
    val addresses = Dns.SYSTEM.lookup(host).filter(InetAddress::isPublicAtprotoAddress)
    addresses.ifEmpty { throw UnknownHostException("No public address for AT Protocol OAuth") }
}

private fun InetAddress.isPublicAtprotoAddress(): Boolean {
    if (isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isSiteLocalAddress || isMulticastAddress)
        return false
    val octets = address
    val a = octets[0].toInt() and 0xff
    val b = octets[1].toInt() and 0xff
    val c = octets[2].toInt() and 0xff
    return if (octets.size == 4) {
        a !in listOf(0, 10, 127) && a < 224 && !(a == 100 && b in 64..127) &&
            !(a == 169 && b == 254) && !(a == 172 && b in 16..31) && !(a == 192 && b == 168) &&
            !(a == 192 && b == 0 && c in listOf(0, 2)) && !(a == 198 && b in 18..19) &&
            !(a == 198 && b == 51 && c == 100) && !(a == 203 && b == 0 && c == 113)
    } else {
        a and 0xe0 == 0x20 && !(a == 0x20 && b == 0x01 && c == 0x0d && (octets[3].toInt() and 0xff) == 0xb8) &&
            !(a == 0x20 && b == 0x02)
    }
}

@Serializable
private class ProtectedResource(
    val resource: String,
    @SerialName("authorization_servers") val authorizationServers: List<String>,
)

@Serializable
private class AuthorizationServer(
    val issuer: String,
    @SerialName("authorization_endpoint") val authorization: String,
    @SerialName("pushed_authorization_request_endpoint") val par: String,
    @SerialName("token_endpoint") val token: String,
    @SerialName("scopes_supported") val scopes: List<String>,
    @SerialName("response_types_supported") val responseTypes: List<String>,
    @SerialName("code_challenge_methods_supported") val pkceMethods: List<String>,
    @SerialName("grant_types_supported") val grants: List<String>,
    @SerialName("dpop_signing_alg_values_supported") val dpopAlgorithms: List<String>,
    @SerialName("token_endpoint_auth_methods_supported") val tokenAuthMethods: List<String>,
    @SerialName("token_endpoint_auth_signing_alg_values_supported") val tokenAuthAlgorithms: List<String>,
    @SerialName("authorization_response_iss_parameter_supported") val issuerInResponse: Boolean,
    @SerialName("client_id_metadata_document_supported") val clientMetadataSupported: Boolean,
    @SerialName("require_pushed_authorization_requests") val parRequired: Boolean,
    @SerialName("require_request_uri_registration") val registerRequestUri: Boolean? = null,
)

@Serializable
private class OAuthError(val error: String)
