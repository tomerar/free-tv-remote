package io.github.tomerar.freetvremote.protocol.remote

import io.github.tomerar.freetvremote.protocol.pairing.PairingClient
import io.github.tomerar.freetvremote.protocol.proto.RemoteDirection
import io.github.tomerar.freetvremote.protocol.proto.RemoteMessage
import io.github.tomerar.freetvremote.protocol.testing.FakeTv
import io.github.tomerar.freetvremote.protocol.tls.ClientIdentity
import io.github.tomerar.freetvremote.protocol.tls.SelfSignedCertificate
import io.github.tomerar.freetvremote.protocol.tls.publicKeyPin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Ordering of connection-attempt lifecycle events. The "old attempt is still unwinding" cases are made
 * deterministic with [SessionTestHooks]: a latch holds the old attempt's cleanup (or message handling)
 * until the new attempt is fully running, so no test depends on timing luck.
 */
class RemoteSessionLifecycleTest {
    private lateinit var tv: FakeTv
    private lateinit var identity: ClientIdentity
    private lateinit var pin: ByteArray
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Before
    fun setUp() {
        tv = FakeTv(pingIntervalMs = 100)
        identity = SelfSignedCertificate.generate("lifecycle-client")
        pin = pair()
    }

    @After
    fun tearDown() {
        scope.cancel()
        tv.close()
    }

    private fun pair(): ByteArray =
        runBlocking {
            PairingClient(identity, "127.0.0.1", tv.pairingPort).begin().use { session ->
                withTimeout(WAIT_MS) { while (tv.displayedCode == null) delay(10) }
                session.submitCode(tv.displayedCode!!).publicKeyPin()
            }
        }

    private fun newSession(port: Int = tv.remotePort, host: String = "127.0.0.1") =
        RemoteSession(
            scope,
            host,
            identity,
            pin,
            RemoteSessionConfig(port = port, connectTimeoutMs = 2_000, idleTimeoutMs = 5_000, backoffMs = listOf(50, 100)),
        )

    private suspend fun RemoteSession.awaitConnected() {
        withTimeout(WAIT_MS) { connectionState.first { it == ConnectionState.Connected } }
    }

    private suspend fun until(condition: () -> Boolean) {
        withTimeout(WAIT_MS) { while (!condition()) delay(10) }
    }

    /** Holds selected callbacks of one attempt until [release] is called. */
    private class Gate(
        private val blockCleanupOf: Int? = null,
        private val blockMessageOf: Pair<Int, (RemoteMessage) -> Boolean>? = null,
    ) : SessionTestHooks {
        private val latch = CountDownLatch(1)
        val blocked = CountDownLatch(1)
        val cleanedUp = CountDownLatch(1)

        fun release() = latch.countDown()

        override fun beforeCleanup(attempt: Int) {
            if (attempt == blockCleanupOf) {
                blocked.countDown()
                check(latch.await(WAIT_MS, TimeUnit.MILLISECONDS)) { "gate never released" }
            }
        }

        override fun afterCleanup(attempt: Int) {
            if (attempt == blockCleanupOf) cleanedUp.countDown()
        }

        override fun beforeHandle(attempt: Int, message: RemoteMessage) {
            val target = blockMessageOf ?: return
            if (attempt == target.first && target.second(message)) {
                blocked.countDown()
                check(latch.await(WAIT_MS, TimeUnit.MILLISECONDS)) { "gate never released" }
            }
        }
    }

    @Test
    fun `immediate stop then start connects again and sends`() =
        runBlocking {
            val session = newSession()
            session.start()
            session.awaitConnected()
            session.stop()
            session.start()
            session.awaitConnected()
            assertTrue(session.pressKey(KeyCodes.HOME))
            until { tv.keys.isNotEmpty() }
            session.stop()
        }

    @Test
    fun `old attempt cleanup finishing after the new attempt started does not break the new connection`() =
        runBlocking {
            val session = newSession()
            val gate = Gate(blockCleanupOf = 1)
            session.testHooks = gate
            session.start()
            session.awaitConnected()

            session.stop() // attempt 1 is cancelled but its cleanup is held back by the gate
            assertTrue("attempt 1 should be unwinding", gate.blocked.await(WAIT_MS, TimeUnit.MILLISECONDS))
            session.start() // attempt 2
            session.awaitConnected()
            assertTrue(session.pressKey(KeyCodes.DPAD_UP))
            until { tv.keys.size == 1 }

            gate.release() // now the old attempt finishes cleaning up
            assertTrue(gate.cleanedUp.await(WAIT_MS, TimeUnit.MILLISECONDS))

            assertEquals(ConnectionState.Connected, session.connectionState.value)
            assertTrue("new connection must still be usable after stale cleanup", session.pressKey(KeyCodes.DPAD_DOWN))
            until { tv.keys.size == 2 }
            assertEquals(1, tv.activeRemoteConnections.get())
            session.stop()
        }

    @Test
    fun `a late message of a stopped attempt is not applied to the session state`() =
        runBlocking {
            val session = newSession()
            val gate = Gate(blockMessageOf = 1 to { it.remote_set_volume_level?.volume_level == STALE_VOLUME })
            session.testHooks = gate
            session.start()
            session.awaitConnected()
            until { session.tvState.value.volumeLevel == 25 }

            tv.sendVolume(STALE_VOLUME) // attempt 1's reader picks it up and is held before applying it
            assertTrue(gate.blocked.await(WAIT_MS, TimeUnit.MILLISECONDS))
            session.stop()
            val before = session.tvState.value
            gate.release()
            delay(200) // give the stale reader every chance to (wrongly) publish

            assertEquals("stale attempt must not publish TV state after stop()", before, session.tvState.value)
            assertEquals(ConnectionState.Idle, session.connectionState.value)
        }

    @Test
    fun `repeated manual reconnects leave exactly one live connection that works`() =
        runBlocking {
            val session = newSession()
            session.start()
            session.awaitConnected()
            repeat(RECONNECTS) {
                session.stop()
                session.start()
            }
            session.awaitConnected()
            until { tv.activeRemoteConnections.get() == 1 }
            assertTrue(session.pressKey(KeyCodes.MENU))
            until { tv.keys.isNotEmpty() }
            session.stop()
            until { tv.activeRemoteConnections.get() == 0 }
        }

    @Test
    fun `start is idempotent while a session is running`() =
        runBlocking {
            val session = newSession()
            repeat(3) { session.start() }
            session.awaitConnected()
            repeat(3) { session.start() }
            delay(300)
            assertEquals(1, tv.remoteConnections.get())
            assertEquals(1, tv.activeRemoteConnections.get())
            session.stop()
        }

    @Test
    fun `stopping while the connection is still being established closes the socket and allows a clean restart`() =
        runBlocking {
            // A server that accepts TCP but never answers the TLS handshake keeps the attempt in "connecting".
            ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { stalled ->
                val session = newSession(port = stalled.localPort)
                session.start()
                val accepted = withTimeout(WAIT_MS) { runInterruptibleAccept(stalled) }

                session.stop()
                assertEquals(ConnectionState.Idle, session.connectionState.value)
                // The client socket must be closed promptly, not leaked: drain the ClientHello it already
                // sent, then expect EOF or a reset. A read timeout would mean the socket is still open.
                accepted.soTimeout = 5_000
                val input = accepted.getInputStream()
                val ended =
                    try {
                        while (input.read() != -1) Unit
                        true
                    } catch (e: java.net.SocketTimeoutException) {
                        false
                    } catch (e: java.io.IOException) {
                        true // connection reset by the closing client
                    }
                assertTrue("stopped attempt leaked its socket", ended)
                accepted.close()
            }
            // And a fresh session on the real TV works right afterwards.
            val session = newSession()
            session.start()
            session.awaitConnected()
            session.stop()
        }

    private suspend fun runInterruptibleAccept(server: ServerSocket) =
        kotlinx.coroutines.runInterruptible(Dispatchers.IO) { server.accept() }

    @Test
    fun `send after stop reports failure instead of using a dead socket`() =
        runBlocking {
            val session = newSession()
            session.start()
            session.awaitConnected()
            session.stop()
            assertEquals(false, session.sendKey(KeyCodes.HOME, RemoteDirection.SHORT))
        }

    private companion object {
        const val WAIT_MS = 15_000L
        const val STALE_VOLUME = 77
        const val RECONNECTS = 5
    }
}
