package io.github.tomerar.freetvremote.protocol.pairing

import io.github.tomerar.freetvremote.protocol.MessageFraming
import io.github.tomerar.freetvremote.protocol.blockingIo
import io.github.tomerar.freetvremote.protocol.proto.PairingConfiguration
import io.github.tomerar.freetvremote.protocol.proto.PairingEncoding
import io.github.tomerar.freetvremote.protocol.proto.PairingMessage
import io.github.tomerar.freetvremote.protocol.proto.PairingOption
import io.github.tomerar.freetvremote.protocol.proto.PairingRequest
import io.github.tomerar.freetvremote.protocol.proto.PairingRole
import io.github.tomerar.freetvremote.protocol.tls.ClientIdentity
import io.github.tomerar.freetvremote.protocol.tls.RecordingTrustManager
import io.github.tomerar.freetvremote.protocol.tls.TlsSupport
import okio.ByteString.Companion.toByteString
import java.io.Closeable
import java.io.IOException
import java.net.InetSocketAddress
import java.net.SocketTimeoutException
import java.security.cert.X509Certificate
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import io.github.tomerar.freetvremote.protocol.proto.PairingSecret as PairingSecretMessage

/** Failures of the pairing handshake. */
public sealed class PairingException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** The code is malformed or its checksum does not match: the user mistyped it. Safe to retry. */
    public class InvalidCode : PairingException("Invalid pairing code")

    /** The TV refused the pairing (wrong secret, timeout on the TV, busy...). Start over. */
    public class Rejected(
        public val status: Int,
    ) : PairingException("TV rejected pairing (status $status)")

    /** The TV could not be reached or the connection dropped. */
    public class ConnectionFailed(
        cause: Throwable,
    ) : PairingException("Connection failed: ${cause.message}", cause)

    /** The TV sent something unexpected. */
    public class ProtocolError(
        message: String,
    ) : PairingException(message)
}

public class PairingClient(
    private val identity: ClientIdentity,
    private val host: String,
    private val port: Int = DEFAULT_PORT,
    private val clientName: String = DEFAULT_CLIENT_NAME,
    private val connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
    private val readTimeoutMs: Int = READ_TIMEOUT_MS,
) {
    /**
     * Connects and runs the handshake up to the point where the TV displays the code.
     * The returned session must be closed.
     */
    public suspend fun begin(): PairingSession {
        val trust = RecordingTrustManager()
        val socket =
            try {
                val factory = TlsSupport.socketFactory(identity, trust)
                factory.createSocket() as SSLSocket
            } catch (e: IOException) {
                throw PairingException.ConnectionFailed(e)
            }
        try {
            blockingIo(socket) {
                socket.soTimeout = readTimeoutMs
                socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
                socket.startHandshake()
                handshake(socket)
            }
            val server =
                trust.serverCertificate
                    ?: throw PairingException.ProtocolError("TV presented no certificate")
            return PairingSession(socket, identity, server)
        } catch (e: PairingException) {
            socket.closeQuietly()
            throw e
        } catch (e: IOException) {
            socket.closeQuietly()
            throw PairingException.ConnectionFailed(e)
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            socket.closeQuietly()
            throw e
        }
    }

    private fun handshake(socket: SSLSocket) {
        val out = socket.outputStream
        val input = socket.inputStream

        send(
            out,
            PairingMessage(
                protocol_version = PROTOCOL_VERSION,
                status = PairingMessage.Status.STATUS_OK,
                pairing_request = PairingRequest(service_name = SERVICE_NAME, client_name = clientName),
            ),
        )
        expect(input) { it.pairing_request_ack != null }

        val encoding =
            PairingEncoding(
                type = PairingEncoding.EncodingType.ENCODING_TYPE_HEXADECIMAL,
                symbol_length = PairingSecret.CODE_LENGTH,
            )
        send(
            out,
            PairingMessage(
                protocol_version = PROTOCOL_VERSION,
                status = PairingMessage.Status.STATUS_OK,
                pairing_option = PairingOption(input_encodings = listOf(encoding), preferred_role = PairingRole.ROLE_TYPE_INPUT),
            ),
        )
        expect(input) { it.pairing_option != null }

        send(
            out,
            PairingMessage(
                protocol_version = PROTOCOL_VERSION,
                status = PairingMessage.Status.STATUS_OK,
                pairing_configuration = PairingConfiguration(encoding = encoding, client_role = PairingRole.ROLE_TYPE_INPUT),
            ),
        )
        expect(input) { it.pairing_configuration_ack != null }
    }

    public companion object {
        public const val DEFAULT_PORT: Int = 6467
        public const val DEFAULT_CLIENT_NAME: String = "Free TV Remote"
        internal const val SERVICE_NAME: String = "atvremote"
        internal const val PROTOCOL_VERSION: Int = 2
        private const val CONNECT_TIMEOUT_MS = 5_000
        private const val READ_TIMEOUT_MS = 30_000
    }
}

/** An established pairing channel waiting for the code shown on the TV. */
public class PairingSession internal constructor(
    private val socket: SSLSocket,
    private val identity: ClientIdentity,
    /** The TV's certificate; store [io.github.tomerar.freetvremote.protocol.tls.publicKeyPin] after success. */
    public val serverCertificate: X509Certificate,
) : Closeable {
    /**
     * Verifies [code] locally (checksum byte), sends the secret and waits for the TV's verdict.
     * @throws PairingException.InvalidCode if the code is wrong; the session stays usable.
     */
    public suspend fun submitCode(code: String): X509Certificate {
        val secret =
            PairingSecret.compute(identity.certificate, serverCertificate, code)
                ?: throw PairingException.InvalidCode()
        try {
            blockingIo(socket) {
                send(
                    socket.outputStream,
                    PairingMessage(
                        protocol_version = PairingClient.PROTOCOL_VERSION,
                        status = PairingMessage.Status.STATUS_OK,
                        pairing_secret = PairingSecretMessage(secret = secret.toByteString()),
                    ),
                )
                expect(socket.inputStream) { it.pairing_secret_ack != null }
            }
        } catch (e: IOException) {
            throw PairingException.ConnectionFailed(e)
        }
        return serverCertificate
    }

    override fun close(): Unit = socket.closeQuietly()
}

private fun send(out: java.io.OutputStream, message: PairingMessage) {
    MessageFraming.write(out, PairingMessage.ADAPTER.encode(message))
    out.flush()
}

private fun readMessage(input: java.io.InputStream): PairingMessage {
    val bytes =
        try {
            MessageFraming.read(input)
        } catch (e: SocketTimeoutException) {
            throw PairingException.ConnectionFailed(e)
        } catch (e: SSLException) {
            throw PairingException.ConnectionFailed(e)
        } ?: throw PairingException.ConnectionFailed(IOException("TV closed the connection"))
    return try {
        PairingMessage.ADAPTER.decode(bytes)
    } catch (e: IOException) {
        throw PairingException.ProtocolError("Undecodable message: ${e.message}")
    }
}

private fun expect(input: java.io.InputStream, predicate: (PairingMessage) -> Boolean): PairingMessage {
    val message = readMessage(input)
    if (message.status != PairingMessage.Status.STATUS_OK) {
        throw PairingException.Rejected(message.status.value)
    }
    if (!predicate(message)) throw PairingException.ProtocolError("Unexpected message from TV")
    return message
}

private fun java.io.Closeable.closeQuietly() {
    try {
        close()
    } catch (_: IOException) {
        // Nothing useful to do.
    }
}
