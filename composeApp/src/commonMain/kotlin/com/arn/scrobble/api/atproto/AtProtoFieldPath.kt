package com.arn.scrobble.api.atproto

import com.arn.scrobble.BuildKonfig

internal enum class AtProtoFieldBackend {
    LOCAL,
    UPSTREAM,
}

internal data class AtProtoSessionPresence(
    val localDids: Set<String>,
    val upstreamDid: String?,
)

internal class AtProtoSessionPathMismatchException(message: String) : IllegalStateException(message)

/** Pure selection/session-compatibility policy used by both field paths. */
internal object AtProtoFieldPath {
    val selected: AtProtoFieldBackend
        get() = selected(BuildKonfig.ATPROTO_USE_UPSTREAM)

    fun selected(useUpstream: Boolean): AtProtoFieldBackend =
        if (useUpstream) AtProtoFieldBackend.UPSTREAM else AtProtoFieldBackend.LOCAL

    fun ensureCanBeginLogin(
        selected: AtProtoFieldBackend,
        sessions: AtProtoSessionPresence,
    ) = ensureSelectedPathCompatible(selected, sessions)

    fun ensureSelectedPathCompatible(
        selected: AtProtoFieldBackend,
        sessions: AtProtoSessionPresence,
    ) {
        val selectedExists = when (selected) {
            AtProtoFieldBackend.LOCAL -> sessions.localDids.isNotEmpty()
            AtProtoFieldBackend.UPSTREAM -> sessions.upstreamDid != null
        }
        val otherExists = when (selected) {
            AtProtoFieldBackend.LOCAL -> sessions.upstreamDid != null
            AtProtoFieldBackend.UPSTREAM -> sessions.localDids.isNotEmpty()
        }
        if (!selectedExists && otherExists) throw mismatch(selected)
    }

    fun requireActiveSession(
        selected: AtProtoFieldBackend,
        expectedDid: String,
        sessions: AtProtoSessionPresence,
    ) {
        ensureCanBeginLogin(selected, sessions)
        val selectedDidExists = when (selected) {
            AtProtoFieldBackend.LOCAL -> expectedDid in sessions.localDids
            AtProtoFieldBackend.UPSTREAM -> sessions.upstreamDid == expectedDid
        }
        check(selectedDidExists) {
            when (selected) {
                AtProtoFieldBackend.LOCAL ->
                    "No local AT Protocol session for $expectedDid; complete a fresh local login"
                AtProtoFieldBackend.UPSTREAM ->
                    "No fork-backed AT Protocol session for $expectedDid; complete a fresh upstream login"
            }
        }
    }

    fun requireGrantedScope(
        selected: AtProtoFieldBackend,
        expectedDid: String,
        sessions: AtProtoSessionPresence,
        localScopes: Map<String, String>,
        upstreamScope: String?,
    ): String {
        requireActiveSession(selected, expectedDid, sessions)
        return when (selected) {
            AtProtoFieldBackend.LOCAL -> localScopes[expectedDid]
            AtProtoFieldBackend.UPSTREAM -> upstreamScope
        } ?: error(
            "The ${selected.name.lowercase()} AT Protocol session has no recorded granted scope; " +
                "complete a fresh ${selected.name.lowercase()} login",
        )
    }

    private fun mismatch(selected: AtProtoFieldBackend): AtProtoSessionPathMismatchException {
        val selectedName = selected.name.lowercase()
        val otherName = if (selected == AtProtoFieldBackend.LOCAL) "fork-backed upstream" else "local"
        return AtProtoSessionPathMismatchException(
            "This build selects the $selectedName AT Protocol path, but only a $otherName session exists. " +
                "Remove the AT Protocol account and complete a fresh $selectedName login; " +
                "JWK and PKCS8/X509 session keys are not converted.",
        )
    }
}
