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

/** Deterministic seams for tests that need to control the ordering of connection-attempt cleanup. */
internal interface SessionTestHooks {
    /** Called on the IO thread of attempt [attempt] right before it releases its resources. */
    fun beforeCleanup(attempt: Int) {}

    /** Called after attempt [attempt] released its resources. */
    fun afterCleanup(attempt: Int) {}

    /** Called on the reader thread of attempt [attempt] before an incoming [message] is applied. */
    fun beforeHandle(attempt: Int, message: RemoteMessage) {}
}

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

    /**
     * Ownership model. Every [start] / [stop] opens a new *generation*. A connection attempt is bound to the
     * generation it was launched for and may only touch session state (the open [link], [connectionState],
     * [tvState], IME counters) while that generation is still current. All of that is decided under [lock],
     * so an attempt that is still unwinding after [stop] (cancellation is only a request) can neither close
     * or clear a newer attempt's socket nor overwrite state published for it.
     */
    private val lock = Any()
    private var generation = 0L
    private var job: Job? = null
    private var link: Link? = null

    /** One TLS connection. Only [ready] links (handshake completed) are used for sending. */
    private class Link(
        val generation: Long,
        val socket: SSLSocket,
    ) {
        @Volatile
        var ready = false
    }

    @Volatile
    private var imeCounter = 0

    @Volatile
    private var fieldCounter = 0
    private val attemptSeq =
        java.util.concurrent.atomic
            .AtomicInteger()

    @Volatile
    internal var testHooks: SessionTestHooks? = null

    public val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    public val tvState: StateFlow<TvState> = _tvState.asStateFlow()

    /** Starts connecting (and staying connected). Idempotent while a session is running. */
    public fun start() {
        synchronized(lock) {
            if (job?.isActive == true) return
            val gen = ++generation
            _connectionState.value = ConnectionState.Connecting
            job = scope.launch { supervise(gen) }
        }
    }

    /** Disconnects and stops reconnecting. The session can be [start]ed again at once. */
    public fun stop() {
        synchronized(lock) {
            generation++ // everything still running belongs to an older generation from here on
            job?.cancel()
            job = null
            link?.socket?.closeQuietly()
            link = null
            _connectionState.value = ConnectionState.Idle
        }
    }

    private inline fun ifCurrent(gen: Long, block: () -> Unit) {
        synchronized(lock) { if (gen == generation) block() }
    }

    private fun isCurrent(gen: Long): Boolean = synchronized(lock) { gen == generation }

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
        val current = synchronized(lock) { link?.takeIf { it.ready } } ?: return false
        return try {
            blockingIo(current.socket) { write(current.socket, message) }
            true
        } catch (e: IOException) {
            current.socket.closeQuietly()
            false
        }
    }

    private fun write(target: SSLSocket, message: RemoteMessage) {
        synchronized(writeLock) {
            MessageFraming.write(target.outputStream, RemoteMessage.ADAPTER.encode(message))
            target.outputStream.flush()
        }
    }

    private suspend fun supervise(gen: Long) {
        var attempt = 0
        var earlyCloses = 0
        while (scope.isActive && isCurrent(gen)) {
            val outcome = runConnection(gen)
            // stop() cancels us while the socket is torn down: never publish state after that.
            currentCoroutineContext().ensureActive()
            if (!isCurrent(gen)) return
            when (outcome) {
                Outcome.PinMismatch -> {
                    return fail(gen, FailureReason.CERTIFICATE_MISMATCH)
                }

                Outcome.HandshakeDone -> {
                    attempt = 0
                    earlyCloses = 0
                }

                Outcome.Early -> {
                    earlyCloses++
                    if (earlyCloses >= config.maxEarlyCloses) return fail(gen, FailureReason.NOT_PAIRED)
                }

                Outcome.Unreachable -> {
                    Unit
                }
            }
            val wait = config.backoffMs[attempt.coerceAtMost(config.backoffMs.lastIndex)]
            attempt++
            ifCurrent(gen) {
                if (outcome == Outcome.Unreachable) _tvState.update { it.copy(isOn = false) }
                _connectionState.value = ConnectionState.Reconnecting(attempt, wait)
            }
            delay(wait)
            ifCurrent(gen) { _connectionState.value = ConnectionState.Connecting }
        }
    }

    private fun fail(gen: Long, reason: FailureReason) {
        ifCurrent(gen) { _connectionState.value = ConnectionState.Failed(reason) }
    }

    private enum class Outcome { HandshakeDone, Early, Unreachable, PinMismatch }

    /** Runs one connection to its end and classifies how it ended. */
    private suspend fun runConnection(gen: Long): Outcome {
        val trust = PinnedTrustManager(pinnedKey)
        val sock = TlsSupport.socketFactory(identity, trust).createSocket() as SSLSocket
        val attempt = attemptSeq.incrementAndGet()
        val mine = Link(gen, sock)
        val registered =
            synchronized(lock) {
                if (gen == generation) link = mine
                gen == generation
            }
        if (!registered) {
            // stop() ran between launching this attempt and opening its socket.
            sock.closeQuietly()
            throw CancellationException("connection attempt superseded")
        }
        var handshakeDone = false
        return try {
            blockingIo(sock) {
                sock.soTimeout = config.idleTimeoutMs
                sock.connect(InetSocketAddress(host, config.port), config.connectTimeoutMs)
                sock.startHandshake()
                readLoop(mine, attempt) { handshakeDone = true }
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
            testHooks?.beforeCleanup(attempt)
            // Identity-checked: a newer attempt's link is never cleared by this one.
            synchronized(lock) { if (link === mine) link = null }
            sock.closeQuietly()
            testHooks?.afterCleanup(attempt)
        }
    }

    private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean =
        generateSequence(this) { it.cause }.any { it is T }

    private fun readLoop(mine: Link, attempt: Int, onHandshakeDone: () -> Unit) {
        val input = mine.socket.inputStream
        while (true) {
            val bytes = MessageFraming.read(input) ?: return
            val message =
                try {
                    RemoteMessage.ADAPTER.decode(bytes)
                } catch (e: IOException) {
                    continue // Ignore frames we cannot decode rather than dropping the link.
                }
            // Pings first: an unanswered ping makes the TV drop us.
            message.remote_ping_request?.let { write(mine.socket, RemoteMessages.pingResponse(it.val1)) }
            testHooks?.beforeHandle(attempt, message)
            handle(mine, message, onHandshakeDone)
        }
    }

    private fun handle(mine: Link, message: RemoteMessage, onHandshakeDone: () -> Unit) {
        val gen = mine.generation
        message.remote_configure?.let { configure ->
            ifCurrent(gen) {
                _tvState.update {
                    it.copy(
                        deviceModel = configure.device_info?.model?.ifEmpty { null } ?: it.deviceModel,
                        deviceVendor = configure.device_info?.vendor?.ifEmpty { null } ?: it.deviceVendor,
                    )
                }
            }
            write(mine.socket, RemoteMessages.configure(config))
        }
        if (message.remote_set_active != null) {
            write(mine.socket, RemoteMessages.setActive())
            onHandshakeDone()
            ifCurrent(gen) {
                mine.ready = true
                _connectionState.value = ConnectionState.Connected
            }
        }
        message.remote_start?.let { start -> ifCurrent(gen) { _tvState.update { it.copy(isOn = start.started) } } }
        message.remote_set_volume_level?.let { volume ->
            ifCurrent(gen) {
                _tvState.update {
                    it.copy(
                        volumeLevel = volume.volume_level,
                        volumeMax = volume.volume_max,
                        isMuted = volume.volume_muted,
                    )
                }
            }
        }
        message.remote_ime_key_inject?.let { ime ->
            ifCurrent(gen) {
                imeCounter = ime.app_info?.counter ?: imeCounter
                fieldCounter = ime.text_field_status?.counter_field ?: fieldCounter
                val app = ime.app_info?.app_package.orEmpty()
                if (app.isNotEmpty()) _tvState.update { it.copy(currentApp = app) }
            }
        }
    }

    private fun SSLSocket.closeQuietly() {
        runCatching { close() }
    }
}
