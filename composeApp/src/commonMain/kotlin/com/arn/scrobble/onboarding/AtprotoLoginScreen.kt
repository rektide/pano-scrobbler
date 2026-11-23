package com.arn.scrobble.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arn.scrobble.api.AccountType
import com.arn.scrobble.ui.ErrorText
import com.arn.scrobble.ui.PanoOutlinedSecureTextField
import com.arn.scrobble.ui.PanoOutlinedTextField
import com.arn.scrobble.ui.accountTypeLabel
import com.arn.scrobble.ui.testTagsAsResId
import com.arn.scrobble.utils.PlatformStuff
import com.arn.scrobble.utils.Stuff.collectAsStateWithInitialValue
import org.jetbrains.compose.resources.stringResource
import pano_scrobbler.composeapp.generated.resources.*

@Composable
fun AtprotoLoginScreen(
    onDone: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AtprotoLoginViewModel = viewModel { AtprotoLoginViewModel() },
) {
    val connection by PlatformStuff.mainPrefs.data.collectAsStateWithInitialValue { it.atprotoConnection }
    val hasScrobbler by PlatformStuff.mainPrefs.data.collectAsStateWithInitialValue { it.currentAccount != null }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var replacing by rememberSaveable(connection?.did) { mutableStateOf(false) }
    val handle = key(connection?.did) { rememberTextFieldState(connection?.handle.orEmpty()) }
    // Passwords must never go into saved instance state (including Navigation's saved state).
    val password = remember { TextFieldState() }
    var teal by rememberSaveable(connection?.destinations) { mutableStateOf(connection?.destinations?.contains(AccountType.TEAL) ?: true) }
    var rocksky by rememberSaveable(connection?.destinations) { mutableStateOf(connection?.destinations?.contains(AccountType.ROCKSKY) ?: true) }
    val destinations = buildSet {
        if (teal) add(AccountType.TEAL)
        if (rocksky) add(AccountType.ROCKSKY)
    }
    val canSubmit = !state.busy && handle.text.isNotBlank() && destinations.isNotEmpty() && password.text.isNotBlank()
    fun submit() {
        if (canSubmit) {
            viewModel.signInWithPassword(handle.text.toString(), password.text.toString(), destinations)
            password.clearText()
        }
    }

    LaunchedEffect(state.signedIn) {
        if (state.signedIn) {
            viewModel.acknowledgeSignIn()
            replacing = false
            onDone()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = modifier.testTagsAsResId()) {
        val connected = connection
        if (connected != null) {
            val status = if (connected.needsReconnect) Res.string.atproto_reconnect_needed else Res.string.atproto_connected
            Text(stringResource(status, connected.handle), style = MaterialTheme.typography.titleMedium)
            val labels = connected.destinations.map { accountTypeLabel(it) }
            Text(stringResource(Res.string.atproto_destinations) + ": " + labels.joinToString(", "))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!replacing && !connected.needsReconnect) {
                    FilledTonalButton(shapes = ButtonDefaults.shapes(), enabled = !state.busy, onClick = { replacing = true }) {
                        Text(stringResource(Res.string.atproto_sign_in_again))
                    }
                }
                TextButton(shapes = ButtonDefaults.shapes(), enabled = !state.busy, onClick = viewModel::disconnect) {
                    Text(stringResource(Res.string.atproto_disconnect))
                }
            }
            if (!hasScrobbler) {
                TextButton(shapes = ButtonDefaults.shapes(), enabled = !state.busy, onClick = onOpenSettings) {
                    Text(stringResource(Res.string.settings))
                }
            }
        }

        Text(stringResource(Res.string.atproto_sign_in_only), style = MaterialTheme.typography.bodyMedium)

        if (connected == null || connected.needsReconnect || replacing) {
            PanoOutlinedTextField(
                handle,
                modifier = Modifier.fillMaxWidth().testTag("atproto_handle"),
                enabled = !state.busy,
                singleLine = true,
                label = { Text(stringResource(Res.string.atproto_handle)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Next,
                ),
            )
            Column {
                Text(stringResource(Res.string.atproto_destinations), style = MaterialTheme.typography.titleMedium)
                AtprotoDestination(AccountType.TEAL, teal, !state.busy) { teal = !teal }
                AtprotoDestination(AccountType.ROCKSKY, rocksky, !state.busy) { rocksky = !rocksky }
            }
            Text(stringResource(Res.string.atproto_app_password_info), style = MaterialTheme.typography.bodyMedium)
            PanoOutlinedSecureTextField(
                password,
                modifier = Modifier.fillMaxWidth().testTag("atproto_app_password"),
                enabled = !state.busy,
                label = { Text(stringResource(Res.string.atproto_app_password)) },
                onKeyboardAction = { performDefaultAction ->
                    submit()
                    performDefaultAction()
                },
            )
            FilledTonalButton(
                shapes = ButtonDefaults.shapes(),
                enabled = canSubmit,
                onClick = ::submit,
                modifier = Modifier.align(Alignment.End).testTag("atproto_password_submit"),
            ) {
                Text(stringResource(Res.string.login_submit))
            }
        }
        if (state.busy) CircularWavyProgressIndicator(Modifier.align(Alignment.End))
        ErrorText(state.error?.let { stringResource(it) })
    }
}

@Composable
private fun AtprotoDestination(type: AccountType, checked: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().toggleable(checked, enabled, Role.Checkbox) { onClick() }
            .padding(vertical = 12.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(accountTypeLabel(type))
    }
}
