package com.arn.scrobble.atproto.storage

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Versioned authenticated encryption. The key owner is the platform, never this file. */
class AesGcmVaultCipher(private val key: () -> SecretKey) : VaultCipher {
    override fun encrypt(plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        check(cipher.iv.size == NONCE_BYTES)
        cipher.updateAAD(MAGIC)
        return MAGIC + cipher.iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(ciphertext: ByteArray): ByteArray {
        if (ciphertext.size < MAGIC.size + NONCE_BYTES + 16 ||
            !ciphertext.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)
        ) throw GeneralSecurityException("Invalid AT Protocol vault envelope")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            key(),
            GCMParameterSpec(128, ciphertext.copyOfRange(MAGIC.size, MAGIC.size + NONCE_BYTES)),
        )
        cipher.updateAAD(MAGIC)
        return cipher.doFinal(ciphertext, MAGIC.size + NONCE_BYTES, ciphertext.size - MAGIC.size - NONCE_BYTES)
    }

    private companion object {
        val MAGIC = byteArrayOf(0x50, 0x41, 0x56, 1)
        const val NONCE_BYTES = 12
    }
}
