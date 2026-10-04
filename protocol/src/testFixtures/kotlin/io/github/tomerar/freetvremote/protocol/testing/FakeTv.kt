package io.github.tomerar.freetvremote.protocol.testing

import io.github.tomerar.freetvremote.protocol.MessageFraming
import io.github.tomerar.freetvremote.protocol.pairing.PairingSecret
import io.github.tomerar.freetvremote.protocol.proto.PairingConfigurationAck
import io.github.tomerar.freetvremote.protocol.proto.PairingEncoding
import io.github.tomerar.freetvremote.protocol.proto.PairingMessage
import io.github.tomerar.freetvremote.protocol.proto.PairingOption
import io.github.tomerar.freetvremote.protocol.proto.PairingRequestAck
import io.github.tomerar.freetvremote.protocol.proto.PairingRole
import io.github.tomerar.freetvremote.protocol.proto.PairingSecretAck
import io.github.tomerar.freetvremote.protocol.proto.RemoteAppInfo
import io.github.tomerar.freetvremote.protocol.proto.RemoteConfigure
import io.github.tomerar.freetvremote.protocol.proto.RemoteDeviceInfo
import io.github.tomerar.freetvremote.protocol.proto.RemoteDirection
import io.github.tomerar.freetvremote.protocol.proto.RemoteImeKeyInject
import io.github.tomerar.freetvremote.protocol.proto.RemoteImeShowRequest
import io.github.tomerar.freetvremote.protocol.proto.RemoteMessage
import io.github.tomerar.freetvremote.protocol.proto.RemotePingRequest
import io.github.tomerar.freetvremote.protocol.proto.RemoteSetActive
import io.github.tomerar.freetvremote.protocol.proto.RemoteSetVolumeLevel
import io.github.tomerar.freetvremote.protocol.proto.RemoteStart
import io.github.tomerar.freetvremote.protocol.proto.RemoteTextFieldStatus
import io.github.tomerar.freetvremote.protocol.tls.ClientIdentity
import io.github.tomerar.freetvremote.protocol.tls.SelfSignedCertificate
import io.github.tomerar.freetvremote.protocol.tls.ServerCertificateTrustManager
import io.github.tomerar.freetvremote.protocol.tls.TlsSupport
import io.github.tomerar.freetvremote.protocol.tls.publicKeyPin
import okio.ByteString.Companion.toByteString
import java.io.Closeable
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket

/**
 * An in-process imitation of an Android TV speaking the Remote v2 protocol on
 * ephemeral local ports. Used by the protocol tests and by the app's tests.
 */
public class FakeTv(
    private val pingIntervalMs: Long = 100,
) : Closeable {
    private val identity: ClientIdentity = SelfSignedCertificate.generate("fake-tv")
    private val pairedPins = CopyOnWriteArrayList<String>()
    private val liveSockets = CopyOnWriteArrayList<SSLSocket>()
    private val random = SecureRandom()

    @Volatile
    private var pairingServer: SSLServerSocket? = null

    @Volatile
    private var remoteServer: SSLServerSocket? = null

    @Volatile
    private var silent = false

    @Volatile
    private var remoteAvailable = true

    @Volatile
    private var pairingAvailable = true

    /** When set, the TV answers the pairing secret with BAD_SECRET even if it is right. */
    @Volatile
    public var rejectSecrets: Boolean = false

    /** What the TV reports as its power state right after a client connected; `null` sends no power report at all. */
    @Volatile
    public var powerOnAtConnect: Boolean? = true

    public val pairingPort: Int
    public val remotePort: Int
    public val serverCertificate: X509Certificate get() = identity.certificate

    /** The code the "TV" shows after a client connected to the pairing port, or `null` before that. */
    @Volatile
    public var displayedCode: String? = null
        private set

    public val keys: CopyOnWriteArrayList<Pair<Int, RemoteDirection>> = CopyOnWriteArrayList()
    public val launchedLinks: CopyOnWriteArrayList<String> = CopyOnWriteArrayList()
    public val typedTexts: CopyOnWriteArrayList<String> = CopyOnWriteArrayList()

    /** (imeCounter, fieldCounter) of every text edit received. */
    public val typedCounters: CopyOnWriteArrayList<Pair<Int, Int>> = CopyOnWriteArrayList()
    public val pingResponses: AtomicInteger = AtomicInteger()
    public val remoteConnections: AtomicInteger = AtomicInteger()

    /** Remote connections currently open (accepted, paired and not yet closed). */
    public val activeRemoteConnections: AtomicInteger = AtomicInteger()

    /** The highest number of remote connections that were open at the same time. */
    public val peakRemoteConnections: AtomicInteger = AtomicInteger()
    private val remoteWriters = CopyOnWriteArrayList<(RemoteMessage) -> Unit>()
    public val pairedCount: Int get() = pairedPins.size

    init {
        val pairing = newServer(0)
        val remote = newServer(0)
        pairingServer = pairing
        remoteServer = remote
        pairingPort = pairing.localPort
        remotePort = remote.localPort
        acceptLoop(pairing, ::handlePairing, available = { pairingAvailable })
        acceptLoop(remote, ::handleRemote, available = { remoteAvailable })
    }

    private fun newServer(port: Int): SSLServerSocket {
        val trust =
            object : ServerCertificateTrustManager() {
                override fun verify(leaf: X509Certificate) = Unit
            }
        val context = TlsSupport.sslContext(identity, trust)
        // reuseAddress must be set before binding, or re-opening the same port can fail on busy machines.
        val server = context.serverSocketFactory.createServerSocket() as SSLServerSocket
        server.reuseAddress = true
        server.bind(InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0)
        server.needClientAuth = true
        return server
    }

    private fun acceptLoop(server: SSLServerSocket, handler: (SSLSocket) -> Unit, available: () -> Boolean = { true }) {
        Thread {
            while (!server.isClosed) {
                val client =
                    try {
                        server.accept() as SSLSocket
                    } catch (e: IOException) {
                        return@Thread
                    }
                if (!available()) {
                    runCatching { client.close() } // down: dropped before any TLS handshake
                    continue
                }
                liveSockets.add(client)
                Thread {
                    try {
                        handler(client)
                    } catch (e: IOException) {
                        // Client went away; fine for a fake.
                    } finally {
                        runCatching { client.close() }
                        liveSockets.remove(client)
                    }
                }.apply { isDaemon = true }.start()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun clientCertificate(socket: SSLSocket): X509Certificate {
        socket.startHandshake()
        return socket.session.peerCertificates[0] as X509Certificate
    }

    // --- Pairing ---------------------------------------------------------------------------

    private fun handlePairing(socket: SSLSocket) {
        val client = clientCertificate(socket)
        val input = socket.inputStream
        val out = socket.outputStream

        fun reply(message: PairingMessage) {
            MessageFraming.write(out, PairingMessage.ADAPTER.encode(message))
            out.flush()
        }

        fun next(): PairingMessage = PairingMessage.ADAPTER.decode(MessageFraming.read(input) ?: throw IOException("eof"))
        val ok = PairingMessage.Status.STATUS_OK

        check(next().pairing_request != null)
        reply(PairingMessage(protocol_version = 2, status = ok, pairing_request_ack = PairingRequestAck(server_name = "Fake TV")))
        check(next().pairing_option != null)
        reply(
            PairingMessage(
                protocol_version = 2,
                status = ok,
                pairing_option =
                    PairingOption(
                        input_encodings =
                            listOf(
                                PairingEncoding(PairingEncoding.EncodingType.ENCODING_TYPE_HEXADECIMAL, 6),
                            ),
                        preferred_role = PairingRole.ROLE_TYPE_OUTPUT,
                    ),
            ),
        )
        check(next().pairing_configuration != null)

        // The TV picks two random tail bytes and derives the checksum byte from the keys.
        val tail = ByteArray(2).also(random::nextBytes)
        val expected = secretFor(client, tail)
        val code = byteArrayOf(expected[0]) + tail
        displayedCode = code.joinToString("") { "%02X".format(it) }
        reply(PairingMessage(protocol_version = 2, status = ok, pairing_configuration_ack = PairingConfigurationAck()))

        val secret = next().pairing_secret?.secret?.toByteArray()
        if (!rejectSecrets && secret != null && MessageDigest.isEqual(secret, expected)) {
            pairedPins.add(client.publicKeyPin().toHex())
            reply(
                PairingMessage(
                    protocol_version = 2,
                    status = ok,
                    pairing_secret_ack = PairingSecretAck(secret = expected.toByteString()),
                ),
            )
        } else {
            reply(PairingMessage(protocol_version = 2, status = PairingMessage.Status.STATUS_BAD_SECRET))
        }
    }

    private fun secretFor(client: X509Certificate, tail: ByteArray): ByteArray {
        val clientKey = client.publicKey as RSAPublicKey
        val serverKey = identity.certificate.publicKey as RSAPublicKey
        return MessageDigest
            .getInstance("SHA-256")
            .apply {
                update(PairingSecret.unsignedBytes(clientKey.modulus))
                update(PairingSecret.unsignedBytes(clientKey.publicExponent))
                update(PairingSecret.unsignedBytes(serverKey.modulus))
                update(PairingSecret.unsignedBytes(serverKey.publicExponent))
                update(tail)
            }.digest()
    }

    // --- Remote ----------------------------------------------------------------------------

    private fun handleRemote(socket: SSLSocket) {
        val client = clientCertificate(socket)
        if (client.publicKeyPin().toHex() !in pairedPins) {
            return // A real TV closes the channel for unknown clients.
        }
        remoteConnections.incrementAndGet()
        peakRemoteConnections.accumulateAndGet(activeRemoteConnections.incrementAndGet(), ::maxOf)
        val input = socket.inputStream
        val out = socket.outputStream
        val writeLock = Any()

        fun send(message: RemoteMessage) =
            synchronized(writeLock) {
                MessageFraming.write(out, RemoteMessage.ADAPTER.encode(message))
                out.flush()
            }
        val writer: (RemoteMessage) -> Unit = { send(it) }
        remoteWriters.add(writer)

        val pinger =
            Thread {
                var counter = 0
                try {
                    while (!socket.isClosed) {
                        Thread.sleep(pingIntervalMs)
                        if (!silent) send(RemoteMessage(remote_ping_request = RemotePingRequest(val1 = ++counter)))
                    }
                } catch (e: InterruptedException) {
                    // closing
                } catch (e: IOException) {
                    // closing
                }
            }.apply { isDaemon = true }
        pinger.start()

        try {
            send(
                RemoteMessage(
                    remote_configure =
                        RemoteConfigure(
                            features = 622,
                            device_info = RemoteDeviceInfo(model = "Fake TV", vendor = "Fake Inc"),
                        ),
                ),
            )
            while (true) {
                val bytes = MessageFraming.read(input) ?: return
                if (silent) continue
                val message = RemoteMessage.ADAPTER.decode(bytes)
                when {
                    message.remote_configure != null -> {
                        send(RemoteMessage(remote_set_active = RemoteSetActive(active = 622)))
                    }

                    message.remote_set_active != null -> {
                        announcePower(::send)
                        send(RemoteMessage(remote_set_volume_level = RemoteSetVolumeLevel(volume_max = 100, volume_level = 25)))
                        send(
                            RemoteMessage(
                                remote_ime_key_inject =
                                    RemoteImeKeyInject(
                                        app_info = RemoteAppInfo(counter = 7, app_package = "com.fake.launcher"),
                                        text_field_status = RemoteTextFieldStatus(counter_field = 3),
                                    ),
                            ),
                        )
                    }

                    message.remote_ping_response != null -> {
                        pingResponses.incrementAndGet()
                    }

                    message.remote_key_inject != null -> {
                        keys.add(message.remote_key_inject.key_code to message.remote_key_inject.direction)
                    }

                    message.remote_app_link_launch_request != null -> {
                        launchedLinks.add(message.remote_app_link_launch_request.app_link)
                    }

                    message.remote_ime_batch_edit != null -> {
                        val edit = message.remote_ime_batch_edit
                        typedCounters.add(edit.ime_counter to edit.field_counter)
                        edit.edit_info.forEach { typedTexts.add(it.text_field_status?.value_.orEmpty()) }
                    }
                }
            }
        } finally {
            pinger.interrupt()
            remoteWriters.remove(writer)
            activeRemoteConnections.decrementAndGet()
        }
    }

    private fun announcePower(send: (RemoteMessage) -> Unit) {
        powerOnAtConnect?.let { send(RemoteMessage(remote_start = RemoteStart(started = it))) }
    }

    // --- Test controls ---------------------------------------------------------------------

    /** Clears the displayed code so a test can wait for the next pairing's code. */
    public fun displayedCodeReset() {
        displayedCode = null
    }

    /** Pushes a power report to every open remote connection (a TV announces it when it turns on or goes to standby). */
    public fun sendPower(started: Boolean) {
        val message = RemoteMessage(remote_start = RemoteStart(started = started))
        remoteWriters.forEach { runCatching { it(message) } }
    }

    /** Pushes a volume update to every open remote connection (as a TV does when its volume changes). */
    public fun sendVolume(level: Int) {
        val message = RemoteMessage(remote_set_volume_level = RemoteSetVolumeLevel(volume_max = 100, volume_level = level))
        remoteWriters.forEach { runCatching { it(message) } }
    }

    /** Pushes the foreground app (package and readable label), as a TV does when the app changes. */
    public fun sendForegroundApp(packageName: String, label: String = "") {
        val message =
            RemoteMessage(
                remote_ime_key_inject = RemoteImeKeyInject(app_info = RemoteAppInfo(app_package = packageName, label = label)),
            )
        remoteWriters.forEach { runCatching { it(message) } }
    }

    /** Tells every open remote connection that a text field wants input (the TV's on-screen keyboard came up). */
    public fun sendTextFieldRequest() {
        val message = RemoteMessage(remote_ime_show_request = RemoteImeShowRequest(remote_text_field_status = RemoteTextFieldStatus()))
        remoteWriters.forEach { runCatching { it(message) } }
    }

    /** Forget a previously paired client (as if the user removed it in the TV's settings). */
    public fun unpairAll() {
        pairedPins.clear()
    }

    /** Closes the remote connections abruptly, as a TV going into standby does. */
    public fun dropRemoteConnections() {
        liveSockets.forEach { runCatching { it.close() } }
    }

    /** Stops answering and pinging without closing sockets: the TV "vanished" from the network. */
    public fun goSilent(value: Boolean = true) {
        silent = value
    }

    /**
     * Makes the remote port stop answering (`false`) or answer again (`true`). The listening socket stays bound
     * for the whole life of the fake, so there is no close/re-open race on the port: while "down", connections are
     * accepted by the OS and dropped before any TLS handshake, and open remote connections are closed.
     */
    public fun setRemoteReachable(reachable: Boolean) {
        remoteAvailable = reachable
        if (!reachable) dropRemoteConnections()
    }

    /**
     * Makes the pairing port stop answering (a TV that is off): connections are accepted by the OS and dropped before
     * any TLS handshake. The port stays bound, so no other server can take it over while a test is connecting.
     */
    public fun setPairingReachable(reachable: Boolean) {
        pairingAvailable = reachable
    }

    override fun close() {
        runCatching { pairingServer?.close() }
        runCatching { remoteServer?.close() }
        liveSockets.forEach { runCatching { it.close() } }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
