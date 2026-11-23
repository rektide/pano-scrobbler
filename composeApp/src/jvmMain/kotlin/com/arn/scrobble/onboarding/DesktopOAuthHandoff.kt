package com.arn.scrobble.onboarding

import co.touchlab.kermit.Logger
import com.arn.scrobble.api.atproto.ATPROTO_REDIRECT_URI
import com.arn.scrobble.utils.DesktopStuff
import com.arn.scrobble.utils.PanoTrayUtils
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.io.IOException

internal object DesktopOAuthHandoff {
    const val IPC_COMMAND = "atproto-oauth-return"
    const val RELAY_ONLY = "--oauth-relay"
    val scheme = ATPROTO_REDIRECT_URI.substringBefore(':')
    val navigation = Channel<Unit>(Channel.CONFLATED)

    fun deliver(url: String) {
        if (AtprotoOAuthCallback.deliver(url)) {
            navigation.trySend(Unit)
            PanoTrayUtils.onTrayMenuItemClickedFn(PanoTrayUtils.ItemId.Open.name)
        }
    }

    // The developer JVM has no installed launcher. Advertise a hidden XDG entry using
    // the current classpath; let the desktop choose the handler without changing defaults.
    fun registerDevelopmentHandler() {
        val path = System.getProperty("pano.native.components.path") ?: return
        if (System.getProperty("pano.dev.oauthHandler") != "true") return

        val java = File(System.getProperty("java.home"), "bin/java").absolutePath
        val appData = DesktopStuff.appDataRoot.absoluteFile
        val args = appData.resolve("oauth-relay.args")
        val applications = DesktopStuff.xdgDataHome.resolve("applications")
        val desktop = applications.resolve("pano-scrobbler-dev-oauth.desktop")
        try {
            appData.mkdirs()
            applications.mkdirs()
            val classpath = System.getProperty("java.class.path").split(File.pathSeparator)
                .joinToString(File.pathSeparator) { File(it).absolutePath }
            args.writeText(listOf(
                "--enable-native-access=ALL-UNNAMED",
                "-Dpano.native.components.path=$path",
                "-cp", classpath, "com.arn.scrobble.main.MainKt", RELAY_ONLY,
            ).joinToString("\n", postfix = "\n", transform = ::argfileArgument))
            val exec = "${desktopExecArgument(java)} ${desktopExecArgument("@${args.absolutePath}")} %u"
            val contents = """
                [Desktop Entry]
                Type=Application
                Name=Pano Scrobbler (development OAuth)
                Exec=$exec
                NoDisplay=true
                MimeType=x-scheme-handler/$scheme;
            """.trimIndent() + "\n"
            if (!desktop.exists() || desktop.readText() != contents) desktop.writeText(contents)
            updateDesktopDatabase(applications)
        } catch (_: IOException) {
            Logger.w("Could not register the development OAuth handler; check desktop-file-utils and XDG paths")
        }
    }

    fun updateDesktopDatabase(applications: File) {
        try {
            val process = ProcessBuilder("update-desktop-database", applications.absolutePath)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start()
            if (process.waitFor() != 0) Logger.w("Could not refresh the XDG application cache")
        } catch (_: IOException) {
            Logger.w("Install desktop-file-utils to refresh the XDG application cache")
        }
    }

    private fun argfileArgument(value: String) = "\"" + value.replace("\\", "\\\\")
        .replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""

    // Desktop-entry escaping is applied before Exec quoting; this is not shell syntax.
    fun desktopExecArgument(value: String) = "\"" + buildString {
        for (char in value) when (char) {
            '\\' -> append("\\\\\\\\")
            '"', '$', '`' -> append("\\\\").append(char)
            '%' -> append("%%")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(char)
        }
    } + "\""
}
