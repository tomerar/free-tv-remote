package io.github.tomerar.freetvremote.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import io.github.tomerar.freetvremote.awaitUntil
import io.github.tomerar.freetvremote.awaitValue
import io.github.tomerar.freetvremote.data.SavedTv
import io.github.tomerar.freetvremote.data.TvRepository
import io.github.tomerar.freetvremote.protocol.proto.RemoteDirection
import io.github.tomerar.freetvremote.protocol.remote.ConnectionState
import io.github.tomerar.freetvremote.protocol.remote.KeyCodes
import io.github.tomerar.freetvremote.protocol.remote.RemoteSession
import io.github.tomerar.freetvremote.protocol.remote.RemoteSessionConfig
import io.github.tomerar.freetvremote.protocol.testing.FakeTv
import io.github.tomerar.freetvremote.testDataStore
import io.github.tomerar.freetvremote.testIdentityProvider
import io.github.tomerar.freetvremote.warmUpTestIdentity
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Failures that are not network problems (identity cannot be loaded or saved, storage write fails) and
 * overlapping quick commands. None of these may crash the app or leave a screen waiting forever.
 */
class FailureHandlingTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val uncaught = CopyOnWriteArrayList<Throwable>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, e -> uncaught += e })
    private lateinit var tv: FakeTv
    private lateinit var repo: TvRepository

    @Before
    fun setUp() {
        warmUpTestIdentity()
        tv = FakeTv(pingIntervalMs = 100)
        repo = TvRepository(testDataStore(scope, tmp.root))
    }

    @After
    fun tearDown() {
        scope.cancel()
        tv.close()
    }

    private fun fastSessions(identity: IdentityProvider) =
        SessionFactory { s, saved ->
            RemoteSession(
                s,
                saved.host,
                identity.get(),
                saved.pinBytes,
                RemoteSessionConfig(port = saved.remotePort, connectTimeoutMs = 2_000, idleTimeoutMs = 5_000, backoffMs = listOf(50, 100)),
            )
        }

    private suspend fun pairedTv(): SavedTv {
        val coordinator = PairingCoordinator(scope, testIdentityProvider, repo)
        coordinator.start("TV", "127.0.0.1", tv.pairingPort, tv.remotePort)
        coordinator.state.awaitValue { it is PairingState.AwaitingCode }
        awaitUntil { tv.displayedCode != null }
        coordinator.submit(tv.displayedCode!!)
        return (coordinator.state.awaitValue { it is PairingState.Success } as PairingState.Success).tv
    }

    // --- pairing ---------------------------------------------------------------------------------------

    @Test
    fun `pairing reports a recoverable failure when the client identity cannot be prepared`() =
        runBlocking {
            val coordinator = PairingCoordinator(scope, { throw IOException("disk full") }, repo)
            coordinator.start("TV", "127.0.0.1", tv.pairingPort, tv.remotePort)
            val state = coordinator.state.awaitValue(timeoutMs = 15_000) { it is PairingState.Failed }
            assertEquals(PairingState.Failed(PairingFailure.INTERNAL), state)
            assertTrue("no uncaught exception expected", uncaught.isEmpty())
        }

    @Test
    fun `an identity failure can be retried once the problem is gone`() =
        runBlocking {
            var broken = true
            val identity = IdentityProvider { if (broken) throw IOException("disk full") else testIdentityProvider.get() }
            val coordinator = PairingCoordinator(scope, identity, repo)
            coordinator.start("TV", "127.0.0.1", tv.pairingPort, tv.remotePort)
            coordinator.state.awaitValue(timeoutMs = 15_000) { it is PairingState.Failed }
            broken = false
            coordinator.start("TV", "127.0.0.1", tv.pairingPort, tv.remotePort)
            coordinator.state.awaitValue { it is PairingState.AwaitingCode }
            Unit
        }

    @Test
    fun `pairing reports a recoverable failure when the pairing cannot be saved`() =
        runBlocking {
            val failingStore =
                object : DataStore<Preferences> {
                    override val data: Flow<Preferences> = testDataStore(scope, tmp.root, "failing").data

                    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
                        throw IOException("cannot write")
                }
            val coordinator = PairingCoordinator(scope, testIdentityProvider, TvRepository(failingStore))
            coordinator.start("TV", "127.0.0.1", tv.pairingPort, tv.remotePort)
            coordinator.state.awaitValue { it is PairingState.AwaitingCode }
            awaitUntil { tv.displayedCode != null }
            coordinator.submit(tv.displayedCode!!)
            val state = coordinator.state.awaitValue(timeoutMs = 15_000) { it is PairingState.Failed }
            assertEquals(PairingState.Failed(PairingFailure.INTERNAL), state)
            assertTrue("no uncaught exception expected", uncaught.isEmpty())
        }

    // --- controller start-up ---------------------------------------------------------------------------

    @Test
    fun `an identity failure while selecting a saved TV does not crash and recovers on reconnect`() =
        runBlocking {
            val saved = pairedTv()
            var broken = true
            val identity = IdentityProvider { if (broken) throw IOException("keystore unavailable") else testIdentityProvider.get() }
            val controller = RemoteController(scope, repo, fastSessions(identity), backgroundGraceMs = 300)
            controller.start()
            controller.onAppForeground()
            controller.activeTv.awaitValue(timeoutMs = 15_000) { it?.id == saved.id }
            assertTrue("no uncaught exception expected: $uncaught", uncaught.isEmpty())
            assertFalse(controller.pressKey(KeyCodes.HOME)) // nothing to send through, but no crash

            broken = false
            controller.reconnect()
            controller.connection.awaitValue { it == ConnectionState.Connected }
            assertTrue(controller.pressKey(KeyCodes.HOME))
        }

    // --- quick commands (widget / tile) ----------------------------------------------------------------

    @Test
    fun `quick keys fail cleanly when the identity cannot be loaded`() =
        runBlocking {
            pairedTv()
            val identity = IdentityProvider { throw IOException("keystore unavailable") }
            val controller = RemoteController(scope, repo, fastSessions(identity), quickConnectTimeoutMs = 6_000)
            controller.start()
            assertFalse(controller.sendQuickKey(KeyCodes.POWER))
            assertTrue("no uncaught exception expected: $uncaught", uncaught.isEmpty())
        }

    @Test
    fun `simultaneous quick keys use one temporary connection at a time and all arrive`() =
        runBlocking {
            pairedTv()
            val controller = RemoteController(scope, repo, fastSessions(testIdentityProvider), quickConnectTimeoutMs = 5_000)
            controller.start()
            controller.activeTv.awaitValue { it != null }
            val codes = listOf(KeyCodes.VOLUME_UP, KeyCodes.VOLUME_DOWN, KeyCodes.VOLUME_MUTE)
            val results = codes.map { code -> async { controller.sendQuickKey(code) } }.awaitAll()

            assertEquals(listOf(true, true, true), results)
            awaitUntil { tv.keys.size == 3 }
            assertEquals(codes.toSet(), tv.keys.map { it.first }.toSet())
            assertTrue(tv.keys.all { it.second == RemoteDirection.SHORT })
            assertEquals("temporary connections must not overlap", 1, tv.peakRemoteConnections.get())
            awaitUntil { tv.activeRemoteConnections.get() == 0 }
        }

    @Test
    fun `a quick key while the app is connected reuses the live session`() =
        runBlocking {
            pairedTv()
            val controller = RemoteController(scope, repo, fastSessions(testIdentityProvider), quickConnectTimeoutMs = 5_000)
            controller.start()
            controller.onAppForeground()
            controller.connection.awaitValue { it == ConnectionState.Connected }
            val before = tv.remoteConnections.get()
            assertTrue(controller.sendQuickKey(KeyCodes.POWER))
            awaitUntil { tv.keys.isNotEmpty() }
            assertEquals("no extra connection expected", before, tv.remoteConnections.get())
            assertEquals(ConnectionState.Connected, controller.connection.first { true })
        }
}
