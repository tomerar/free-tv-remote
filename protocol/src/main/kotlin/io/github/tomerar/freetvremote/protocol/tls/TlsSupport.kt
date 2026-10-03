package io.github.tomerar.freetvremote.protocol.tls

import java.net.Socket
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509ExtendedTrustManager

/**
 * Android TVs present self-signed certificates, so the usual CA validation does
 * not apply. Trust is established through pairing and then enforced by pinning.
 */
public object TlsSupport {
    private const val KEY_ALIAS = "client"

    public fun socketFactory(identity: ClientIdentity, trustManager: ServerCertificateTrustManager): SSLSocketFactory =
        sslContext(identity, trustManager).socketFactory

    /** A TLS context that presents [identity] and delegates peer validation to [trustManager]. */
    public fun sslContext(identity: ClientIdentity, trustManager: X509ExtendedTrustManager): SSLContext {
        val context = SSLContext.getInstance("TLS")
        context.init(arrayOf<KeyManager>(SingleIdentityKeyManager(identity)), arrayOf<TrustManager>(trustManager), SecureRandom())
        return context
    }

    /** Presents one fixed identity for every handshake. */
    private class SingleIdentityKeyManager(
        private val identity: ClientIdentity,
    ) : X509ExtendedKeyManager() {
        private val chain = arrayOf(identity.certificate)

        override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String> = arrayOf(KEY_ALIAS)

        override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String =
            KEY_ALIAS

        override fun chooseEngineClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, engine: SSLEngine?): String =
            KEY_ALIAS

        override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String> = arrayOf(KEY_ALIAS)

        override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String = KEY_ALIAS

        override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?): String =
            KEY_ALIAS

        override fun getCertificateChain(alias: String?): Array<X509Certificate>? = if (alias == KEY_ALIAS) chain else null

        override fun getPrivateKey(alias: String?): PrivateKey? = if (alias == KEY_ALIAS) identity.privateKey else null
    }
}

/** Shared plumbing: the app never validates client certificates, it is the client. */
public abstract class ServerCertificateTrustManager : X509ExtendedTrustManager() {
    @Volatile
    public var serverCertificate: X509Certificate? = null
        private set

    protected abstract fun verify(leaf: X509Certificate)

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("Server sent no certificate")
        serverCertificate = leaf
        verify(leaf)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        checkServerTrusted(chain, authType)
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        checkServerTrusted(chain, authType)
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        // Not used: this trust manager is only installed on the client side.
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) {
        // Not used.
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) {
        // Not used.
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

/** Accepts any server certificate and remembers it (used while pairing, before a pin exists). */
public class RecordingTrustManager : ServerCertificateTrustManager() {
    override fun verify(leaf: X509Certificate) {
        // Trust on first use: the pairing code proves the TV is the one the user is looking at.
    }
}

/** Only accepts a server whose public key matches the pin recorded during pairing. */
public class PinnedTrustManager(
    private val expectedPin: ByteArray,
) : ServerCertificateTrustManager() {
    override fun verify(leaf: X509Certificate) {
        if (!java.security.MessageDigest.isEqual(leaf.publicKeyPin(), expectedPin)) {
            throw CertificateException("Server certificate does not match the pinned key")
        }
    }
}
