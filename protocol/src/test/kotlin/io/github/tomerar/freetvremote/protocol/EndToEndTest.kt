package io.github.tomerar.freetvremote.protocol

import io.github.tomerar.freetvremote.protocol.pairing.PairingClient
import io.github.tomerar.freetvremote.protocol.pairing.PairingException
import io.github.tomerar.freetvremote.protocol.proto.RemoteDirection
import io.github.tomerar.freetvremote.protocol.remote.ConnectionState
import io.github.tomerar.freetvremote.protocol.remote.FailureReason
import io.github.tomerar.freetvremote.protocol.remote.KeyCodes
import io.github.tomerar.freetvremote.protocol.remote.RemoteSession
import io.github.tomerar.freetvremote.protocol.remote.RemoteSessionConfig
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
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/** Pairing and key sending against an in-process fake TV, over real TLS sockets. */
class EndToEndTest {
    private lateinit var tv: FakeTv
    private lateinit var identity: ClientIdentity
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fastConfig =
        RemoteSessionConfig(
            port = 0,
            connectTimeoutMs = 1_000,
            idleTimeoutMs = 1_500,
            backoffMs = listOf(50, 100),
        )

    @Before
    fun setUp() {
        tv = FakeTv(pingIntervalMs = 100)
        identity = SelfSignedCertificate.generate("test-client")
    }

    @After
    fun tearDown() {
        scope.cancel()
        tv.close()
    }

    private fun config() = fastConfig.copy(port = tv.remotePort)

    private suspend fun pair(who: ClientIdentity = identity): ByteArray {
        PairingClient(who, "127.0.0.1", tv.pairingPort).begin().use { session ->
            val code = awaitCode()
            return session.submitCode(code).publicKeyPin()
        }
    }

    private suspend fun awaitCode(): String =
        withTimeout(15_000) {
            while (tv.displayedCode == null) delay(10)
            tv.displayedCode!!
        }

    private suspend fun RemoteSession.await(timeoutMs: Long = 15_000, predicate: (ConnectionState) -> Boolean): ConnectionState =
        withTimeout(timeoutMs) { connectionState.first(predicate) }

    private suspend fun until(timeoutMs: Long = 15_000, condition: () -> Boolean) {
        withTimeout(timeoutMs) { while (!condition()) delay(10) }
    }

    private fun newSession(pin: ByteArray) = RemoteSession(scope, "127.0.0.1", identity, pin, config())

    @Test
    fun `pairing with the displayed code succeeds and pins the TV`() =
        runBlocking {
            val pin = pair()
            assertEquals(1, tv.pairedCount)
            assertArrayEquals(tv.serverCertificate.publicKeyPin(), pin)
        }

    @Test
    fun `a mistyped code is rejected locally and the session can retry`() =
        runBlocking {
            PairingClient(identity, "127.0.0.1", tv.pairingPort).begin().use { session ->
                val code = awaitCode()
                val wrong = code.take(5) + (if (code.last() == '0') '1' else '0')
                try {
                    session.submitCode(wrong)
                    fail("expected InvalidCode")
                } catch (_: PairingException.InvalidCode) {
                    // expected
                }
                assertEquals(0, tv.pairedCount)
                session.submitCode(code) // the same session still works
                assertEquals(1, tv.pairedCount)
            }
        }

    @Test
    fun `the TV can reject the secret`() =
        runBlocking {
            tv.rejectSecrets = true
            PairingClient(identity, "127.0.0.1", tv.pairingPort).begin().use { session ->
                try {
                    session.submitCode(awaitCode())
                    fail("expected Rejected")
                } catch (e: PairingException.Rejected) {
                    assertEquals(402, e.status)
                }
            }
            assertEquals(0, tv.pairedCount)
        }

    @Test
    fun `pairing a TV that is off reports a connection failure`() =
        runBlocking {
            val port = tv.pairingPort
            tv.close()
            try {
                PairingClient(identity, "127.0.0.1", port, connectTimeoutMs = 500).begin()
                fail("expected ConnectionFailed")
            } catch (_: PairingException.ConnectionFailed) {
                // expected
            }
        }

    @Test
    fun `session connects reads TV state and sends keys, links and text`() =
        runBlocking {
            val session = newSession(pair())
            session.start()
            session.await { it == ConnectionState.Connected }

            until { session.tvState.value.volumeLevel != null && session.tvState.value.currentApp != null }
            val state = session.tvState.value
            assertEquals(true, state.isOn)
            assertEquals(25, state.volumeLevel)
            assertEquals(100, state.volumeMax)
            assertEquals("com.fake.launcher", state.currentApp)
            assertEquals("Fake TV", state.deviceModel)

            assertTrue(session.pressKey(KeyCodes.DPAD_UP))
            assertTrue(session.keyDown(KeyCodes.VOLUME_UP))
            assertTrue(session.keyUp(KeyCodes.VOLUME_UP))
            assertTrue(session.launchApp("https://www.netflix.com/"))
            assertTrue(session.sendText("hello"))

            until { tv.keys.size == 3 && tv.launchedLinks.isNotEmpty() && tv.typedTexts.isNotEmpty() }
            assertEquals(
                listOf(
                    KeyCodes.DPAD_UP to RemoteDirection.SHORT,
                    KeyCodes.VOLUME_UP to RemoteDirection.START_LONG,
                    KeyCodes.VOLUME_UP to RemoteDirection.END_LONG,
                ),
                tv.keys.toList(),
            )
            assertEquals(listOf("https://www.netflix.com/"), tv.launchedLinks.toList())
            assertEquals(listOf("hello"), tv.typedTexts.toList())
            assertEquals(listOf(7 to 3), tv.typedCounters.toList()) // counters learned from the TV
            session.stop()
        }

    @Test
    fun `TV pings are always answered`() =
        runBlocking {
            val session = newSession(pair())
            session.start()
            session.await { it == ConnectionState.Connected }
            until { tv.pingResponses.get() >= 3 }
            session.stop()
        }

    @Test
    fun `sending while disconnected returns false instead of throwing`() =
        runBlocking {
            val session = newSession(pair())
            assertEquals(false, session.pressKey(KeyCodes.HOME))
        }

    @Test
    fun `session reconnects after the TV drops the connection`() =
        runBlocking {
            val session = newSession(pair())
            session.start()
            session.await { it == ConnectionState.Connected }
            tv.dropRemoteConnections()
            session.await { it is ConnectionState.Reconnecting }
            session.await { it == ConnectionState.Connected }
            until { tv.remoteConnections.get() >= 2 }
            assertTrue(session.pressKey(KeyCodes.HOME))
            until { tv.keys.isNotEmpty() }
            session.stop()
        }

    @Test
    fun `silent TV is detected and the session starts reconnecting`() =
        runBlocking {
            val session = newSession(pair())
            session.start()
            session.await { it == ConnectionState.Connected }
            tv.goSilent()
            session.await(timeoutMs = 10_000) { it is ConnectionState.Reconnecting }
            session.stop()
        }

    @Test
    fun `unreachable TV is retried and picked up when it returns`() =
        runBlocking {
            val pin = pair()
            tv.setRemoteReachable(false)
            val session = newSession(pin)
            session.start()
            val reconnecting = session.await { it is ConnectionState.Reconnecting } as ConnectionState.Reconnecting
            assertTrue(reconnecting.attempt >= 1)
            assertEquals(false, session.tvState.value.isOn)
            tv.setRemoteReachable(true)
            session.await { it == ConnectionState.Connected }
            session.stop()
        }

    @Test
    fun `a different TV certificate is refused`() =
        runBlocking {
            pair()
            val session = newSession(SelfSignedCertificate.generate("other").certificate.publicKeyPin())
            session.start()
            val state = session.await { it is ConnectionState.Failed }
            assertEquals(ConnectionState.Failed(FailureReason.CERTIFICATE_MISMATCH), state)
            assertEquals(0, tv.keys.size)
        }

    @Test
    fun `a phone the TV no longer knows ends up as NOT_PAIRED`() =
        runBlocking {
            val pin = pair()
            tv.unpairAll()
            val session = newSession(pin)
            session.start()
            val state = session.await { it is ConnectionState.Failed }
            assertEquals(ConnectionState.Failed(FailureReason.NOT_PAIRED), state)
        }

    @Test
    fun `stop and start again works`() =
        runBlocking {
            val session = newSession(pair())
            session.start()
            session.await { it == ConnectionState.Connected }
            session.stop()
            assertEquals(ConnectionState.Idle, session.connectionState.value)
            session.start()
            session.await { it == ConnectionState.Connected }
            session.stop()
        }
}
