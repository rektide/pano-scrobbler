package com.arn.scrobble.atproto.posting

import com.arn.scrobble.atproto.repo.CreatedRecord
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AtProtoPosterTest {
    @Test
    fun `derives writable targets from granted scopes`() {
        assertEquals(
            setOf(AtProtoTarget.TEAL),
            AtProtoTarget.grantedBy("atproto repo:fm.teal.feed.play?action=create"),
        )
        assertEquals(
            AtProtoTarget.entries.toSet(),
            AtProtoTarget.grantedBy("atproto repo:*?action=create"),
        )
        assertEquals(
            AtProtoTarget.entries.toSet(),
            AtProtoTarget.grantedBy("atproto transition:generic"),
        )
        assertTrue(AtProtoTarget.grantedBy("atproto repo:app.rocksky.scrobble?action=update").isEmpty())
    }

    @Test
    fun `posts each enabled target and reports partial failures`() = runTest {
        val attempted = mutableListOf<String>()
        val poster = AtProtoPoster(
            config = AtProtoPostingConfig(),
            createRecord = { did, collection, record ->
                assertEquals("did:plc:abcdefghijklmnopqrstuvwx", did)
                assertEquals(collection, record["\$type"].toString().trim('"'))
                attempted += collection
                if (collection == AtProtoTarget.ROCKSKY.collection) error("Rocksky unavailable")
                CreatedRecord("at://example/$collection/record", "bafyexample")
            },
            json = Json { explicitNulls = false },
        )

        val result = poster.post(
            did = "did:plc:abcdefghijklmnopqrstuvwx",
            play = Play("Track", "Artist", playedAtMillis = 1),
        )

        assertEquals(AtProtoTarget.entries.map { it.collection }, attempted)
        assertEquals(setOf(AtProtoTarget.TEAL), result.created.keys)
        assertEquals(setOf(AtProtoTarget.ROCKSKY), result.failures.keys)
        assertFalse(result.isSuccess)
    }

    @Test
    fun `skips disabled targets`() = runTest {
        val attempted = mutableListOf<String>()
        val poster = AtProtoPoster(
            config = AtProtoPostingConfig(targets = setOf(AtProtoTarget.TEAL)),
            createRecord = { _, collection, _ ->
                attempted += collection
                CreatedRecord("at://example/$collection/record", "bafyexample")
            },
            json = Json { explicitNulls = false },
        )

        val result = poster.post(
            did = "did:plc:abcdefghijklmnopqrstuvwx",
            play = Play("Track", "Artist", playedAtMillis = 1),
        )

        assertEquals(listOf(AtProtoTarget.TEAL.collection), attempted)
        assertTrue(result.isSuccess)
    }
}
