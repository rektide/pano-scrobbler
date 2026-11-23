package com.arn.scrobble.navigation

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.core.util.Consumer
import com.arn.scrobble.onboarding.AtprotoOAuthCallback

@Composable
actual fun NavFromOutsideEffect(
    onNavigate: (PanoRoute) -> Unit,
    isAndroidDialogActivity: Boolean,
) {
    val activity = LocalActivity.current as? ComponentActivity
    val activityRestored = LocalActivityRestoredFlag.current

    DisposableEffect(Unit) {
        fun deliverOAuth(intent: Intent): Boolean {
            val url = intent.dataString ?: return false
            if (isAndroidDialogActivity || intent.action != Intent.ACTION_VIEW || !AtprotoOAuthCallback.deliver(url))
                return false
            intent.data = null
            onNavigate(PanoRoute.LoginAtproto)
            return true
        }

        val consumer = Consumer<Intent> {
            if (deliverOAuth(it)) return@Consumer
            val route = if (isAndroidDialogActivity)
                DeepLinkUtils.parseDialogDeepLink(it)
            else
                DeepLinkUtils.parseDeepLink(it)

            if (route != null)
                onNavigate(route)
        }

        activity?.intent?.let {
            if (activityRestored)
                deliverOAuth(it)
            else
                consumer.accept(it)
        }

        activity?.addOnNewIntentListener(consumer)

        onDispose {
            activity?.removeOnNewIntentListener(consumer)
        }
    }
}
