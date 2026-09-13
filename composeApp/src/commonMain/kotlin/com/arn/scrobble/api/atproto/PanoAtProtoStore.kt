package com.arn.scrobble.api.atproto

import com.arn.scrobble.atproto.oauth.AtProtoSession
import com.arn.scrobble.atproto.oauth.AtProtoSessionStore
import com.arn.scrobble.atproto.oauth.AuthorizationStateStore
import com.arn.scrobble.atproto.oauth.PendingAuthorization
import com.arn.scrobble.utils.PlatformStuff
import kotlinx.coroutines.flow.first

object PanoAtProtoStore : AuthorizationStateStore, AtProtoSessionStore {
    override suspend fun put(authorization: PendingAuthorization) {
        PlatformStuff.mainPrefs.updateData { prefs ->
            check(authorization.state !in prefs.atProtoAuthorizationStates) {
                "AT Protocol authorization state already exists"
            }
            prefs.copy(
                atProtoAuthorizationStates = prefs.atProtoAuthorizationStates +
                    (authorization.state to authorization),
            )
        }
    }

    override suspend fun take(state: String): PendingAuthorization? {
        var authorization: PendingAuthorization? = null
        PlatformStuff.mainPrefs.updateData { prefs ->
            authorization = prefs.atProtoAuthorizationStates[state]
            if (authorization == null) {
                prefs
            } else {
                prefs.copy(atProtoAuthorizationStates = prefs.atProtoAuthorizationStates - state)
            }
        }
        return authorization
    }

    override suspend fun get(did: String): AtProtoSession? =
        PlatformStuff.mainPrefs.data.first().atProtoSessions[did]

    override suspend fun put(session: AtProtoSession) {
        PlatformStuff.mainPrefs.updateData { prefs ->
            prefs.copy(atProtoSessions = prefs.atProtoSessions + (session.did to session))
        }
    }

    override suspend fun delete(did: String) {
        PlatformStuff.mainPrefs.updateData { prefs ->
            prefs.copy(atProtoSessions = prefs.atProtoSessions - did)
        }
    }
}
