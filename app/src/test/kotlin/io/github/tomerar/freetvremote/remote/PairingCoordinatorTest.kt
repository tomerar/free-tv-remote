package io.github.tomerar.freetvremote.remote

import io.github.tomerar.freetvremote.awaitUntil
import io.github.tomerar.freetvremote.awaitValue
import io.github.tomerar.freetvremote.data.TvRepository
import io.github.tomerar.freetvremote.protocol.pairing.PairingClient
import io.github.tomerar.freetvremote.protocol.testing.FakeTv
import io.github.tomerar.freetvremote.protocol.tls.publicKeyPin
import io.github.tomerar.freetvremote.testDataStore
import io.github.tomerar.freetvremote.testIdentityProvider
import io.github.tomerar.freetvremote.warmUpTestIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PairingCoordinatorTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var tv: FakeTv
    private lateinit var repo: TvRepository
    private lateinit var coordinator: PairingCoordinator

    @Before
    fun setUp() {
        warmUpTestIdentity()
        tv = FakeTv()
        repo = TvRepository(testDataStore(scope, tmp.root))
        coordinator =
            PairingCoordinator(
                scope,
                testIdentityProvider,
                repo,
                // Bounded timeouts: a TV that never answers must end in a failure, never hang a test.
                PairingClientFactory { id, host, port ->
                    PairingClient(id, host, port, connectTimeoutMs = 2_000, readTimeoutMs = 5_000)
                },
            )
    }

    @After
    fun tearDown() {
        coordinator.cancel()
        scope.cancel()
        tv.close()
    }

    private fun begin() = coordinator.start("Living Room", "127.0.0.1", tv.pairingPort, tv.remotePort)

    private suspend fun awaitCode(): String {
        awaitUntil { tv.displayedCode != null }
        return tv.displayedCode!!
    }

    @Test
    fun `entering the displayed code pairs and saves the TV`() =
        runBlocking {
            begin()
            coordinator.state.awaitValue { it is PairingState.AwaitingCode }
            coordinator.submit(awaitCode())
            val success = coordinator.state.awaitValue { it is PairingState.Success } as PairingState.Success

            val saved = repo.tvs.first().single()
            assertEquals(success.tv, saved)
            assertEquals("Living Room", saved.name)
            assertEquals(tv.remotePort, saved.remotePort)
            assertArrayEquals(tv.serverCertificate.publicKeyPin(), saved.pinBytes)
            assertEquals(saved.id, repo.lastUsedId.first())
            assertEquals(1, tv.pairedCount)
        }

    @Test
    fun `wrong code keeps the session open and the right code then works`() =
        runBlocking {
            begin()
            coordinator.state.awaitValue { it is PairingState.AwaitingCode }
            val code = awaitCode()
            val wrong = code.dropLast(1) + (if (code.last() == '0') '1' else '0')
            coordinator.submit(wrong)
            coordinator.state.awaitValue { it == PairingState.AwaitingCode(lastCodeWasInvalid = true) }
            coordinator.submit(code)
            coordinator.state.awaitValue { it is PairingState.Success }
            assertEquals(1, repo.tvs.first().size)
        }

    @Test
    fun `malformed input is flagged without touching the network`() =
        runBlocking {
            begin()
            coordinator.state.awaitValue { it is PairingState.AwaitingCode }
            coordinator.submit("12")
            assertEquals(PairingState.AwaitingCode(lastCodeWasInvalid = true), coordinator.state.value)
            assertEquals(0, tv.pairedCount)
        }

    @Test
    fun `TV that is off is reported as unreachable`() =
        runBlocking {
            val port = tv.pairingPort
            tv.close()
            coordinator.start("Off TV", "127.0.0.1", port, tv.remotePort)
            val failed = coordinator.state.awaitValue { it is PairingState.Failed }
            assertEquals(PairingState.Failed(PairingFailure.UNREACHABLE), failed)
            assertTrue(repo.tvs.first().isEmpty())
        }

    @Test
    fun `TV rejecting the secret ends in a rejected failure and saves nothing`() =
        runBlocking {
            tv.rejectSecrets = true
            begin()
            coordinator.state.awaitValue { it is PairingState.AwaitingCode }
            coordinator.submit(awaitCode())
            val failed = coordinator.state.awaitValue { it is PairingState.Failed }
            assertEquals(PairingState.Failed(PairingFailure.REJECTED), failed)
            assertTrue(repo.tvs.first().isEmpty())
        }

    @Test
    fun `cancel returns to idle and a new attempt can start`() =
        runBlocking {
            begin()
            coordinator.state.awaitValue { it is PairingState.AwaitingCode }
            coordinator.cancel()
            assertEquals(PairingState.Idle, coordinator.state.value)
            begin()
            coordinator.state.awaitValue { it is PairingState.AwaitingCode }
            Unit
        }

    @Test
    fun `pairing the same TV again keeps one entry`() =
        runBlocking {
            repeat(2) {
                tv.displayedCodeReset()
                begin()
                coordinator.state.awaitValue { it is PairingState.AwaitingCode }
                coordinator.submit(awaitCode())
                coordinator.state.awaitValue { it is PairingState.Success }
            }
            assertEquals(1, repo.tvs.first().size)
        }
}
