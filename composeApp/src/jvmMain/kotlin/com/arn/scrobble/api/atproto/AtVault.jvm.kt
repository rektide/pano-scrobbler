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

internal actual fun platformAtVault(allowDesktopFile: Boolean): AtVaultFiles = DesktopAtVault(allowDesktopFile)

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
