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

@Composable
internal fun hasAtAccount(): Boolean {
    val changed by atAuthority.changed.collectAsState()
    val present by produceState(false, changed) {
        value = try { atAuthority.account().identity != null } catch (_: AtFailure) { false }
    }
    return present
}

@Composable
internal fun AtAccountScreen(modifier: Modifier) {
    var account by remember { mutableStateOf<AtAccount?>(null) }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var callback by remember { mutableStateOf("") }
    var settings by remember { mutableStateOf(AtSettings()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var allowFile by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val changed by atAuthority.changed.collectAsState()

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
            catch (e: AtFailure) { error = e.problem.text }
            catch (_: Exception) { error = AtProblem.NETWORK.text }
            finally { busy = false }
        }
    }

    LaunchedEffect(changed) {
        // A missing POSIX vault before first opt-in is normal.
        try { reload() } catch (e: AtFailure) { if (!PlatformStuff.isDesktop) error = e.problem.text }
    }
    val identity = account?.identity
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("One account can send to Teal, Rocksky, or both.")
        if (identity != null) {
            Text(identity.handle ?: identity.did, style = MaterialTheme.typography.titleMedium)
            if (identity.handle != null) Text(identity.did)
            Text(when (account?.method) {
                AtMethod.OAUTH -> "Connected · Browser OAuth"
                AtMethod.APP_PASSWORD -> "Connected · App password"
                null -> AtProblem.RECONNECT.text
            })
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        OutlinedTextField(identifier, { identifier = it }, enabled = !busy, singleLine = true, label = { Text("Handle or DID") })
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
            AtToggle("Send completed plays to ${target.label}", target in settings.completed, !busy) {
                edit(settings.copy(completed = settings.completed.toggled(target, it)))
            }
            AtToggle("Publish live status on ${target.label}", target in settings.live, !busy) {
                edit(settings.copy(live = settings.live.toggled(target, it)))
            }
            AtToggle("Allow deleting my ${target.label} listens", target in settings.delete, !busy) {
                edit(settings.copy(delete = settings.delete.toggled(target, it)))
            }
        }
        if (settings.completed.size == 2) Text("If you already mirror Teal to Rocksky, select Teal only to avoid duplicate Rocksky plays.")
        account?.missing?.takeIf { it.isNotEmpty() }?.let { missing ->
            Text("${AtProblem.PERMISSION.text}\n" + missing.entries.joinToString("\n") { (key, actions) -> "$key: ${actions.sorted().joinToString()}" })
        }
        if (PlatformStuff.isDesktop && account == null) {
            AtToggle("Use an owner-only local file (not encrypted; POSIX filesystem required)", allowFile, !busy) { allowFile = it }
        }
        Text("Browser sign-in grants selected actions and may require reconnecting periodically.")
        Button(enabled = !busy && identifier.isNotBlank(), onClick = {
            run { PlatformStuff.openInBrowser(atAuthority.beginBrowser(identifier, settings, allowFile)) }
        }) { Text(if (identity == null) "Sign in with browser" else "Authorize or reconnect in browser") }
        if (account?.waiting == true) {
            Text("Finish approval in your browser. Return here when done.")
            if (PlatformStuff.isDesktop) {
                OutlinedTextField(callback, { callback = it }, label = { Text("Complete callback URL") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                Button(enabled = !busy && callback.isNotBlank(), onClick = {
                    val submitted = callback.trim()
                    callback = ""
                    run { atAuthority.finishBrowser(submitted) }
                }) { Text("Complete browser sign-in") }
            }
        }
        Text("Create an app password in your provider’s account settings. This grants broader account access; never enter your primary password here.")
        OutlinedTextField(password, { password = it }, enabled = !busy, singleLine = true,
            label = { Text("App password") }, visualTransformation = PasswordVisualTransformation())
        Button(enabled = !busy && identifier.isNotBlank() && password.isNotBlank(), onClick = {
            val submitted = password.trim()
            password = ""
            run { atAuthority.appPassword(identifier, submitted, settings, allowFile) }
        }) { Text("Use an app password") }
        if (identity != null) {
            AtPendingList(identity.did, changed, onRetry = { run { atOutbox.retry(identity.did) } })
        }
        if (identity != null) OutlinedButton(enabled = !busy, onClick = { confirmDisconnect = true }) { Text("Disconnect") }
    }
    if (confirmDisconnect && identity != null) AlertDialog(
        onDismissRequest = { confirmDisconnect = false },
        text = { Text("Pending plays are paused for this DID. Public history is not deleted, and live status may remain visible until expiry.") },
        confirmButton = { TextButton(onClick = {
            confirmDisconnect = false
            run { if (!atAuthority.disconnect(identity.did)) error = "Disconnected locally; remote session revocation was not confirmed." }
        }) { Text("Disconnect") } },
        dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") } },
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
        Text("Pending deliveries", style = MaterialTheme.typography.titleMedium)
        rows.forEach { row ->
            val target = AtTarget.valueOf(row.target).label
            val title = runCatching { kotlinx.serialization.json.Json.decodeFromString<com.arn.scrobble.api.lastfm.ScrobbleData>(row.original).track }.getOrNull() ?: "Listen"
            val reason = when (row.state) {
                "PAUSED" -> "Authorization needed or destination permission missing"
                "RETRY" -> "Offline/retrying"
                "CONFLICT" -> "Record conflict"
                "METADATA" -> "Required track metadata missing or invalid"
                "REJECTED" -> "Record rejected by your provider"
                else -> "Queued"
            }
            Text("$target · $title — $reason")
        }
        OutlinedButton(onClick = onRetry) { Text("Retry paused plays") }
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
