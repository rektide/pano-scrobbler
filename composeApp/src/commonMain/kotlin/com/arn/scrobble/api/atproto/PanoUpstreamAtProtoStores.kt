package com.arn.scrobble.api.atproto

import androidx.datastore.core.DataStore
import com.arn.scrobble.pref.MainPrefs
import com.arn.scrobble.utils.PlatformStuff
import io.github.kikin81.atproto.oauth.OAuthSession
import io.github.kikin81.atproto.oauth.OAuthSessionStore
import io.github.kikin81.atproto.oauth.PendingAuth
import io.github.kikin81.atproto.oauth.PendingAuthStore
import kotlinx.coroutines.flow.first

/**
 * Fork pending-login persistence over Pano's existing durable preference file.
 *
 * This field-test implementation intentionally does not add encryption or
 * Android backup exclusion. [PendingAuth] contains PKCE and private-key
 * material; hardening the backing store remains a required later slice.
 */
internal class PanoUpstreamPendingAuthStore(
    private val prefs: DataStore<MainPrefs> = PlatformStuff.mainPrefs,
) : PendingAuthStore {
    override suspend fun load(): PendingAuth? = prefs.data.first().atProtoUpstreamPendingAuth

    override suspend fun save(pending: PendingAuth) {
        prefs.updateData { it.copy(atProtoUpstreamPendingAuth = pending) }
    }

    override suspend fun clear() {
        prefs.updateData { it.copy(atProtoUpstreamPendingAuth = null) }
    }
}

/**
 * Fork OAuth-session persistence over Pano's existing durable preference file.
 *
 * [DataStore.updateData] completes before each mutation returns, satisfying the
 * fork's rotating-token durability contract. Encryption and backup exclusion
 * remain deliberately deferred for this reversible field-test path.
 */
internal class PanoUpstreamOAuthSessionStore(
    private val prefs: DataStore<MainPrefs> = PlatformStuff.mainPrefs,
) : OAuthSessionStore {
    override suspend fun load(): OAuthSession? = prefs.data.first().atProtoUpstreamSession

    override suspend fun save(session: OAuthSession) {
        prefs.updateData { it.copy(atProtoUpstreamSession = session) }
    }

    override suspend fun clear() {
        prefs.updateData { it.copy(atProtoUpstreamSession = null) }
    }
}
