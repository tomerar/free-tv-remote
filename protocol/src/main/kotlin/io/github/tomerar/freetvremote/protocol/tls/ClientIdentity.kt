package io.github.tomerar.freetvremote.protocol.tls

import java.security.MessageDigest
import java.security.PrivateKey
import java.security.cert.X509Certificate

/** The app's long-lived TLS client identity (self-signed certificate + private key). */
public class ClientIdentity(
    public val privateKey: PrivateKey,
    public val certificate: X509Certificate,
)

/** SHA-256 of the DER-encoded SubjectPublicKeyInfo; used to pin a TV after pairing. */
public fun X509Certificate.publicKeyPin(): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(publicKey.encoded)
