package com.arn.scrobble

import com.arn.scrobble.api.atproto.AT_CALLBACK
import com.arn.scrobble.main.handleDesktopAtCallback
import com.arn.scrobble.utils.appImageLauncher
import java.io.File
import kotlin.test.*

class AtDesktopCallbackTest {
    @Test fun callbacksNeverFallThroughToArgvOrLeakDiagnostics() {
        val url = "$AT_CALLBACK?code=secret-fixture&state=opaque&iss=https%3A%2F%2Fas.example.com"
        val seen = mutableListOf<String>()
        val logs = mutableListOf<String>()
        fun handle(vararg args: String) = handleDesktopAtCallback(arrayOf(*args), { seen += it }, { logs += it })
        assertFalse(handle("--minimized"))
        assertTrue(handle(url))
        assertTrue(handle("--atproto-callback", url))
        assertTrue(handle(url, url))
        assertTrue(handle("--atproto-callback", url, "--quit"))
        assertTrue(handle("--data-dir", "fixture", url))
        assertTrue(handle(url.replace("/oauth/callback", "/wrong")))
        assertTrue(handle("$url&code=other"))
        assertTrue(handleDesktopAtCallback(arrayOf(url), { error(it) }, { logs += it }))
        assertEquals(listOf(url, url), seen)
        assertEquals(6, logs.size)
        assertTrue(logs.all { "secret-fixture" !in it && '?' !in it && "opaque" !in it })
    }

    @Test fun packagedEntryPointsRegisterTheSameProtocolAndQuoteOneArgument() {
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, "desktopApp/inno/installer.iss").isFile }
        val linux = File(root, "desktopApp/pano-scrobbler.desktop").readText()
        assertContains(linux, "MimeType=x-scheme-handler/io.github.kawaiidango;")
        assertContains(linux, "Exec=pano-scrobbler %u")
        assertContains(File(root, "desktopApp/package-for-linux.sh").readText(), "Exec=AppRun %u")
        val launcher = appImageLauncher(linux, "/Example Path/pano%u-${'$'}f.AppImage")
        assertContains(launcher, "Exec=\"/Example Path/pano%%u-\\\\${'$'}f.AppImage\" %u")
        val windows = File(root, "desktopApp/inno/installer.iss").readText()
        val scheme = windows.lineSequence().filter { "Software\\Classes\\io.github.kawaiidango" in it }.toList()
        assertEquals(3, scheme.size)
        assertTrue(scheme.all { "Tasks: not extractonly" in it })
        assertTrue(scheme.any { "uninsdeletekey" in it })
        assertTrue(scheme.any { "ValueName: \"URL Protocol\"" in it })
        assertTrue(scheme.any { "\"\"{app}\\{#EXE_NAME}\"\" --atproto-callback \"\"%1\"\"" in it })
    }
}
