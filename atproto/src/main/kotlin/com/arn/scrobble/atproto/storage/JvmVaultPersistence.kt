package com.arn.scrobble.atproto.storage

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.READ
import java.nio.file.attribute.AclEntry
import java.nio.file.attribute.AclEntryPermission
import java.nio.file.attribute.AclEntryType
import java.nio.file.attribute.AclFileAttributeView
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermissions
import java.util.EnumSet

/** Desktop only: private access, not OS-keystore encryption or protection from the same OS user. */
class JvmVaultPersistence(directory: File) : VaultPersistence {
    private val root = directory.toPath().toAbsolutePath().normalize()
    private val data = root.resolve("credentials.json")
    override val lockFile = root.resolve("credentials.lock").toFile()
    private val directoryPermissions = PosixFilePermissions.fromString("rwx------")
    private val filePermissions = PosixFilePermissions.fromString("rw-------")
    private val posix: Boolean
    private val acl: List<AclEntry>

    init {
        val parent = root.parent ?: throw IOException("Credential directory requires a parent")
        if (!Files.isDirectory(parent)) throw IOException("Credential directory parent does not exist")
        val store = Files.getFileStore(parent)
        posix = store.supportsFileAttributeView(PosixFileAttributeView::class.java)
        acl = if (!posix && store.supportsFileAttributeView(AclFileAttributeView::class.java)) {
            val owner = parent.fileSystem.userPrincipalLookupService
                .lookupPrincipalByName(System.getProperty("user.name"))
            listOf(AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(owner)
                .setPermissions(EnumSet.allOf(AclEntryPermission::class.java)).build())
        } else emptyList()
        if (!posix && acl.isEmpty()) throw IOException("Filesystem cannot enforce private credential access")
        try {
            Files.createDirectory(root, privacyAttribute(directory = true))
        } catch (_: FileAlreadyExistsException) {
            if (!Files.isDirectory(root, NOFOLLOW_LINKS)) throw IOException("Invalid credential directory")
        }
        protect(root, directory = true)
        try {
            Files.createFile(lockFile.toPath(), privacyAttribute(directory = false))
        } catch (_: FileAlreadyExistsException) {
            if (!Files.isRegularFile(lockFile.toPath(), NOFOLLOW_LINKS)) throw IOException("Invalid credential lock file")
        }
        protect(lockFile.toPath(), directory = false)
        if (Files.exists(data, NOFOLLOW_LINKS)) protect(data, directory = false)
    }

    override fun read(): ByteArray? = try {
        Files.newInputStream(data).use { it.readVaultBytes() }
    } catch (_: NoSuchFileException) {
        null
    }

    override fun write(bytes: ByteArray) {
        val temporary = Files.createTempFile(root, "credentials-", ".tmp", privacyAttribute(directory = false))
        try {
            FileOutputStream(temporary.toFile()).use {
                it.write(bytes)
                it.fd.sync()
            }
            Files.move(temporary, data, ATOMIC_MOVE, REPLACE_EXISTING)
            if (posix) FileChannel.open(root, READ).use { it.force(true) }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun protect(path: Path, directory: Boolean) {
        if (Files.isSymbolicLink(path)) throw IOException("Credential paths must not be symbolic links")
        if (posix) {
            Files.setPosixFilePermissions(path, if (directory) directoryPermissions else filePermissions)
        } else {
            Files.getFileAttributeView(path, AclFileAttributeView::class.java).acl = acl
        }
    }

    private fun privacyAttribute(directory: Boolean): FileAttribute<*> = if (posix) {
        PosixFilePermissions.asFileAttribute(if (directory) directoryPermissions else filePermissions)
    } else object : FileAttribute<List<AclEntry>> {
        override fun name() = "acl:acl"
        override fun value() = acl
    }
}
