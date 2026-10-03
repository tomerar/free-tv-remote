package io.github.tomerar.freetvremote.protocol.remote

import io.github.tomerar.freetvremote.protocol.blockingIo
import io.github.tomerar.freetvremote.protocol.closeOffThread
import io.github.tomerar.freetvremote.protocol.tls.ClientIdentity
import io.github.tomerar.freetvremote.protocol.tls.PinnedTrustManager
import io.github.tomerar.freetvremote.protocol.tls.TlsSupport
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.InetSocketAddress
import javax.net.ssl.SSLSocket

/**
 * Confirms that the device at [host]:[port] is the TV whose key was pinned at pairing time ([pin]).
 *
 * It only completes the TLS handshake with the pinned-key check and closes the connection again: nothing is sent,
 * so it cannot press a key or change anything on the TV. Returns `false` for any other device, for an unreachable
 * address and for a handshake that fails or takes too long; it never throws except for coroutine cancellation.
 */
public suspend fun confirmPinnedTv(
    identity: ClientIdentity,
    host: String,
    port: Int,
    pin: ByteArray,
    connectTimeoutMs: Int = 2_000,
    handshakeTimeoutMs: Int = 3_000,
): Boolean {
    val socket =
        try {
            TlsSupport.socketFactory(identity, PinnedTrustManager(pin)).createSocket() as SSLSocket
        } catch (e: IOException) {
            return false
        }
    return try {
        blockingIo(socket) {
            socket.soTimeout = handshakeTimeoutMs
            socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
            socket.startHandshake()
        }
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        false
    } finally {
        socket.closeOffThread()
    }
}
