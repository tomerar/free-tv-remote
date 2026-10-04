package io.github.tomerar.freetvremote.protocol.remote

import io.github.tomerar.freetvremote.protocol.pairing.PairingClient
import io.github.tomerar.freetvremote.protocol.testing.FakeTv
import io.github.tomerar.freetvremote.protocol.tls.ClientIdentity
import io.github.tomerar.freetvremote.protocol.tls.SelfSignedCertificate
import io.github.tomerar.freetvremote.protocol.tls.publicKeyPin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TvIdentityCheckTest {
    private lateinit var tv: FakeTv
    private lateinit var identity: ClientIdentity
    private lateinit var pin: ByteArray

    @Before
    fun setUp() {
        tv = FakeTv(pingIntervalMs = 100)
        identity = SelfSignedCertificate.generate("identity-check-client")
        pin =
            runBlocking {
                PairingClient(identity, "127.0.0.1", tv.pairingPort).begin().use { session ->
                    withTimeout(10_000) { while (tv.displayedCode == null) delay(10) }
                    session.submitCode(tv.displayedCode!!).publicKeyPin()
                }
            }
    }

    @After
    fun tearDown() = tv.close()

    @Test
    fun `the TV that was paired is confirmed`() =
        runBlocking {
            assertTrue(confirmPinnedTv(identity, "127.0.0.1", tv.remotePort, pin))
        }

    @Test
    fun `a device with another key is not confirmed`() =
        runBlocking {
            val other = pin.copyOf().also { it[0] = (it[0] + 1).toByte() }
            assertFalse(confirmPinnedTv(identity, "127.0.0.1", tv.remotePort, other))
        }

    @Test
    fun `an address where nothing listens is not confirmed`() =
        runBlocking {
            // The port stays bound but nothing answers (a TV that is off): no race with whoever else may take a freed port.
            tv.setRemoteReachable(false)
            assertFalse(confirmPinnedTv(identity, "127.0.0.1", tv.remotePort, pin, connectTimeoutMs = 500))
        }

    @Test
    fun `the check sends nothing to the TV`() =
        runBlocking {
            confirmPinnedTv(identity, "127.0.0.1", tv.remotePort, pin)
            delay(300)
            assertEquals(0, tv.keys.size)
            assertEquals(0, tv.typedTexts.size)
            assertEquals(0, tv.launchedLinks.size)
        }
}
