package io.github.tomerar.freetvremote.protocol.tls

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.security.KeyFactory
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec

/** Protects the serialized private key at rest (the Android app wraps it with a Keystore key). */
public interface KeyProtector {
    public fun wrap(plain: ByteArray): ByteArray

    public fun unwrap(wrapped: ByteArray): ByteArray

    public object None : KeyProtector {
        override fun wrap(plain: ByteArray): ByteArray = plain

        override fun unwrap(wrapped: ByteArray): ByteArray = wrapped
    }
}

/**
 * Persists the [ClientIdentity] in a single file. The certificate is stored in
 * the clear, the PKCS#8 private key goes through the [KeyProtector].
 */
public class IdentityStore(
    private val file: File,
    private val protector: KeyProtector = KeyProtector.None,
    private val commonName: String = "free-tv-remote",
) {
    @Synchronized
    public fun loadOrCreate(): ClientIdentity {
        if (file.exists()) {
            try {
                return read()
            } catch (e: IOException) {
                // Corrupt or undecryptable (e.g. keystore reset): fall through and regenerate.
                file.delete()
            } catch (e: java.security.GeneralSecurityException) {
                file.delete()
            }
        }
        val identity = SelfSignedCertificate.generate(commonName)
        write(identity)
        return identity
    }

    @Synchronized
    public fun delete() {
        file.delete()
    }

    private fun write(identity: ClientIdentity) {
        val temp = File(file.parentFile, file.name + ".tmp")
        file.parentFile?.mkdirs()
        DataOutputStream(temp.outputStream().buffered()).use { out ->
            out.writeInt(FORMAT_VERSION)
            val key = protector.wrap(identity.privateKey.encoded)
            out.writeInt(key.size)
            out.write(key)
            val cert = identity.certificate.encoded
            out.writeInt(cert.size)
            out.write(cert)
        }
        if (!temp.renameTo(file)) {
            temp.delete()
            throw IOException("Could not store the client identity")
        }
    }

    private fun read(): ClientIdentity {
        DataInputStream(file.inputStream().buffered()).use { input ->
            if (input.readInt() != FORMAT_VERSION) throw IOException("Unknown identity format")
            val wrappedKey = readBlock(input)
            val certBytes = readBlock(input)
            val keyBytes = protector.unwrap(wrappedKey)
            val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyBytes))
            val certificate =
                CertificateFactory
                    .getInstance("X.509")
                    .generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
            return ClientIdentity(privateKey, certificate)
        }
    }

    private fun readBlock(input: DataInputStream): ByteArray {
        val size = input.readInt()
        if (size < 0 || size > MAX_BLOCK) throw IOException("Corrupt identity file")
        return ByteArray(size).also { input.readFully(it) }
    }

    private companion object {
        const val FORMAT_VERSION = 1
        const val MAX_BLOCK = 16 * 1024
    }
}
