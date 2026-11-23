package com.arn.scrobble

import com.arn.scrobble.api.atproto.WindowsAtVault
import com.arn.scrobble.api.atproto.atWindows
import org.junit.Assume.assumeTrue
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull

class AtWindowsVaultTest {
    @Test fun systemDpapiRoundTripAndCorruptionFailClosed() {
        assumeTrue("Windows DPAPI fixture", atWindows)
        val root = Files.createTempDirectory(File("build").toPath(), "dpapi-fixture-").toFile()
        try {
            val vault = WindowsAtVault(root)
            assertNull(vault.read())
            val fixture = "ordinary test payload".encodeToByteArray()
            vault.write(fixture)
            val file = File(root, "authority")
            assertFalse(file.readBytes().decodeToString().contains("ordinary test payload"))
            assertContentEquals(fixture, WindowsAtVault(root).read())
            vault.write(fixture.reversedArray())
            assertContentEquals(fixture.reversedArray(), WindowsAtVault(root).read())
            val corrupted = file.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            file.writeBytes(corrupted)
            assertFails { WindowsAtVault(root).read() }
        } finally { root.deleteRecursively() }
    }
}
