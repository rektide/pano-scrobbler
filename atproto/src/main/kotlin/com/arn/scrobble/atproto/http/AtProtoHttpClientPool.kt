package com.arn.scrobble.atproto.http

import io.ktor.client.HttpClient
import java.util.concurrent.atomic.AtomicBoolean

/** A lease covers an entire protocol operation, including the gaps between network calls. */
class AtProtoHttpClientLease(val client: HttpClient, private val release: () -> Unit) : AutoCloseable {
    private val released = AtomicBoolean()
    override fun close() { if (released.compareAndSet(false, true)) release() }
}

fun interface AtProtoHttpClientProvider {
    fun acquire(): AtProtoHttpClientLease
}

/** Proxy changes retire an engine; outstanding operations finish before that engine is closed. */
class AtProtoHttpClientPool(private val create: () -> HttpClient) : AtProtoHttpClientProvider, AutoCloseable {
    private class Entry(val client: HttpClient, var users: Int = 0, var retired: Boolean = false)
    private var current: Entry? = null
    private var closed = false

    override fun acquire(): AtProtoHttpClientLease {
        val entry = synchronized(this) {
            check(!closed) { "AT Protocol HTTP pool is closed" }
            (current ?: Entry(create()).also { current = it }).also { it.users++ }
        }
        return AtProtoHttpClientLease(entry.client) {
            val close = synchronized(this) {
                entry.users--
                entry.retired && entry.users == 0
            }
            if (close) entry.client.close()
        }
    }

    fun invalidate() { retire(closePool = false) }
    override fun close() { retire(closePool = true) }

    private fun retire(closePool: Boolean) {
        val entry = synchronized(this) {
            if (closePool) closed = true
            current?.also { it.retired = true; current = null }?.takeIf { it.users == 0 }
        }
        entry?.client?.close()
    }
}
