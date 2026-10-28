package com.arn.scrobble.atproto.http

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AtProtoHttpClientPoolTest {
    @Test fun invalidationRetiresButDoesNotCloseALeasedEngine(): Unit = runBlocking {
        var created = 0
        val pool = AtProtoHttpClientPool {
            created++
            HttpClient(MockEngine { respond("fixture") }) { followRedirects = false }
        }
        val old = pool.acquire()
        val other = pool.acquire()
        assertSame(old.client, other.client)
        pool.invalidate()
        assertFalse(old.client.coroutineContext[Job]!!.isCompleted)
        // An operation's second call must still work even after proxy invalidation.
        assertEquals("fixture", old.client.get("https://fixture.example").bodyAsText())
        val fresh = pool.acquire()
        assertNotSame(old.client, fresh.client)
        assertEquals(2, created)
        old.close()
        old.close()
        assertFalse(other.client.coroutineContext[Job]!!.isCompleted)
        other.close()
        other.client.coroutineContext[Job]!!.join()
        assertTrue(other.client.coroutineContext[Job]!!.isCompleted)
        pool.close()
        assertFalse(fresh.client.coroutineContext[Job]!!.isCompleted)
        assertFailsWith<IllegalStateException> { pool.acquire() }
        fresh.close()
        fresh.client.coroutineContext[Job]!!.join()
        assertTrue(fresh.client.coroutineContext[Job]!!.isCompleted)
    }
}
