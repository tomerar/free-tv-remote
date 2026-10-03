package io.github.tomerar.freetvremote.protocol.tls

import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date

/** Generates RSA self-signed certificates without any third-party crypto library. */
public object SelfSignedCertificate {
    private const val RSA_KEY_BITS = 2048
    private const val SERIAL_BITS = 63
    private const val OID_SHA256_WITH_RSA = "1.2.840.113549.1.1.11"
    private const val OID_COMMON_NAME = "2.5.4.3"
    private const val X509_V3 = 2L
    private const val MILLIS_PER_DAY = 86_400_000L
    private const val BACKDATE_DAYS = 1L
    private const val DEFAULT_VALIDITY_DAYS = 365L * 25

    public fun generateKeyPair(bits: Int = RSA_KEY_BITS): KeyPair =
        KeyPairGenerator.getInstance("RSA").apply { initialize(bits, SecureRandom()) }.generateKeyPair()

    public fun generate(
        commonName: String,
        validityDays: Long = DEFAULT_VALIDITY_DAYS,
        keyPair: KeyPair = generateKeyPair(),
        now: Date = Date(),
    ): ClientIdentity {
        val notBefore = Date(now.time - BACKDATE_DAYS * MILLIS_PER_DAY)
        val notAfter = Date(now.time + validityDays * MILLIS_PER_DAY)
        val signatureAlgorithm = Der.sequence(Der.oid(OID_SHA256_WITH_RSA), Der.nullValue())
        val name = Der.sequence(Der.set(Der.sequence(Der.oid(OID_COMMON_NAME), Der.utf8(commonName))))
        val serial = BigInteger(SERIAL_BITS, SecureRandom()).add(BigInteger.ONE)

        val tbs =
            Der.sequence(
                Der.explicit0(Der.integer(BigInteger.valueOf(X509_V3))),
                Der.integer(serial),
                signatureAlgorithm,
                name,
                Der.sequence(Der.time(notBefore), Der.time(notAfter)),
                name,
                keyPair.public.encoded,
            )
        val signature =
            Signature.getInstance("SHA256withRSA").run {
                initSign(keyPair.private)
                update(tbs)
                sign()
            }
        val der = Der.sequence(tbs, signatureAlgorithm, Der.bitString(signature))
        val certificate =
            CertificateFactory
                .getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(der)) as X509Certificate
        return ClientIdentity(keyPair.private, certificate)
    }
}
