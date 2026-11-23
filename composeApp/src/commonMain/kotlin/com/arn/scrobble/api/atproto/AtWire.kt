package com.arn.scrobble.api.atproto

import com.arn.scrobble.api.Requesters
import com.arn.scrobble.pref.MainPrefs
import com.arn.scrobble.utils.PlatformStuff
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal enum class AtProblem(val text: String) {
    IDENTITY("Could not verify the AT Protocol account or its server."),
    METADATA("Browser sign-in is unavailable: Pano's published OAuth metadata could not be verified. You can use an app password instead."),
    CALLBACK("Invalid, expired or already-used browser response. Start browser sign-in again."),
    LOGIN("Sign-in was rejected. Check the account, app password or provider approval."),
    RECONNECT("Authorization needed — reconnect the same DID to resume delivery."),
    PERMISSION("Destination permission missing — review enabled actions and authorize in the browser."),
    STORAGE("AT Protocol credential storage is unavailable. Check storage and try again."),
    NETWORK("Offline or server temporarily unavailable. Try again later."),
    RESPONSE("Unexpected AT Protocol response."),
    CONFLICT("Record conflict — the existing record was not changed."),
    INPUT("Required track metadata is missing or invalid."),
}

internal class AtFailure(val problem: AtProblem) : IOException(problem.text)
internal fun checkAt(condition: Boolean, problem: AtProblem = AtProblem.RESPONSE) {
    if (!condition) throw AtFailure(problem)
}

internal fun JsonObject.text(name: String): String? = (get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content
internal fun JsonObject.array(name: String): JsonArray? = get(name) as? JsonArray
internal fun JsonObject.obj(name: String): JsonObject? = get(name) as? JsonObject
internal fun JsonObject.flag(name: String): Boolean? = (get(name) as? JsonPrimitive)?.booleanOrNull
internal fun JsonObject.number(name: String): Long? = (get(name) as? JsonPrimitive)?.longOrNull
internal fun JsonObject.strings(name: String): List<String> = array(name)?.mapNotNull {
    (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content
}.orEmpty()

internal fun secureUrl(raw: String?): HttpUrl = raw?.toHttpUrlOrNull()?.takeIf {
    it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.fragment == null &&
        raw.startsWith("https://") && '\\' !in raw && raw.none { ch -> ch.isWhitespace() } &&
        '@' !in raw.substringAfter("://").substringBefore('/').substringBefore('?')
} ?: throw AtFailure(AtProblem.IDENTITY)

internal fun HttpUrl.origin() = newBuilder().encodedPath("/").query(null).fragment(null).build()
internal fun HttpUrl.xrpc(nsid: String) = newBuilder().addPathSegments("xrpc/$nsid")

internal data class AtReply(val code: Int, val text: String, val nonce: String?, val retryAfter: String?, val mediaType: String?) {
    val json: JsonObject get() = try {
        Json.parseToJsonElement(text) as? JsonObject ?: throw AtFailure(AtProblem.RESPONSE)
    } catch (_: IllegalArgumentException) { throw AtFailure(AtProblem.RESPONSE) }

    val error: String? get() = try { json.text("error") } catch (_: AtFailure) { null }
    val ok get() = code in 200..299
    val transient get() = code == 408 || code == 429 || code in 500..599
}

/** No Ktor plugins, cookies, caches, automatic redirects, authenticator or automatic replay. */
internal class AtWire(private val build: () -> OkHttpClient.Builder = ::atClientBuilder) {
    private val client by lazy {
        build().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .authenticator(Authenticator.NONE).proxyAuthenticator(Authenticator.NONE)
            .cookieJar(CookieJar.NO_COOKIES).cache(null)
            .callTimeout(15, TimeUnit.SECONDS).build()
    }

    suspend fun get(url: HttpUrl): AtReply = execute(Request.Builder().url(url).build())

    suspend fun post(url: HttpUrl, body: RequestBody, headers: Map<String, String> = emptyMap()): AtReply {
        val request = Request.Builder().url(url).post(object : RequestBody() {
            override fun isOneShot() = true // also prevents Retry-After: 0 follow-ups
            override fun contentType() = body.contentType()
            override fun contentLength() = body.contentLength()
            override fun writeTo(sink: BufferedSink) = body.writeTo(sink)
        })
        headers.forEach { (key, value) -> request.header(key, value) }
        return execute(request.build())
    }

    private suspend fun execute(request: Request): AtReply = suspendCancellableCoroutine { result ->
        val call = client.newCall(request)
        result.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = result.resumeWithException(e)
            override fun onResponse(call: Call, response: Response) {
                val value = runCatching {
                    response.use {
                        val source = it.body.source()
                        checkAt(!source.request(2_097_153L))
                        AtReply(it.code, source.readUtf8(), it.headers.values("DPoP-Nonce").singleOrNull()
                            ?.takeIf { nonce -> nonce.length in 1..2048 && nonce.all { c -> c in '!'..'~' } },
                            it.header("Retry-After"), it.body.contentType()?.let { mt -> "${mt.type}/${mt.subtype}" })
                    }
                }
                value.fold(result::resume, result::resumeWithException)
            }
        })
    }
}

private fun atClientBuilder(): OkHttpClient.Builder = OkHttpClient.Builder().apply {
    val p = Requesters.proxy.value
    proxy(when (p.type) {
        MainPrefs.ProxyPrefs.Type.SYSTEM -> PlatformStuff.getSystemSocksProxy()
        MainPrefs.ProxyPrefs.Type.HTTP -> Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(p.host, p.port))
        MainPrefs.ProxyPrefs.Type.SOCKS5 -> Proxy(Proxy.Type.SOCKS, InetSocketAddress.createUnresolved(p.host, p.port))
    })
}

internal val anonymousAt = AtWire()
internal fun JsonObject.requestBody() = toString().toRequestBody("application/json".toMediaType())
