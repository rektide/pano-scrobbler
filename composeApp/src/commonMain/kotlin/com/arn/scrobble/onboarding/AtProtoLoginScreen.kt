package com.arn.scrobble.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arn.scrobble.api.AccountType
import com.arn.scrobble.api.atproto.PanoAtProto
import com.arn.scrobble.atproto.oauth.AtProtoAccount
import com.arn.scrobble.atproto.posting.AtProtoTarget
import com.arn.scrobble.ui.PanoOutlinedTextField
import com.arn.scrobble.utils.PlatformStuff
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import pano_scrobbler.composeapp.generated.resources.*

@Composable
fun AtProtoLoginScreen(onDone: () -> Unit, modifier: Modifier = Modifier) {
    if (PlatformStuff.isTv) return
    val identifier = rememberTextFieldState()
    val callback = rememberTextFieldState()
    val state by PanoAtProto.login.collectAsStateWithLifecycle()
    val prefs by PlatformStuff.mainPrefs.data.collectAsStateWithLifecycle(null)
    var account by remember { mutableStateOf<AtProtoAccount?>(null) }
    var targetError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        try { account = PanoAtProto.authority.currentAccount() }
        catch (cause: CancellationException) { throw cause }
        catch (_: Exception) { targetError = true }
    }
    LaunchedEffect(state.launch) { state.launch?.let { PlatformStuff.openInBrowser(it.authorizationUrl) } }
    LaunchedEffect(state.completed) {
        state.completed?.let { if (PanoAtProto.consumeCompleted(it.session)) onDone() }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(Res.string.atproto_info))
        PanoOutlinedTextField(identifier, singleLine = true, label = { Text(stringResource(Res.string.atproto_account)) },
            modifier = Modifier.fillMaxWidth().testTag("atproto_identifier"))
        Button(onClick = { PanoAtProto.begin(identifier.text.toString()) }, enabled = !state.busy && identifier.text.isNotBlank()) {
            Text(stringResource(Res.string.atproto_sign_in))
        }
        if (state.busy) CircularProgressIndicator()
        state.launch?.let { launch ->
            Text(stringResource(Res.string.atproto_waiting))
            TextButton(onClick = { PlatformStuff.openInBrowser(launch.authorizationUrl) }) { Text(stringResource(Res.string.atproto_sign_in)) }
        }
        PanoOutlinedTextField(callback, singleLine = true, label = { Text(stringResource(Res.string.atproto_callback)) },
            modifier = Modifier.fillMaxWidth().testTag("atproto_callback"))
        Button(onClick = { PanoAtProto.completeCallback(callback.text.toString()) }, enabled = !state.busy && callback.text.isNotBlank()) {
            Text(stringResource(Res.string.atproto_complete))
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
            if (state.publication != null) TextButton(onClick = PanoAtProto::retryPublication, enabled = !state.busy) { Text(stringResource(Res.string.atproto_publish)) }
        }
        account?.let { current ->
            Text(stringResource(Res.string.atproto_targets), style = MaterialTheme.typography.titleMedium)
            val reference = prefs?.scrobbleAccounts?.singleOrNull { it.type == AccountType.ATPROTO && it.atProtoSession == current.session }
            val granted = AtProtoTarget.grantedBy(current.grantedScopes)
            for (target in AtProtoTarget.entries) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(checked = target in reference?.atProtoTargets.orEmpty() && target in granted,
                    enabled = reference != null && target in granted,
                    onCheckedChange = { enabled -> scope.launch {
                        try { PanoAtProto.select(current.session, target, enabled); targetError = false }
                        catch (cause: CancellationException) { throw cause }
                        catch (_: Exception) { targetError = true }
                    } })
                Text(if (target in granted) target.name else "${target.name} — ${stringResource(Res.string.atproto_not_granted)}")
            }
        }
        if (targetError) Text("Authorization or destination settings could not be read or saved. Sign in again if necessary.", color = MaterialTheme.colorScheme.error)
    }
}
