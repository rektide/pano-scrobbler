package com.arn.scrobble.atproto.posting

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TargetGrantRegressionTest {
    @Test
    fun `accepts named positional encoded and repeated valid grants`() {
        for (permission in listOf(
            "repo:fm.teal.feed.play", "repo:fm.teal.feed.play?",
            "repo?collection=fm.teal.feed.play&action=create",
            "repo:%66m.teal.feed.play?action=%63reate",
            "repo?%63ollection=fm.teal.feed.play&%61ction=create",
            "repo:fm.teal.feed.play?action=delete&action=create&action=update",
            "repo?collection=fm.teal.feed.play&collection=com.example.fooV2&action=create",
        )) assertEquals(setOf(AtProtoTarget.TEAL), AtProtoTarget.grantedBy("atproto $permission"), permission)
        assertEquals(AtProtoTarget.entries.toSet(), AtProtoTarget.grantedBy(
            "atproto repo?collection=fm.teal.feed.play&collection=app.rocksky.scrobble&action=create",
        ))
        assertEquals(AtProtoTarget.entries.toSet(), AtProtoTarget.grantedBy("atproto repo:%2A?action=create"))
    }

    @Test
    fun `rejects entire invalid permission rather than partially granting it`() {
        for (permission in listOf(
            "repo:fm.teal.feed.play?action=create&action=nope",
            "repo:fm.teal.feed.play?action=create&action=create",
            "repo:fm.teal.feed.play?action=create&action=",
            "repo:fm.teal.feed.play?collection=app.rocksky.scrobble&action=create",
            "repo:fm.teal.feed.play?unknown=value&action=create",
            "repo?collection=fm.teal.feed.play&collection=bad&action=create",
            "repo:fm.teal.*?action=create", "repo:fm.teal.feed.pl*?action=create",
            "repo?collection=fm.teal.feed.play&collection=com.example.foo-bar&action=create",
            "repo:fm.teal.feed.play?action=cre%ZZate", "repo:fm.teal.feed.play?action=%FFcreate",
            "repo:fm.teal.feed.play?action=Create", "repo:fm.teal.feed.play?action=update",
            "repo:fm.teal.feed.play?action=create&", "repo:fm.teal.feed.play?action",
            "repo:", "repo?collection=", "repo?action=create",
        )) assertTrue(AtProtoTarget.grantedBy("atproto $permission").isEmpty(), permission)
        // Unknown scopes do not invalidate a separate, fully understood grant.
        assertEquals(setOf(AtProtoTarget.ROCKSKY), AtProtoTarget.grantedBy(
            "atproto repo:fm.teal.feed.play?action=create&action=nope repo:app.rocksky.scrobble?action=create",
        ))
    }

    @Test
    fun `uses actual atproto grants intersected with explicitly selected targets`() {
        assertTrue(AtProtoTarget.grantedBy("repo:*").isEmpty())
        assertTrue(AtProtoTarget.grantedBy("atproto\trepo:*").isEmpty())
        assertTrue(AtProtoTarget.grantedBy("atproto repo:fm.teal.feed.play", setOf(AtProtoTarget.ROCKSKY)).isEmpty())
        assertEquals(setOf(AtProtoTarget.TEAL), AtProtoTarget.grantedBy("atproto repo:*", setOf(AtProtoTarget.TEAL)))
        assertEquals(setOf(AtProtoTarget.ROCKSKY), AtProtoTarget.grantedBy("atproto transition:generic", setOf(AtProtoTarget.ROCKSKY)))
        assertTrue(AtProtoTarget.grantedBy("atproto repo:*", emptySet()).isEmpty())
    }
}
