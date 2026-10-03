package io.github.tomerar.freetvremote.protocol.tls

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.interfaces.RSAPublicKey
import java.util.Date

class TlsIdentityTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `generated certificate is a valid self signed RSA certificate`() {
        val identity = SelfSignedCertificate.generate("unit-test")
        val cert = identity.certificate
        cert.checkValidity()
        cert.verify(cert.publicKey) // signed by its own key
        assertEquals("CN=unit-test", cert.subjectX500Principal.name)
        assertEquals(cert.subjectX500Principal, cert.issuerX500Principal)
        assertEquals(2048, (cert.publicKey as RSAPublicKey).modulus.bitLength())
        assertEquals("SHA256withRSA", cert.sigAlgName)
        assertEquals(3, cert.version)
    }

    @Test
    fun `far future expiry uses generalized time and still parses`() {
        val identity = SelfSignedCertificate.generate("long-lived", validityDays = 365L * 60)
        assertTrue(identity.certificate.notAfter.after(Date(Date().time + 365L * 50 * 86_400_000L)))
    }

    @Test
    fun `serials differ between certificates`() {
        val a = SelfSignedCertificate.generate("a").certificate.serialNumber
        val b = SelfSignedCertificate.generate("a").certificate.serialNumber
        assertNotEquals(a, b)
    }

    @Test
    fun `identity is persisted and reloaded unchanged`() {
        val file = File(tmp.root, "identity.bin")
        val first = IdentityStore(file).loadOrCreate()
        val second = IdentityStore(file).loadOrCreate()
        assertArrayEquals(first.certificate.encoded, second.certificate.encoded)
        assertArrayEquals(first.privateKey.encoded, second.privateKey.encoded)
    }

    @Test
    fun `key protector is applied to the stored private key`() {
        val file = File(tmp.root, "identity.bin")
        val xor =
            object : KeyProtector {
                override fun wrap(plain: ByteArray) = ByteArray(plain.size) { (plain[it].toInt() xor 0x5A).toByte() }

                override fun unwrap(wrapped: ByteArray) = wrap(wrapped)
            }
        val created = IdentityStore(file, xor).loadOrCreate()
        assertFalse("plain key must not be on disk", file.readBytes().indexOfSubArray(created.privateKey.encoded.copyOf(32)) >= 0)
        val loaded = IdentityStore(file, xor).loadOrCreate()
        assertArrayEquals(created.privateKey.encoded, loaded.privateKey.encoded)
    }

    @Test
    fun `corrupt identity file is replaced instead of crashing`() {
        val file = File(tmp.root, "identity.bin")
        file.writeBytes(byteArrayOf(1, 2, 3))
        val identity = IdentityStore(file).loadOrCreate()
        identity.certificate.checkValidity()
        assertTrue(file.length() > 100)
    }

    @Test
    fun `pin is stable and differs per key`() {
        val a = SelfSignedCertificate.generate("a").certificate
        val b = SelfSignedCertificate.generate("b").certificate
        assertArrayEquals(a.publicKeyPin(), a.publicKeyPin())
        assertFalse(a.publicKeyPin().contentEquals(b.publicKeyPin()))
        assertEquals(32, a.publicKeyPin().size)
    }

    private fun ByteArray.indexOfSubArray(sub: ByteArray): Int {
        outer@ for (i in 0..size - sub.size) {
            for (j in sub.indices) if (this[i + j] != sub[j]) continue@outer
            return i
        }
        return -1
    }
}
