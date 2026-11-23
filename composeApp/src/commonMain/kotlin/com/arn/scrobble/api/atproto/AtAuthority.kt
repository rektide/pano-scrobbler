package com.arn.scrobble.api.atproto

import com.arn.scrobble.db.AtEnrollment
import com.arn.scrobble.db.AtEnrollmentStore
import com.arn.scrobble.db.RoomAtEnrollment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException

internal data class AtAccount(
    val identity: AtIdentity?, val method: AtMethod?, val settings: AtSettings,
    val missing: Map<String, Set<String>>, val waiting: Boolean,
    val problem: AtProblem? = null,
)

internal enum class AtMutation(val nsid: String, val actions: Set<String>) {
    CREATE("com.atproto.repo.createRecord", setOf("create")),
    PUT("com.atproto.repo.putRecord", setOf("create", "update")),
    DELETE("com.atproto.repo.deleteRecord", setOf("delete")),
}

internal class AtAuthority(
    private val vault: AtVault = AtVault(),
    public: AtWire = anonymousAt,
    private val transport: AtWire = AtWire(),
    private val resolve: suspend (String) -> AtIdentity = AtIdentityResolver(public)::resolve,
    private val clock: () -> Long = System::currentTimeMillis,
    private val connected: suspend (String) -> Unit = { atOutbox.retry(it) },
    private val enrollmentStore: AtEnrollmentStore = RoomAtEnrollment(),
) {
    private val oauth = AtOAuth(public, transport, clock)
    private var resourceNonce: Pair<String, String>? = null
    val notices = MutableStateFlow<AtProblem?>(null)
    val changed = MutableStateFlow("")

    /** Safe in the listener and worker scheduler: no credential file or key access. */
    suspend fun enrollment() = enrollmentStore.read()

    suspend fun migrateEnrollment() {
        if (enrollment() != null) return
        try { vault.locked { adoptEnrollment() } }
        catch (e: AtFailure) { if (e.problem != AtProblem.STORAGE) throw e }
    }

    private suspend fun AtVault.Transaction.adoptEnrollment(): AtEnrollment = enrollment() ?: AtEnrollment(
        state.active, state.settings[state.active?.did] ?: AtSettings(),
    ).also { enrollmentStore.save(it, resume = true) }

    suspend fun account(): AtAccount {
        val saved = enrollment()
        return try {
            vault.locked {
                val profile = adoptEnrollment()
                val choices = profile.settings
                val current = state.session?.takeIf { it.usable && it.identity.did == profile.identity?.did }
                val missing = if (current?.method != AtMethod.OAUTH) emptyMap() else choices.requested()
                    .mapValues { (collection, actions) -> actions - current.permissions[collection].orEmpty() }
                    .filterValues { it.isNotEmpty() }
                AtAccount(profile.identity, current?.method, choices, missing, state.pending?.let { it.expires > clock() } == true)
            }
        } catch (e: AtFailure) {
            if (e.problem != AtProblem.STORAGE) throw e
            // On first POSIX run there is no vault until explicit file opt-in.
            AtAccount(saved?.identity, null, saved?.settings ?: AtSettings(), emptyMap(), false,
                if (saved?.identity != null || !atVaultNeedsFileConsent) AtProblem.STORAGE else null)
        }
    }

    suspend fun appPassword(identifier: String, password: String, settings: AtSettings, allowFile: Boolean) {
        checkAt(password.length in 8..512 && password.none(Char::isISOControl), AtProblem.LOGIN)
        val identity = resolve(identifier)
        vault.locked(allowFile) {
            val pds = secureUrl(identity.pds)
            val reply = transport.post(pds.xrpc("com.atproto.server.createSession").build(), buildJsonObject {
                put("identifier", identity.did)
                put("password", password)
            }.requestBody())
            install(passwordSession(reply, identity, atSecret()), choices(identity.did, settings))
        }
        connected(identity.did)
    }

    suspend fun beginBrowser(identifier: String, settings: AtSettings, allowFile: Boolean): String {
        val identity = resolve(identifier)
        return vault.locked(allowFile) {
            val (pending, browser) = oauth.begin(identity, choices(identity.did, settings))
            save(state.copy(pending = pending))
            browser
        }
    }

    suspend fun finishBrowser(callback: String, wakeWorker: Boolean = true) {
        val parameters = parseAtCallback(callback)
        val did = vault.locked {
            val pending = state.pending ?: throw AtFailure(AtProblem.CALLBACK)
            checkAt(pending.expires > clock() && parameters["state"] == pending.state && parameters["iss"] == pending.issuer.origin &&
                (parameters["client_id"] == null || parameters["client_id"] == AT_CLIENT), AtProblem.CALLBACK)
            // Consume before any exchange, including after crash or delivery through a second process.
            save(state.copy(pending = null))
            checkAt("error" !in parameters, AtProblem.LOGIN)
            val code = parameters["code"] ?: throw AtFailure(AtProblem.CALLBACK)
            val identity = resolve(pending.identity.did)
            checkAt(identity.pds == pending.identity.pds && oauth.discover(identity) == pending.issuer, AtProblem.IDENTITY)
            val session = oauth.token(pending.identity, atSecret(), pending.issuer, pending.signer, pending.requested, form(
                "client_id" to AT_CLIENT, "grant_type" to "authorization_code", "redirect_uri" to AT_CALLBACK,
                "code_verifier" to pending.verifier, "code" to code,
            ))
            verifyBinding(session, AtProblem.IDENTITY)
            install(session, pending.settings)
            pending.identity.did
        }
        if (wakeWorker) connected(did)
    }

    private suspend fun AtVault.Transaction.choices(did: String, fallback: AtSettings): AtSettings {
        val current = enrollment()
        return current?.settings?.takeIf { current.identity?.did == did } ?: state.settings[did] ?: fallback
    }

    private suspend fun AtVault.Transaction.install(session: AtSession, settings: AtSettings) {
        // Re-arm paused rows and persist intent before saving usable credentials. A crash
        // between vault commit and enqueue leaves runnable rows for the startup worker.
        enrollmentStore.save(AtEnrollment(session.identity, settings), resume = true)
        save(state.copy(active = session.identity, session = session, pending = null,
            settings = state.settings + (session.identity.did to settings)))
        notices.value = null
        changed.value = session.generation
    }

    suspend fun settings(did: String, settings: AtSettings) {
        val newlyEnabled = vault.locked {
            val profile = adoptEnrollment()
            val identity = profile.identity ?: throw AtFailure(AtProblem.RECONNECT)
            checkAt(identity.did == did, AtProblem.RECONNECT)
            val enabled = settings.completed - profile.settings.completed
            enrollmentStore.save(AtEnrollment(identity, settings), resume = enabled.isNotEmpty())
            save(state.copy(settings = state.settings + (did to settings), pending = null))
            enabled.isNotEmpty()
        }
        if (newlyEnabled) connected(did)
    }

    /** Returns false if no remote session revocation could be confirmed; local use is always cleared. */
    suspend fun disconnect(did: String): Boolean = vault.locked {
        checkAt(adoptEnrollment().identity?.did == did, AtProblem.RECONNECT)
        val session = state.session?.takeUnless { it.claimed }
        enrollmentStore.save(AtEnrollment(null))
        save(state.copy(session = null, active = null, pending = null))
        notices.value = null
        changed.value = atSecret()
        if (session == null) return@locked false
        try {
            if (session.method == AtMethod.APP_PASSWORD) {
                transport.post(secureUrl(session.identity.pds).xrpc("com.atproto.server.deleteSession").build(),
                    ByteArray(0).toRequestBody(), mapOf("Authorization" to "Bearer ${session.refresh}")).ok
            } else {
                val url = session.issuer?.revoke ?: return@locked false
                oauth.dpop(secureUrl(url), session.signer!!, form("client_id" to AT_CLIENT, "token" to session.refresh,
                    "token_type_hint" to "refresh_token")).first.ok
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { false }
    }

    /** A mutation stays pinned to its DID and session generation until its one-shot response. */
    suspend fun write(did: String, collection: String, mutation: AtMutation, body: JsonObject,
                      clearingStatus: Boolean = false, expectedGeneration: String? = null): AtReply = vault.locked {
        val profile = enrollment()
        checkAt(profile?.identity?.did == did && state.active?.did == did && body.text("repo") == did && body.text("collection") == collection, AtProblem.RECONNECT)
        val settings = profile!!.settings
        val allowed = settings.requested()[collection].orEmpty()
        checkAt(allowed.containsAll(mutation.actions) ||
            (clearingStatus && mutation == AtMutation.DELETE && AtTarget.entries.any { it.status == collection }), AtProblem.PERMISSION)
        var session = state.session?.takeIf { it.usable } ?: throw AtFailure(AtProblem.RECONNECT)
        checkAt(expectedGeneration == null || expectedGeneration == session.generation, AtProblem.RECONNECT)
        checkGrant(session, collection, mutation)
        if (session.expires <= clock() + 20_000) session = refresh(session)
        checkGrant(session, collection, mutation)
        val url = secureUrl(session.identity.pds).xrpc(mutation.nsid).build()
        val reply = if (session.method == AtMethod.OAUTH) {
            // PDS nonces deliberately do not overwrite the AS nonce, even when both share a host.
            val nonceKey = "${session.generation}:${session.identity.pds}"
            val last = resourceNonce?.takeIf { it.first == nonceKey }?.second
            val result = oauth.dpop(url, session.signer!!.copy(nonce = last), body.requestBody(), session.access)
            result.second?.let { resourceNonce = nonceKey to it }
            result.first
        } else transport.post(url, body.requestBody(), mapOf("Authorization" to "Bearer ${session.access}"))
        if (reply.code == 401 && reply.error in setOf("ExpiredToken", "InvalidToken", "invalid_token", "AuthenticationRequired")) {
            save(state.copy(session = session.copy(expires = 0)))
            throw AtFailure(AtProblem.NETWORK) // next scheduled attempt refreshes; do not replay this POST here
        }
        reply
    }

    private fun checkGrant(session: AtSession, collection: String, mutation: AtMutation) {
        checkAt(session.method == AtMethod.APP_PASSWORD || session.permissions[collection].orEmpty().containsAll(mutation.actions), AtProblem.PERMISSION)
    }

    private suspend fun AtVault.Transaction.refresh(previous: AtSession): AtSession {
        verifyBinding(previous, AtProblem.RECONNECT)
        // An interrupted request must leave a durable claim rather than risk replaying a rotated token.
        save(state.copy(session = previous.copy(claimed = true)))
        val next = try {
            if (previous.method == AtMethod.OAUTH) {
                oauth.token(previous.identity, previous.generation, previous.issuer!!, previous.signer!!, previous.permissions, form(
                    "client_id" to AT_CLIENT, "grant_type" to "refresh_token", "refresh_token" to previous.refresh,
                ))
            } else {
                val reply = transport.post(secureUrl(previous.identity.pds).xrpc("com.atproto.server.refreshSession").build(),
                    ByteArray(0).toRequestBody(), mapOf("Authorization" to "Bearer ${previous.refresh}"))
                passwordSession(reply, previous.identity, previous.generation)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            if (e is UnknownHostException || e is ConnectException) {
                save(state.copy(session = previous))
                throw AtFailure(AtProblem.NETWORK)
            }
            throw AtFailure(AtProblem.RECONNECT)
        }
        verifyBinding(next, AtProblem.RECONNECT)
        save(state.copy(session = next))
        return next
    }

    private suspend fun verifyBinding(session: AtSession, mismatch: AtProblem) {
        val verified = resolve(session.identity.did)
        checkAt(verified.did == session.identity.did && verified.pds == session.identity.pds, mismatch)
        if (session.method == AtMethod.OAUTH) checkAt(oauth.discover(verified) == session.issuer, mismatch)
    }

    private fun passwordSession(reply: AtReply, identity: AtIdentity, generation: String): AtSession {
        checkAt(reply.ok, if (reply.transient) AtProblem.NETWORK else AtProblem.LOGIN)
        val json = reply.json
        checkAt(json.text("did") == identity.did, AtProblem.LOGIN)
        // Opaque to the client; renew after explicit expiry rejection or a conservative lifetime.
        return AtSession(identity, generation, AtMethod.APP_PASSWORD, requireToken(json.text("accessJwt")),
            requireToken(json.text("refreshJwt")), clock() + 30 * 60_000)
    }
}

private val AtSession.usable get() = !claimed && (method == AtMethod.APP_PASSWORD || grantVersion == 1)

internal val atAuthority = AtAuthority()
