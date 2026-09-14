package com.arn.scrobble.api.atproto

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AtProtoFieldPathTest {
    @Test
    fun `selector defaults can be evaluated for both build values`() {
        assertEquals(AtProtoFieldBackend.LOCAL, AtProtoFieldPath.selected(useUpstream = false))
        assertEquals(AtProtoFieldBackend.UPSTREAM, AtProtoFieldPath.selected(useUpstream = true))
    }

    @Test
    fun `upstream selection rejects a lone local session with fresh-login guidance`() {
        val failure = assertFailsWith<AtProtoSessionPathMismatchException> {
            AtProtoFieldPath.ensureCanBeginLogin(
                selected = AtProtoFieldBackend.UPSTREAM,
                sessions = AtProtoSessionPresence(localDids = setOf("did:plc:local"), upstreamDid = null),
            )
        }

        assertContains(failure.message.orEmpty(), "fresh upstream login")
        assertContains(failure.message.orEmpty(), "not converted")
    }

    @Test
    fun `local selection rejects a lone fork session`() {
        assertFailsWith<AtProtoSessionPathMismatchException> {
            AtProtoFieldPath.ensureCanBeginLogin(
                selected = AtProtoFieldBackend.LOCAL,
                sessions = AtProtoSessionPresence(localDids = emptySet(), upstreamDid = "did:plc:upstream"),
            )
        }
    }

    @Test
    fun `matching selected session is required for account operations`() {
        val sessions = AtProtoSessionPresence(
            localDids = setOf("did:plc:local"),
            upstreamDid = "did:plc:upstream",
        )

        AtProtoFieldPath.requireActiveSession(AtProtoFieldBackend.LOCAL, "did:plc:local", sessions)
        AtProtoFieldPath.requireActiveSession(AtProtoFieldBackend.UPSTREAM, "did:plc:upstream", sessions)

        val failure = assertFailsWith<IllegalStateException> {
            AtProtoFieldPath.requireActiveSession(AtProtoFieldBackend.UPSTREAM, "did:plc:other", sessions)
        }
        assertContains(failure.message.orEmpty(), "fresh upstream login")
    }
}
