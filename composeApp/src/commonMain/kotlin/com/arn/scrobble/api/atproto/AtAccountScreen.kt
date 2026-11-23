package com.arn.scrobble.api.atproto

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.arn.scrobble.utils.PlatformStuff
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import pano_scrobbler.composeapp.generated.resources.*

@Composable
internal fun hasAtAccount(): Boolean {
    val changed by atAuthority.changed.collectAsState()
    val present by produceState(false, changed) {
        value = try { atAuthority.account().identity != null } catch (_: AtFailure) { false }
    }
    return present
}

@Composable
internal fun AtAccountScreen(modifier: Modifier, browse: (String?) -> Unit) {
    var account by remember { mutableStateOf<AtAccount?>(null) }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var callback by remember { mutableStateOf("") }
    var settings by remember { mutableStateOf(AtSettings()) }
    var error by remember { mutableStateOf<StringResource?>(null) }
    var busy by remember { mutableStateOf(false) }
    var allowFile by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val changed by atAuthority.changed.collectAsState()
    val notices by atAuthority.notices.collectAsState()

    suspend fun reload() {
        val loaded = atAuthority.account()
        account = loaded
        loaded.identity?.let { identifier = it.handle ?: it.did }
        if (loaded.identity != null) settings = loaded.settings
    }

    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try { action(); reload() }
            catch (e: CancellationException) { throw e }
            catch (e: AtFailure) { error = e.problem.message }
            catch (_: Exception) { error = AtProblem.NETWORK.message }
            finally { busy = false }
        }
    }

    LaunchedEffect(changed) {
        // A missing POSIX vault before first opt-in is normal.
        try { reload() } catch (e: AtFailure) { error = e.problem.message }
    }
    LaunchedEffect(account?.identity?.did) {
        val did = account?.identity?.did ?: return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(3000)
            try {
                val renewed = atAuthority.account()
                if (renewed.identity?.did == did) account = renewed
            } catch (e: AtFailure) { error = e.problem.message }
        }
    }
    val identity = account?.identity
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(Res.string.at_account_intro))
        OutlinedButton(onClick = { browse(identity?.did) }) { Text(stringResource(Res.string.at_account_browse)) }
        if (identity != null) {
            Text(identity.handle ?: identity.did, style = MaterialTheme.typography.titleMedium)
            if (identity.handle != null) Text(identity.did)
            Text(stringResource(when (account?.method) {
                AtMethod.OAUTH -> Res.string.at_connected_oauth
                AtMethod.APP_PASSWORD -> Res.string.at_connected_password
                null -> account?.problem?.message ?: AtProblem.RECONNECT.message
            }))
        }
        (error ?: account?.problem?.message?.takeIf { identity == null })?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        notices?.takeUnless { it.message == error || it == account?.problem || it == AtProblem.STORAGE && identity == null }
            ?.let { Text(stringResource(it.message), color = MaterialTheme.colorScheme.error) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedTextField(identifier, { identifier = it }, enabled = !busy, singleLine = true, label = { Text(stringResource(Res.string.at_did)) })
        fun edit(next: AtSettings) {
            if (identity == null) settings = next
            else run {
                val noLongerLive = settings.live - next.live
                atAuthority.settings(identity.did, next)
                atLive.disable(identity.did, noLongerLive)
                settings = next
            }
        }
        AtTarget.entries.forEach { target ->
            AtToggle(stringResource(Res.string.at_send_completed, target.label), target in settings.completed, !busy) {
                edit(settings.copy(completed = settings.completed.toggled(target, it)))
            }
            AtToggle(stringResource(Res.string.at_live_toggle, target.label), target in settings.live, !busy) {
                edit(settings.copy(live = settings.live.toggled(target, it)))
            }
            AtToggle(stringResource(Res.string.at_delete_toggle, target.label), target in settings.delete, !busy) {
                edit(settings.copy(delete = settings.delete.toggled(target, it)))
            }
        }
        AtToggle(stringResource(Res.string.at_love_toggle), settings.likes, !busy) { edit(settings.copy(likes = it)) }
        AtToggle(stringResource(Res.string.at_match_toggle), settings.rockskyMatch, !busy) { edit(settings.copy(rockskyMatch = it)) }
        if (settings.completed.size == 2) Text(stringResource(Res.string.at_mirror_warning))
        account?.missing?.takeIf { it.isNotEmpty() }?.let { missing ->
            Text(stringResource(AtProblem.PERMISSION.message) + "\n" + missing.entries.joinToString("\n") { (key, actions) -> "$key: ${actions.sorted().joinToString()}" })
        }
        if (atVaultNeedsFileConsent && identity == null) {
            AtToggle(stringResource(Res.string.at_allow_posix), allowFile, !busy) { allowFile = it }
        }
        Text(stringResource(Res.string.at_browser_scope))
        Button(enabled = !busy && identifier.isNotBlank(), onClick = {
            run { PlatformStuff.openInBrowser(atAuthority.beginBrowser(identifier, settings, allowFile)) }
        }) { Text(stringResource(if (identity == null) Res.string.at_browser_login else Res.string.at_browser_reconnect)) }
        if (account?.waiting == true) {
            Text(stringResource(Res.string.at_browser_waiting))
            if (PlatformStuff.isDesktop) {
                OutlinedTextField(callback, { callback = it }, label = { Text(stringResource(Res.string.at_callback_url)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                Button(enabled = !busy && callback.isNotBlank(), onClick = {
                    val submitted = callback.trim()
                    callback = ""
                    run { atAuthority.finishBrowser(submitted) }
                }) { Text(stringResource(Res.string.at_callback_complete)) }
            }
        }
        Text(stringResource(Res.string.at_password_intro))
        OutlinedTextField(password, { password = it }, enabled = !busy, singleLine = true,
            label = { Text(stringResource(Res.string.at_password)) }, visualTransformation = PasswordVisualTransformation())
        Button(enabled = !busy && identifier.isNotBlank() && password.isNotBlank(), onClick = {
            val submitted = password.trim()
            password = ""
            run { atAuthority.appPassword(identifier, submitted, settings, allowFile) }
        }) { Text(stringResource(Res.string.at_password_use)) }
        if (identity != null) {
            AtPendingList(identity.did, changed, onRetry = { run { atOutbox.retry(identity.did) } })
        }
        if (identity != null) OutlinedButton(enabled = !busy, onClick = { confirmDisconnect = true }) { Text(stringResource(Res.string.at_disconnect)) }
    }
    if (confirmDisconnect && identity != null) AlertDialog(
        onDismissRequest = { confirmDisconnect = false },
        text = { Text(stringResource(Res.string.at_disconnect_explain)) },
        confirmButton = { TextButton(onClick = {
            confirmDisconnect = false
            run { if (!atAuthority.disconnect(identity.did)) error = Res.string.at_disconnect_unconfirmed }
        }) { Text(stringResource(Res.string.at_disconnect)) } },
        dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text(stringResource(Res.string.at_cancel)) } },
    )
}

@Composable
private fun AtPendingList(did: String, changed: String, onRetry: () -> Unit) {
    val rows by produceState(emptyList(), did, changed) {
        while (true) {
            value = atOutbox.pending(did)
            kotlinx.coroutines.delay(3000)
        }
    }
    if (rows.isNotEmpty()) {
        Text(stringResource(Res.string.at_pending), style = MaterialTheme.typography.titleMedium)
        rows.forEach { row ->
            val target = AtTarget.valueOf(row.target).label
            val title = runCatching { kotlinx.serialization.json.Json.decodeFromString<com.arn.scrobble.api.lastfm.ScrobbleData>(row.original).track }.getOrNull() ?: stringResource(Res.string.at_listen)
            val reason = when (row.state) {
                "PAUSED", "RECONNECT" -> AtProblem.RECONNECT.message
                "PERMISSION" -> AtProblem.PERMISSION.message
                "RETRY" -> Res.string.at_pending_retry
                "CONFLICT" -> AtProblem.CONFLICT.message
                "METADATA" -> AtProblem.INPUT.message
                "REJECTED" -> Res.string.at_pending_rejected
                else -> Res.string.at_pending_queued
            }
            Text(stringResource(Res.string.at_pending_row, target, title, stringResource(reason)))
        }
        OutlinedButton(onClick = onRetry) { Text(stringResource(Res.string.at_retry_paused)) }
    }
}

@Composable
private fun AtToggle(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Checkbox(checked, onChange, enabled = enabled)
        Text(label)
    }
}

private fun <T> Set<T>.toggled(value: T, enabled: Boolean) = if (enabled) this + value else this - value
