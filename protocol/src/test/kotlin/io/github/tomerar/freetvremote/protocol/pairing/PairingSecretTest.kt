package io.github.tomerar.freetvremote.protocol.pairing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.interfaces.RSAPublicKey
import java.security.spec.RSAPublicKeySpec

class PairingSecretTest {
    private fun key(modulus: BigInteger, exponent: Long = 65537): RSAPublicKey =
        KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, BigInteger.valueOf(exponent))) as RSAPublicKey

    // Fixed, deliberately "round" 2048-bit style moduli so the expected bytes are easy to reason about.
    private val clientModulus = BigInteger("c0" + "11".repeat(255), 16)
    private val serverModulus = BigInteger("a5" + "22".repeat(255), 16)

    /** Independent reference: hex strings concatenated the way a Python implementation would. */
    private fun reference(code: String): ByteArray {
        fun hex(n: BigInteger): String = n.toString(16).let { if (it.length % 2 == 1) "0$it" else it }
        val joined =
            hex(clientModulus) + hex(BigInteger.valueOf(65537)) + hex(serverModulus) + hex(BigInteger.valueOf(65537)) +
                code.substring(2)
        val bytes = ByteArray(joined.length / 2) { joined.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    private fun validCode(tail: String): String {
        val first = "%02X".format(reference("00$tail")[0].toInt() and 0xFF)
        return first + tail
    }

    @Test
    fun `exponent 65537 is encoded as three bytes`() {
        assertArrayEquals(byteArrayOf(1, 0, 1), PairingSecret.unsignedBytes(BigInteger.valueOf(65537)))
    }

    @Test
    fun `sign byte is stripped from moduli with the top bit set`() {
        val bytes = PairingSecret.unsignedBytes(clientModulus)
        assertEquals(256, bytes.size)
        assertEquals(0xC0.toByte(), bytes[0])
    }

    @Test
    fun `secret matches the independent reference implementation`() {
        val code = validCode("1A2B")
        val secret = PairingSecret.compute(key(clientModulus), key(serverModulus), code)
        assertNotNull(secret)
        assertArrayEquals(reference(code), secret)
    }

    @Test
    fun `code is case and whitespace insensitive`() {
        val code = validCode("ABCD")
        val expected = PairingSecret.compute(key(clientModulus), key(serverModulus), code)
        val messy = " " + code.lowercase().substring(0, 3) + " " + code.lowercase().substring(3) + " "
        assertArrayEquals(expected, PairingSecret.compute(key(clientModulus), key(serverModulus), messy))
    }

    @Test
    fun `wrong checksum byte is rejected locally`() {
        val code = validCode("1A2B")
        val wrongFirst = "%02X".format((code.substring(0, 2).toInt(16) + 1) and 0xFF)
        assertNull(PairingSecret.compute(key(clientModulus), key(serverModulus), wrongFirst + "1A2B"))
    }

    @Test
    fun `a mistyped tail is rejected locally`() {
        val code = validCode("1A2B")
        assertNull(PairingSecret.compute(key(clientModulus), key(serverModulus), code.dropLast(1) + "C"))
    }

    @Test
    fun `malformed codes are rejected`() {
        for (bad in listOf("", "12345", "1234567", "12345G", "ZZZZZZ")) {
            assertFalse(bad, PairingSecret.isWellFormedCode(bad))
            assertNull(PairingSecret.compute(key(clientModulus), key(serverModulus), bad))
        }
        assertTrue(PairingSecret.isWellFormedCode("1a2B3c"))
    }

    @Test
    fun `swapping client and server changes the secret`() {
        val code = validCode("1A2B")
        val a = PairingSecret.compute(key(clientModulus), key(serverModulus), code)
        assertNotNull(a)
        // The swapped order hashes different bytes, so the same code almost surely fails the checksum.
        val b = PairingSecret.compute(key(serverModulus), key(clientModulus), code)
        assertTrue(b == null || !a!!.contentEquals(b))
    }
}
