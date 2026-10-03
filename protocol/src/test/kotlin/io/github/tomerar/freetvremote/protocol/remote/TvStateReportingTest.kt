package io.github.tomerar.freetvremote.protocol.remote

import io.github.tomerar.freetvremote.protocol.pairing.PairingClient
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

/** What the status card shows comes from these fields: app label, text-field request and connection time. */
class TvStateReportingTest {
    private lateinit var tv: FakeTv
    private lateinit var identity: ClientIdentity
    private lateinit var pin: ByteArray
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notes = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        tv = FakeTv(pingIntervalMs = 100)
        identity = SelfSignedCertificate.generate("state-client")
        pin =
            runBlocking {
                PairingClient(identity, "127.0.0.1", tv.pairingPort).begin().use { session ->
                    withTimeout(WAIT_MS) { while (tv.displayedCode == null) delay(10) }
                    session.submitCode(tv.displayedCode!!).publicKeyPin()
                }
            }
    }

    @After
    fun tearDown() {
        scope.cancel()
        tv.close()
    }

    private fun session(clock: () -> Long = System::currentTimeMillis) =
        RemoteSession(
            scope,
            "127.0.0.1",
            identity,
            pin,
            RemoteSessionConfig(port = tv.remotePort, connectTimeoutMs = 2_000, idleTimeoutMs = 5_000, backoffMs = listOf(50, 100)),
            log = { notes += it },
            clock = clock,
        )

    private suspend fun RemoteSession.awaitConnected() {
        withTimeout(WAIT_MS) { connectionState.first { it == ConnectionState.Connected } }
    }

    /**
     * The fake TV announces its launcher right after the handshake. Waiting for that message first makes the
     * tests deterministic: otherwise it could arrive after (and replace) what a test just sent.
     */
    private suspend fun RemoteSession.awaitConnectedAndSettled() {
        awaitConnected()
        awaitState { it.currentApp == "com.fake.launcher" }
    }

    private suspend fun RemoteSession.awaitState(predicate: (TvState) -> Boolean) {
        withTimeout(WAIT_MS) { tvState.first(predicate) }
    }

    @Test
    fun `the readable app name and the package are both reported`() =
        runBlocking {
            val s = session()
            s.start()
            s.awaitConnectedAndSettled()
            tv.sendForegroundApp("com.netflix.ninja", "Netflix")
            s.awaitState { it.currentApp == "com.netflix.ninja" }
            assertEquals("Netflix", s.tvState.value.currentAppLabel)
            s.stop()
        }

    @Test
    fun `an app without a label has no label, and a new app drops the old label`() =
        runBlocking {
            val s = session()
            s.start()
            s.awaitConnectedAndSettled()
            tv.sendForegroundApp("com.netflix.ninja", "Netflix")
            s.awaitState { it.currentAppLabel == "Netflix" }
            tv.sendForegroundApp("com.vendor.player")
            s.awaitState { it.currentApp == "com.vendor.player" }
            assertNull(s.tvState.value.currentAppLabel)
            s.stop()
        }

    @Test
    fun `a text field request is reported and cleared when the app changes`() =
        runBlocking {
            val s = session()
            s.start()
            s.awaitConnectedAndSettled()
            assertFalse(s.tvState.value.textFieldActive)
            tv.sendTextFieldRequest()
            s.awaitState { it.textFieldActive }
            tv.sendForegroundApp("com.google.android.youtube.tv", "YouTube")
            s.awaitState { it.currentApp == "com.google.android.youtube.tv" }
            assertFalse(s.tvState.value.textFieldActive)
            s.stop()
        }

    @Test
    fun `a text field request is cleared by a new connection`() =
        runBlocking {
            val s = session()
            s.start()
            s.awaitConnectedAndSettled()
            tv.sendTextFieldRequest()
            s.awaitState { it.textFieldActive }
            s.stop()
            s.start()
            s.awaitConnectedAndSettled()
            assertFalse(s.tvState.value.textFieldActive)
            s.stop()
        }

    @Test
    fun `connection time starts when connected and ends when the session stops`() =
        runBlocking {
            var now = 1_000L
            val s = session { now }
            assertNull(s.connectedSince.value)
            s.start()
            s.awaitConnectedAndSettled()
            assertEquals(1_000L, s.connectedSince.value)
            now = 9_000L
            delay(150) // a ping must not restart the clock
            assertEquals(1_000L, s.connectedSince.value)
            s.stop()
            assertNull(s.connectedSince.value)
        }

    @Test
    fun `the diagnostics notes name the kind of message but never its content`() =
        runBlocking {
            val s = session()
            s.start()
            s.awaitConnectedAndSettled()
            tv.sendForegroundApp("com.secret.app", "Secret Label")
            tv.sendTextFieldRequest()
            s.awaitState { it.textFieldActive && it.currentAppLabel == "Secret Label" }
            assertTrue(notes.any { "text field request" in it })
            assertTrue(notes.any { "app label" in it })
            assertFalse(notes.any { "Secret" in it || "com.secret" in it })
            assertNotNull(s.connectedSince.value)
            s.stop()
        }

    private companion object {
        const val WAIT_MS = 10_000L
    }
}
