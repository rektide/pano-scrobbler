package com.arn.scrobble.api.atproto

import com.arn.scrobble.utils.PlatformStuff
import java.io.File
import java.io.FileOutputStream
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.READ
import java.nio.file.attribute.PosixFilePermissions
import java.util.Base64
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

internal val atWindows: Boolean = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
internal actual val atVaultNeedsFileConsent = !atWindows

internal actual fun platformAtVault(allowDesktopFile: Boolean): AtVaultFiles =
    if (atWindows) WindowsAtVault(File(PlatformStuff.filesDir, "atproto-dpapi")) else DesktopAtVault(allowDesktopFile)

/** Uses Windows DPAPI CurrentUser via .NET Framework's ProtectedData. Only pipes carry payloads. */
internal class WindowsAtVault(private val directory: File) : AtVaultFiles {
    private val dir = directory.toPath()
    private val file = dir.resolve("authority")
    override val lockFile = dir.resolve("authority.lock").toFile()

    init {
        check(atWindows)
        Files.createDirectories(dir)
        check(Files.isDirectory(dir, NOFOLLOW_LINKS))
        if (Files.notExists(lockFile.toPath(), NOFOLLOW_LINKS)) {
            try { Files.createFile(lockFile.toPath()) }
            catch (_: java.nio.file.FileAlreadyExistsException) { /* another process created it */ }
        }
        check(Files.isRegularFile(lockFile.toPath(), NOFOLLOW_LINKS))
    }

    override fun read(): ByteArray? {
        if (Files.notExists(file, NOFOLLOW_LINKS)) return null
        check(Files.isRegularFile(file, NOFOLLOW_LINKS) && Files.size(file) in 2..MAX)
        val sealed = Files.readAllBytes(file)
        check(sealed[0] == 1.toByte())
        return dpapi("Unprotect", sealed.copyOfRange(1, sealed.size))
    }

    override fun write(bytes: ByteArray) {
        val encrypted = byteArrayOf(1) + dpapi("Protect", bytes)
        check(encrypted.size <= MAX)
        val part = Files.createTempFile(dir, "authority-", ".tmp")
        try {
            FileOutputStream(part.toFile()).use { out -> out.write(encrypted); out.fd.sync() }
            Files.move(part, file, ATOMIC_MOVE, REPLACE_EXISTING)
        } finally { Files.deleteIfExists(part) }
    }

    private fun dpapi(verb: String, payload: ByteArray): ByteArray {
        check(payload.isNotEmpty() && payload.size <= MAX)
        val root = File(checkNotNull(System.getenv("SystemRoot")))
        check(root.isAbsolute)
        val powershell = File(root, "System32/WindowsPowerShell/v1.0/powershell.exe")
        // Fixed code only in -EncodedCommand; the payload is never in arguments, environment, or a file.
        val script = """
            ${'$'}ErrorActionPreference = 'Stop'
            Add-Type -AssemblyName System.Security
            ${'$'}inputBytes = [Convert]::FromBase64String([Console]::In.ReadToEnd())
            ${'$'}outputBytes = [Security.Cryptography.ProtectedData]::$verb(${'$'}inputBytes, ${'$'}null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
            [Console]::Write([Convert]::ToBase64String(${'$'}outputBytes))
        """.trimIndent()
        val command = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        val process = ProcessBuilder(powershell.absolutePath, "-NoLogo", "-NoProfile", "-NonInteractive",
            "-WindowStyle", "Hidden", "-EncodedCommand", command)
            .redirectError(ProcessBuilder.Redirect.DISCARD).start()
        val pipe = FutureTask {
            process.outputStream.use { output -> Base64.getEncoder().wrap(output).use { it.write(payload) } }
            process.inputStream.use { it.readNBytes(2 * MAX.toInt() + 1) }
        }
        thread(name = "pano-dpapi-pipe", isDaemon = true) { pipe.run() }
        try {
            val output = pipe.get(15, TimeUnit.SECONDS)
            check(output.size <= 2 * MAX && process.waitFor(2, TimeUnit.SECONDS) && process.exitValue() == 0)
            return Base64.getDecoder().decode(output).also { check(it.size in 1..MAX) }
        } finally {
            if (process.isAlive) process.destroyForcibly()
            pipe.cancel(true)
        }
    }

    private companion object { const val MAX = 1_048_576L }
}

/** Explicitly opted-in fallback; not encryption, and never used on non-POSIX filesystems. */
private class DesktopAtVault(allow: Boolean) : AtVaultFiles {
    private val dir = File(PlatformStuff.filesDir, "atproto-private").toPath()
    private val dirPermission = PosixFilePermissions.fromString("rwx------")
    private val filePermission = PosixFilePermissions.fromString("rw-------")
    private val file = dir.resolve("authority")
    override val lockFile: File

    init {
        if (Files.notExists(dir, NOFOLLOW_LINKS)) {
            check(allow)
            try { Files.createDirectory(dir, PosixFilePermissions.asFileAttribute(dirPermission)) }
            catch (_: java.nio.file.FileAlreadyExistsException) { /* other process initialized it */ }
        }
        check(Files.isDirectory(dir, NOFOLLOW_LINKS) && Files.getPosixFilePermissions(dir, NOFOLLOW_LINKS) == dirPermission)
        val lock = dir.resolve("authority.lock")
        try { Files.createFile(lock, PosixFilePermissions.asFileAttribute(filePermission)) }
        catch (_: java.nio.file.FileAlreadyExistsException) { /* stable lock inode */ }
        check(Files.isRegularFile(lock, NOFOLLOW_LINKS) && Files.getPosixFilePermissions(lock, NOFOLLOW_LINKS) == filePermission)
        lockFile = lock.toFile()
    }

    override fun read(): ByteArray? {
        if (Files.notExists(file, NOFOLLOW_LINKS)) return null
        check(Files.isRegularFile(file, NOFOLLOW_LINKS) && Files.getPosixFilePermissions(file, NOFOLLOW_LINKS) == filePermission)
        check(Files.size(file) in 1..1_048_576)
        return Files.readAllBytes(file)
    }

    override fun write(bytes: ByteArray) {
        val next = Files.createTempFile(dir, "authority-", ".tmp", PosixFilePermissions.asFileAttribute(filePermission))
        try {
            FileOutputStream(next.toFile()).use { stream -> stream.write(bytes); stream.fd.sync() }
            Files.move(next, file, ATOMIC_MOVE, REPLACE_EXISTING)
            FileChannel.open(dir, READ).use { it.force(true) }
        } finally {
            Files.deleteIfExists(next)
        }
    }
}
