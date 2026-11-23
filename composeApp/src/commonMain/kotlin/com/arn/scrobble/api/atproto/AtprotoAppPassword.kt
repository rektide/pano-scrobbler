package com.arn.scrobble.api.atproto

import com.arn.scrobble.api.Requesters
import com.arn.scrobble.api.lastfm.ApiException
import com.arn.scrobble.utils.Stuff
import io.ktor.client.HttpClient
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.OutgoingContent
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

class AtprotoAppPasswordSession(
    val did: String,
    val pds: String,
    val accessJwt: String,
    val refreshJwt: String,
)

@Serializable
private class CreateSessionRequest(val identifier: String, val password: String)

@Serializable
private class CreateSessionResponse(
    val did: String,
    val accessJwt: String,
    val refreshJwt: String,
    val active: Boolean = true,
)

/** Resolves the handle, exchanges the app password at its verified PDS, and checks the session's DID. */
suspend fun signInWithAppPassword(handle: String, password: String): AtprotoAppPasswordSession =
    resolveAtprotoHandle(handle).signInWithAppPassword(password, Requesters.baseKtorClient)

internal suspend fun AtprotoIdentity.signInWithAppPassword(
    password: String,
    baseClient: HttpClient,
): AtprotoAppPasswordSession =
    baseClient.config { followRedirects = false }.use { client ->
        exchangeAppPassword(client, password)
    }

private suspend fun AtprotoIdentity.exchangeAppPassword(
    client: HttpClient,
    password: String,
): AtprotoAppPasswordSession {
    val credentials = Stuff.myJson.encodeToString(CreateSessionRequest(did, password)).encodeToByteArray()
    return client.preparePost("$pds/xrpc/com.atproto.server.createSession") {
        // Ktor's OkHttp engine treats channel-backed bodies as one-shot.
        setBody(object : OutgoingContent.ReadChannelContent() {
            private val channel = ByteReadChannel(credentials)
            override val contentType = ContentType.Application.Json
            override val contentLength = credentials.size.toLong()

            override fun readFrom() = channel
        })
    }.execute { response ->
        if (!response.status.isSuccess())
            throw ApiException(response.status.value, response.status.description)
        val session = try {
            Stuff.myJson.decodeFromString<CreateSessionResponse>(response.bodyAsText())
        } catch (_: SerializationException) {
            // A parser exception can include the response's session tokens.
            throw ApiException(-1, "Invalid AT Protocol session response")
        }
        if (!session.active || session.did != did || session.accessJwt.isBlank() || session.refreshJwt.isBlank())
            throw ApiException(-1, "PDS did not return an active session for this account")
        AtprotoAppPasswordSession(session.did, pds, session.accessJwt, session.refreshJwt)
    }
}
