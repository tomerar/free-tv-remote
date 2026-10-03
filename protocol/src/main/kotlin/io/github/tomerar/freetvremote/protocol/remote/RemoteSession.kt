package io.github.tomerar.freetvremote.protocol.remote

import io.github.tomerar.freetvremote.protocol.MessageFraming
import io.github.tomerar.freetvremote.protocol.blockingIo
import io.github.tomerar.freetvremote.protocol.proto.RemoteDirection
import io.github.tomerar.freetvremote.protocol.proto.RemoteMessage
import io.github.tomerar.freetvremote.protocol.tls.ClientIdentity
import io.github.tomerar.freetvremote.protocol.tls.PinnedTrustManager
import io.github.tomerar.freetvremote.protocol.tls.TlsSupport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket

/**
 * One long-lived control session with a paired TV (port 6466).
 *
 * It owns the socket, answers the TV's pings, reconnects with backoff when the
 * link drops and publishes [connectionState] and [tvState]. It lives in the
 * [scope] it is given (never in UI code) and survives until [stop] is called.
 */
public class RemoteSession(
    private val scope: CoroutineScope,
    private val host: String,
    private val identity: ClientIdentity,
    private val pinnedKey: ByteArray,
    private val config: RemoteSessionConfig = RemoteSessionConfig(),
) {
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    private val _tvState = MutableStateFlow(TvState())
    private val writeLock = Any()

    @Volatile
    private var socket: SSLSocket? = null

    @Volatile
    private var imeCounter = 0

    @Volatile
    private var fieldCounter = 0
    private var job: Job? = null

    public val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    public val tvState: StateFlow<TvState> = _tvState.asStateFlow()

    /** Starts connecting (and staying connected). Idempotent. */
    @Synchronized
    public fun start() {
        if (job?.isActive == true) return
        _connectionState.value = ConnectionState.Connecting
        job = scope.launch { supervise() }
    }

    /** Disconnects and stops reconnecting. The session can be [start]ed again. */
    @Synchronized
    public fun stop() {
        job?.cancel()
        job = null
        closeSocket()
        _connectionState.value = ConnectionState.Idle
    }

    /** Sends a key. Returns `false` when there is no live connection. */
    public suspend fun sendKey(code: Int, direction: RemoteDirection): Boolean =
        send(RemoteMessages.key(code, direction))

    /** A normal tap of [code]. */
    public suspend fun pressKey(code: Int): Boolean = sendKey(code, RemoteDirection.SHORT)

    /** Presses [code] and holds it until [keyUp]; the TV generates the long-press / auto-repeat itself. */
    public suspend fun keyDown(code: Int): Boolean = sendKey(code, RemoteDirection.START_LONG)

    public suspend fun keyUp(code: Int): Boolean = sendKey(code, RemoteDirection.END_LONG)

    /** Opens a deep link / app link on the TV (e.g. `https://www.netflix.com/`). */
    public suspend fun launchApp(link: String): Boolean = send(RemoteMessages.appLink(link))

    /** Types [text] into the TV's focused text field. */
    public suspend fun sendText(text: String): Boolean {
        if (text.isEmpty()) return true
        return send(RemoteMessages.text(text, imeCounter, fieldCounter))
    }

    public suspend fun send(message: RemoteMessage): Boolean {
        val current = socket ?: return false
        if (_connectionState.value != ConnectionState.Connected) return false
        return try {
            blockingIo(current) { write(current, message) }
            true
        } catch (e: IOException) {
            runCatching { current.close() }
            false
        }
    }

    private fun write(target: SSLSocket, message: RemoteMessage) {
        synchronized(writeLock) {
            MessageFraming.write(target.outputStream, RemoteMessage.ADAPTER.encode(message))
            target.outputStream.flush()
        }
    }

    private suspend fun supervise() {
        var attempt = 0
        var earlyCloses = 0
        while (scope.isActive) {
            val outcome = runConnection()
            // stop() cancels us while the socket is torn down: never publish state after that.
            currentCoroutineContext().ensureActive()
            when (outcome) {
                Outcome.PinMismatch -> {
                    return fail(FailureReason.CERTIFICATE_MISMATCH)
                }

                Outcome.HandshakeDone -> {
                    attempt = 0
                    earlyCloses = 0
                }

                Outcome.Early -> {
                    earlyCloses++
                    if (earlyCloses >= config.maxEarlyCloses) return fail(FailureReason.NOT_PAIRED)
                }

                Outcome.Unreachable -> {
                    Unit
                }
            }
            _tvState.update { it.copy(isOn = if (outcome == Outcome.Unreachable) false else it.isOn) }
            val wait = config.backoffMs[attempt.coerceAtMost(config.backoffMs.lastIndex)]
            attempt++
            _connectionState.value = ConnectionState.Reconnecting(attempt, wait)
            delay(wait)
            _connectionState.value = ConnectionState.Connecting
        }
    }

    private fun fail(reason: FailureReason) {
        _connectionState.value = ConnectionState.Failed(reason)
    }

    private enum class Outcome { HandshakeDone, Early, Unreachable, PinMismatch }

    /** Runs one connection to its end and classifies how it ended. */
    private suspend fun runConnection(): Outcome {
        val trust = PinnedTrustManager(pinnedKey)
        val sock = TlsSupport.socketFactory(identity, trust).createSocket() as SSLSocket
        socket = sock
        var handshakeDone = false
        return try {
            blockingIo(sock) {
                sock.soTimeout = config.idleTimeoutMs
                sock.connect(InetSocketAddress(host, config.port), config.connectTimeoutMs)
                sock.startHandshake()
                readLoop(sock) { handshakeDone = true }
            }
            if (handshakeDone) Outcome.HandshakeDone else Outcome.Early
        } catch (e: CancellationException) {
            throw e
        } catch (e: SSLException) {
            when {
                e.hasCause<CertificateException>() && trust.serverCertificate != null -> Outcome.PinMismatch
                handshakeDone -> Outcome.HandshakeDone
                trust.serverCertificate != null -> Outcome.Early
                else -> Outcome.Unreachable
            }
        } catch (e: SocketTimeoutException) {
            if (handshakeDone) Outcome.HandshakeDone else Outcome.Unreachable
        } catch (e: IOException) {
            if (handshakeDone) Outcome.HandshakeDone else Outcome.Unreachable
        } finally {
            socket = null
            runCatching { sock.close() }
        }
    }

    private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean =
        generateSequence(this) { it.cause }.any { it is T }

    private fun readLoop(sock: SSLSocket, onHandshakeDone: () -> Unit) {
        val input = sock.inputStream
        while (true) {
            val bytes = MessageFraming.read(input) ?: return
            val message =
                try {
                    RemoteMessage.ADAPTER.decode(bytes)
                } catch (e: IOException) {
                    continue // Ignore frames we cannot decode rather than dropping the link.
                }
            // Pings first: an unanswered ping makes the TV drop us.
            message.remote_ping_request?.let { write(sock, RemoteMessages.pingResponse(it.val1)) }
            handle(sock, message, onHandshakeDone)
        }
    }

    private fun handle(sock: SSLSocket, message: RemoteMessage, onHandshakeDone: () -> Unit) {
        message.remote_configure?.let { configure ->
            _tvState.update {
                it.copy(
                    deviceModel = configure.device_info?.model?.ifEmpty { null } ?: it.deviceModel,
                    deviceVendor = configure.device_info?.vendor?.ifEmpty { null } ?: it.deviceVendor,
                )
            }
            write(sock, RemoteMessages.configure(config))
        }
        if (message.remote_set_active != null) {
            write(sock, RemoteMessages.setActive())
            onHandshakeDone()
            _connectionState.value = ConnectionState.Connected
        }
        message.remote_start?.let { start -> _tvState.update { it.copy(isOn = start.started) } }
        message.remote_set_volume_level?.let { volume ->
            _tvState.update {
                it.copy(
                    volumeLevel = volume.volume_level,
                    volumeMax = volume.volume_max,
                    isMuted = volume.volume_muted,
                )
            }
        }
        message.remote_ime_key_inject?.let { ime ->
            imeCounter = ime.app_info?.counter ?: imeCounter
            fieldCounter = ime.text_field_status?.counter_field ?: fieldCounter
            val app = ime.app_info?.app_package.orEmpty()
            if (app.isNotEmpty()) _tvState.update { it.copy(currentApp = app) }
        }
    }

    private fun closeSocket() {
        runCatching { socket?.close() }
    }
}
