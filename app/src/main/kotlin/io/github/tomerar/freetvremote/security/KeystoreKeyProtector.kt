package io.github.tomerar.freetvremote.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.tomerar.freetvremote.protocol.tls.KeyProtector
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps the TLS private key with an AES-GCM key that lives in the Android Keystore, so the key
 * material in the app's private storage is useless without the device's keystore. If the Keystore
 * is unusable the key is stored unwrapped in the app-private (not backed up) file; a marker byte
 * records which one was used.
 */
class KeystoreKeyProtector(
    private val alias: String = DEFAULT_ALIAS,
) : KeyProtector {
    override fun wrap(plain: ByteArray): ByteArray =
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
            byteArrayOf(MARKER_KEYSTORE) + cipher.iv + cipher.doFinal(plain)
        } catch (e: GeneralSecurityException) {
            byteArrayOf(MARKER_PLAIN) + plain
        }

    override fun unwrap(wrapped: ByteArray): ByteArray {
        if (wrapped.isEmpty()) throw GeneralSecurityException("Empty key blob")
        val body = wrapped.copyOfRange(1, wrapped.size)
        return when (wrapped[0]) {
            MARKER_PLAIN -> {
                body
            }

            MARKER_KEYSTORE -> {
                val iv = body.copyOfRange(0, IV_BYTES)
                val cipher =
                    Cipher.getInstance(TRANSFORMATION).apply {
                        init(Cipher.DECRYPT_MODE, existingKey(), GCMParameterSpec(TAG_BITS, iv))
                    }
                cipher.doFinal(body, IV_BYTES, body.size - IV_BYTES)
            }

            else -> {
                throw GeneralSecurityException("Unknown key blob format")
            }
        }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey =
        keyStore().getKey(alias, null) as? SecretKey ?: throw GeneralSecurityException("Keystore key is gone")

    private fun secretKey(): SecretKey {
        (keyStore().getKey(alias, null) as? SecretKey)?.let { return it }
        val spec =
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).apply { init(spec) }.generateKey()
    }

    private companion object {
        const val DEFAULT_ALIAS = "free_tv_remote_identity_wrap"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val MARKER_PLAIN: Byte = 0
        const val MARKER_KEYSTORE: Byte = 1
    }
}
