package com.arn.scrobble.api.atproto

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttpConfig
import okhttp3.RequestBody
import okio.BufferedSink

/** Must run after base policy; setting only Ktor's flag does not constrain the engine. */
internal fun HttpClientConfig<OkHttpConfig>.atProtoCredentialPolicy() {
    followRedirects = false
    expectSuccess = false
    engine {
        config {
            followRedirects(false)
            followSslRedirects(false)
            // Refresh tokens are single-use. Transport retries cannot prove non-consumption.
            retryOnConnectionFailure(false)
        }
        // Application interceptor: the retry/follow-up layer must see this body, not just the wire layer.
        // The connection retry flag alone does not prevent e.g. HTTP 503 Retry-After: 0 replay.
        addInterceptor { chain ->
            val request = chain.request()
            val body = request.body
            chain.proceed(if (body == null) request else request.newBuilder()
                .method(request.method, AtProtoOneShotRequestBody(body))
                .build())
        }
    }
}

/** Per engine call, not per semantic payload: an explicit protocol retry gets its own body/proof. */
internal class AtProtoOneShotRequestBody(private val delegate: RequestBody) : RequestBody() {
    override fun contentType() = delegate.contentType()
    override fun contentLength() = delegate.contentLength()
    override fun isDuplex() = delegate.isDuplex()
    override fun isOneShot() = true
    override fun writeTo(sink: BufferedSink) = delegate.writeTo(sink)
}
