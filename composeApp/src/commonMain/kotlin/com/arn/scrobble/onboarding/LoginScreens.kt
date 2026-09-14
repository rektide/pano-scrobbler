package com.arn.scrobble.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arn.scrobble.api.AccountType
import com.arn.scrobble.api.UserAccountTemp
import com.arn.scrobble.icons.Icons
import com.arn.scrobble.icons.OpenInBrowser
import com.arn.scrobble.navigation.PanoRoute
import com.arn.scrobble.ui.ButtonWithIcon
import com.arn.scrobble.ui.ErrorText
import com.arn.scrobble.ui.PanoOutlinedSecureTextField
import com.arn.scrobble.ui.PanoOutlinedTextField
import com.arn.scrobble.ui.VerifyButton
import com.arn.scrobble.ui.testTagsAsResId
import com.arn.scrobble.utils.PlatformStuff
import com.arn.scrobble.utils.Stuff
import com.arn.scrobble.utils.Stuff.collectAsStateWithInitialValue
import com.arn.scrobble.utils.redactedMessage
import org.jetbrains.compose.resources.stringResource
import pano_scrobbler.composeapp.generated.resources.Res
import pano_scrobbler.composeapp.generated.resources.api_url
import pano_scrobbler.composeapp.generated.resources.atproto_callback_url
import pano_scrobbler.composeapp.generated.resources.atproto_handle
import pano_scrobbler.composeapp.generated.resources.atproto_waiting_callback
import pano_scrobbler.composeapp.generated.resources.listenbrainz_info
import pano_scrobbler.composeapp.generated.resources.login_in_browser
import pano_scrobbler.composeapp.generated.resources.password
import pano_scrobbler.composeapp.generated.resources.pref_token_label
import pano_scrobbler.composeapp.generated.resources.server_url
import pano_scrobbler.composeapp.generated.resources.username

@Composable
fun AtProtoLoginScreen(
    callbackUrl: String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel { LoginViewModel() },
) {
    val handle = rememberTextFieldState()
    val pastedCallback = rememberTextFieldState()
    var waitingForCallback by rememberSaveable { mutableStateOf(callbackUrl != null) }
    var openedAuthorizationUrl by rememberSaveable { mutableStateOf<String?>(null) }
    val startResult by viewModel.atProtoStartResult.collectAsStateWithLifecycle()
    val completionResult by viewModel.result.collectAsStateWithLifecycle(null)
    val connected by PlatformStuff.mainPrefs.data.collectAsStateWithInitialValue { prefs ->
        prefs.scrobbleAccounts.any { it.type == AccountType.ATPROTO }
    }
    val launch = startResult?.getOrNull()

    LaunchedEffect(callbackUrl) {
        if (!callbackUrl.isNullOrBlank()) {
            viewModel.atProtoComplete(callbackUrl)
        }
    }

    LaunchedEffect(launch?.authorizationUrl) {
        val authorizationUrl = launch?.authorizationUrl ?: return@LaunchedEffect
        waitingForCallback = true
        if (openedAuthorizationUrl != authorizationUrl) {
            openedAuthorizationUrl = authorizationUrl
            PlatformStuff.openInBrowser(authorizationUrl)
        }
    }

    LaunchedEffect(connected) {
        if (connected) onDone()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier,
    ) {
        if (!callbackUrl.isNullOrBlank()) {
            Text(stringResource(Res.string.atproto_waiting_callback))
            if (completionResult == null) {
                CircularWavyProgressIndicator(modifier = Modifier.align(Alignment.End))
            } else {
                ErrorText(completionResult?.exceptionOrNull()?.redactedMessage)
            }
        } else if (!waitingForCallback) {
            PanoOutlinedTextField(
                handle,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(Res.string.atproto_handle)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Done,
                ),
                onKeyboardAction = { performDefaultAction ->
                    performDefaultAction()
                    viewModel.atProtoStart(handle.text.toString())
                },
            )
            VerifyButton(
                onDone = { waitingForCallback = true },
                doStuff = { viewModel.atProtoStart(handle.text.toString()) },
                result = startResult,
            )
        } else {
            Text(stringResource(Res.string.atproto_waiting_callback))
            if (launch != null) {
                ButtonWithIcon(
                    onClick = { PlatformStuff.openInBrowser(launch.authorizationUrl) },
                    text = stringResource(Res.string.login_in_browser),
                    icon = Icons.OpenInBrowser,
                    modifier = Modifier.align(Alignment.End),
                )
            }
            PanoOutlinedTextField(
                pastedCallback,
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                label = { Text(stringResource(Res.string.atproto_callback_url)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Done,
                ),
                onKeyboardAction = { performDefaultAction ->
                    performDefaultAction()
                    viewModel.atProtoComplete(pastedCallback.text.toString())
                },
            )
            VerifyButton(
                onDone = onDone,
                doStuff = { viewModel.atProtoComplete(pastedCallback.text.toString()) },
                result = completionResult,
            )
        }
    }
}

@Composable
fun ListenBrainzLoginScreen(
    customServerSlot: Int?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel { LoginViewModel() },
) {
    val token = rememberTextFieldState()
    val apiRoot = rememberTextFieldState("https://")
    val result by viewModel.result.collectAsStateWithLifecycle(null)
    val doLogin = {
        if (customServerSlot != null) {
            viewModel.listenBrainzLogin(
                token.text.toString(),
                customServerSlot,
                apiRoot.text.toString()
            )
        } else {
            viewModel.listenBrainzLogin(token.text.toString(), customServerSlot)
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
    ) {

        if (customServerSlot != null) {
            PanoOutlinedTextField(
                apiRoot,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(Res.string.api_url)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    capitalization = KeyboardCapitalization.None,
                    imeAction = ImeAction.Next
                )
            )
        } else {
            ButtonWithIcon(
                onClick = {
                    PlatformStuff.openInBrowser("https://listenbrainz.org/profile")
                },
                text = stringResource(
                    Res.string.listenbrainz_info,
                    "listenbrainz.org/profile"
                ),
                icon = Icons.OpenInBrowser,
                maxLines = 3,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
            )
        }

        PanoOutlinedTextField(
            token,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(Res.string.pref_token_label)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Done
            ),
            onKeyboardAction = { performDefaultAction ->
                doLogin()
                performDefaultAction()
            }
        )

        VerifyButton(
            onDone = onDone,
            doStuff = doLogin,
            onTrustAll = if (customServerSlot != null) {
                {
                    viewModel.listenBrainzLogin(
                        token.text.toString(),
                        customServerSlot,
                        apiRoot.text.toString(),
                        true
                    )
                }
            } else null,
            result = result
        )
    }
}


@Composable
fun GnufmLoginScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel { LoginViewModel() },
) {
    val username = rememberTextFieldState()
    val password = rememberTextFieldState()
    val apiRoot = rememberTextFieldState()
    val result by viewModel.result.collectAsStateWithLifecycle(null)
    val doLogin = {
        viewModel.gnufmLogin(
            apiRoot.text.toString(),
            username.text.toString(),
            password.text.toString()
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.testTagsAsResId()
    ) {
        PanoOutlinedTextField(
            apiRoot,
            modifier = Modifier.fillMaxWidth().testTag("login_url"),
            singleLine = true,
            label = { Text(stringResource(Res.string.api_url)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Next
            )
        )
        PanoOutlinedTextField(
            username,
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("login_username"),
            label = { Text(stringResource(Res.string.username)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Next
            )
        )
        PanoOutlinedSecureTextField(
            password,
            modifier = Modifier.fillMaxWidth().testTag("login_password"),
            label = { Text(stringResource(Res.string.password)) },
            onKeyboardAction = { performDefaultAction ->
                doLogin()
                performDefaultAction()
            }
        )

        VerifyButton(
            onDone = onDone,
            doStuff = doLogin,
            result = result
        )
    }
}

@Composable
fun PleromaLoginScreen(
    onBackAndThenNavigate: (PanoRoute) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = viewModel { LoginViewModel() },
) {
    val apiRoot = rememberTextFieldState("https://")
    val result by viewModel.pleromaCredsResult.collectAsStateWithLifecycle(null)
    val redirectUri = remember {
        if (PlatformStuff.isTv)
            Stuff.DEEPLINK_SCHEME + "://auth/pleroma"
        else
            "urn:ietf:wg:oauth:2.0:oob"
    }
    val onSubmit = {
        viewModel.pleromaCreateApp(apiRoot.text.toString(), redirectUri)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier
    ) {
        PanoOutlinedTextField(
            apiRoot,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(Res.string.server_url)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Done
            ),
            onKeyboardAction = { performDefaultAction ->
                onSubmit()
                performDefaultAction()
            }
        )

        VerifyButton(
            onDone = {
                val creds = result?.getOrNull() ?: return@VerifyButton
                val _apiRoot = if (apiRoot.text.endsWith('/')) apiRoot.text else "${apiRoot.text}/"

                val userAccountTemp = UserAccountTemp(AccountType.PLEROMA, "", _apiRoot.toString())
                val url =
                    "${_apiRoot}oauth/authorize?client_id=${creds.client_id}&redirect_uri=${
                        creds.redirect_uri
                    }&response_type=code&scope=read+write"

                val route = if (PlatformStuff.isTv) {
                    PanoRoute.WebView(url, userAccountTemp, creds)
                } else {
                    PanoRoute.OobPleromaAuth(url, userAccountTemp, creds)
                }

                onBackAndThenNavigate(route)
            },
            doStuff = onSubmit,
            result = result
        )
    }
}
