package io.github.tomerar.freetvremote.protocol.pairing

import java.math.BigInteger
import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.interfaces.RSAPublicKey

/**
 * Computes the pairing secret the TV expects:
 * `SHA-256(clientModulus | clientExponent | serverModulus | serverExponent | codeTail)`
 * where every number is its unsigned big-endian minimal byte representation and
 * `codeTail` is the last 4 hex characters (2 bytes) of the on-screen code.
 * The first two hex characters of the code are a checksum byte: the first byte
 * of the digest must equal it.
 */
public object PairingSecret {
    public const val CODE_LENGTH: Int = 6
    private const val CHECK_CHARS = 2
    private const val HEX_RADIX = 16

    /** Normalises user input: trims, removes spaces and upper-cases. */
    public fun normalizeCode(input: String): String = input.filterNot { it.isWhitespace() }.uppercase()

    public fun isWellFormedCode(code: String): Boolean =
        code.length == CODE_LENGTH && code.all { Character.digit(it, HEX_RADIX) >= 0 }

    /**
     * @return the secret digest, or `null` when the code is malformed or its
     * checksum byte does not match (i.e. the user mistyped the code).
     */
    public fun compute(clientKey: RSAPublicKey, serverKey: RSAPublicKey, code: String): ByteArray? {
        val normalized = normalizeCode(code)
        if (!isWellFormedCode(normalized)) return null
        val digest =
            MessageDigest
                .getInstance("SHA-256")
                .apply {
                    update(unsignedBytes(clientKey.modulus))
                    update(unsignedBytes(clientKey.publicExponent))
                    update(unsignedBytes(serverKey.modulus))
                    update(unsignedBytes(serverKey.publicExponent))
                    update(hexToBytes(normalized.substring(CHECK_CHARS)))
                }.digest()
        val expectedFirstByte = normalized.substring(0, CHECK_CHARS).toInt(HEX_RADIX).toByte()
        return if (digest[0] == expectedFirstByte) digest else null
    }

    public fun compute(clientCertificate: Certificate, serverCertificate: Certificate, code: String): ByteArray? =
        compute(
            clientCertificate.publicKey as RSAPublicKey,
            serverCertificate.publicKey as RSAPublicKey,
            code,
        )

    internal fun unsignedBytes(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        return if (raw.size > 1 && raw[0] == 0.toByte()) raw.copyOfRange(1, raw.size) else raw
    }

    internal fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Odd hex length" }
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(HEX_RADIX).toByte()
        }
    }
}
