package io.github.tomerar.freetvremote.remote

import io.github.tomerar.freetvremote.awaitUntil
import io.github.tomerar.freetvremote.awaitValue
import io.github.tomerar.freetvremote.data.SavedTv
import io.github.tomerar.freetvremote.data.TvRepository
import io.github.tomerar.freetvremote.protocol.proto.RemoteDirection
import io.github.tomerar.freetvremote.protocol.remote.ConnectionState
import io.github.tomerar.freetvremote.protocol.remote.FailureReason
import io.github.tomerar.freetvremote.protocol.remote.KeyCodes
import io.github.tomerar.freetvremote.protocol.remote.RemoteSessionConfig
import io.github.tomerar.freetvremote.protocol.testing.FakeTv
import io.github.tomerar.freetvremote.protocol.tls.publicKeyPin
import io.github.tomerar.freetvremote.sharedIdentity
import io.github.tomerar.freetvremote.testDataStore
import io.github.tomerar.freetvremote.testIdentityProvider
import io.github.tomerar.freetvremote.warmUpTestIdentity
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The controller + repository + real protocol stack against fake TVs: multi-TV, reconnect, lifecycle. */
class RemoteControllerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tvs = mutableListOf<FakeTv>()
    private lateinit var repo: TvRepository
    private lateinit var controller: RemoteController

    private val fastSessions =
        SessionFactory { s, tv ->
            io.github.tomerar.freetvremote.protocol.remote.RemoteSession(
                s,
                tv.host,
                sharedIdentity,
                tv.pinBytes,
                RemoteSessionConfig(
                    port = tv.remotePort,
                    connectTimeoutMs = 1_000,
                    idleTimeoutMs = 2_000,
                    backoffMs = listOf(50, 100),
                ),
            )
        }

    @Before
    fun setUp() {
        warmUpTestIdentity()
        repo = TvRepository(testDataStore(scope, tmp.root))
        controller = RemoteController(scope, repo, fastSessions, backgroundGraceMs = 300, quickConnectTimeoutMs = 3_000)
    }

    @After
    fun tearDown() {
        scope.cancel()
        tvs.forEach { it.close() }
    }

    /** Creates a fake TV and records it as already paired (the phone's identity is registered on it). */
    private suspend fun pairedTv(name: String): Pair<FakeTv, SavedTv> {
        val fake = FakeTv(pingIntervalMs = 100).also { tvs += it }
        val coordinator = PairingCoordinator(scope, testIdentityProvider, repo)
        coordinator.start(name, "127.0.0.1", fake.pairingPort, fake.remotePort)
        coordinator.state.awaitValue { it is PairingState.AwaitingCode }
        awaitUntil { fake.displayedCode != null }
        coordinator.submit(fake.displayedCode!!)
        val ok = coordinator.state.awaitValue { it is PairingState.Success } as PairingState.Success
        return fake to ok.tv
    }

    private suspend fun pressUntilReceived(code: Int, received: () -> Boolean) {
        withTimeout(15_000) {
            while (!received()) {
                controller.pressKey(code)
                delay(100)
            }
        }
    }

    @Test
    fun `starts on the last used TV and connects when the app is in the foreground`() =
        runBlocking {
            val (fake, saved) = pairedTv("Shield")
            controller.start()
            controller.onAppForeground()
            controller.connection.awaitValue { it == ConnectionState.Connected }
            assertEquals(saved, controller.activeTv.value)

            assertTrue(controller.pressKey(KeyCodes.DPAD_CENTER))
            awaitUntil { fake.keys.isNotEmpty() }
            assertEquals(KeyCodes.DPAD_CENTER to RemoteDirection.SHORT, fake.keys.first())
        }

    @Test
    fun `nothing is connected before the app is in the foreground`() =
        runBlocking {
            pairedTv("Shield")
            controller.start()
            controller.activeTv.awaitValue { it != null }
            assertEquals(ConnectionState.Idle, controller.connection.value)
        }

    @Test
    fun `switching between saved TVs reconnects to the selected one and remembers it`() =
        runBlocking {
            val (first, _) = pairedTv("Shield")
            val (second, secondSaved) = pairedTv("TCL")
            controller.start()
            controller.onAppForeground()
            // Pairing the second TV made it the last used one.
            controller.activeTv.awaitValue { it?.id == secondSaved.id }
            pressUntilReceived(KeyCodes.HOME) { second.keys.isNotEmpty() }
            assertTrue(first.keys.isEmpty())

            val firstId =
                repo.tvs
                    .first()
                    .first { it.name == "Shield" }
                    .id
            controller.selectTv(firstId)
            controller.activeTv.awaitValue { it?.id == firstId }
            // `connection` is a derived StateFlow and can briefly lag behind the switch: press until it lands.
            pressUntilReceived(KeyCodes.BACK) { first.keys.isNotEmpty() }
            assertEquals(KeyCodes.BACK, first.keys.first().first)
            assertTrue(second.keys.none { it.first == KeyCodes.BACK })
            assertEquals(firstId, repo.lastUsedId.first())
        }

    @Test
    fun `reconnects automatically when the TV drops the connection`() =
        runBlocking {
            val (fake, _) = pairedTv("Shield")
            controller.start()
            controller.onAppForeground()
            controller.connection.awaitValue { it == ConnectionState.Connected }
            fake.dropRemoteConnections()
            controller.connection.awaitValue { it is ConnectionState.Reconnecting }
            controller.connection.awaitValue { it == ConnectionState.Connected }
            assertTrue(controller.pressKey(KeyCodes.VOLUME_UP))
        }

    @Test
    fun `disconnects after the grace period in the background and reconnects on return`() =
        runBlocking {
            val (fake, _) = pairedTv("Shield")
            controller.start()
            controller.onAppForeground()
            controller.connection.awaitValue { it == ConnectionState.Connected }

            controller.onAppBackground()
            controller.connection.awaitValue { it == ConnectionState.Idle }
            assertFalse(controller.pressKey(KeyCodes.HOME))

            controller.onAppForeground()
            controller.connection.awaitValue { it == ConnectionState.Connected }
            assertTrue(fake.remoteConnections.get() >= 2)
        }

    @Test
    fun `returning quickly keeps the connection`() =
        runBlocking {
            val (fake, _) = pairedTv("Shield")
            controller.start()
            controller.onAppForeground()
            controller.connection.awaitValue { it == ConnectionState.Connected }
            controller.onAppBackground()
            controller.onAppForeground()
            delay(600)
            assertEquals(ConnectionState.Connected, controller.connection.value)
            assertEquals(1, fake.remoteConnections.get())
        }

    @Test
    fun `removing the active TV disconnects and clears the selection`() =
        runBlocking {
            val (_, saved) = pairedTv("Shield")
            controller.start()
            controller.onAppForeground()
            controller.connection.awaitValue { it == ConnectionState.Connected }
            repo.remove(saved.id)
            controller.activeTv.awaitValue { it == null }
            controller.connection.awaitValue { it == ConnectionState.Idle }
            assertFalse(controller.pressKey(KeyCodes.HOME))
        }

    @Test
    fun `a TV that forgot this phone ends in the not paired state`() =
        runBlocking {
            val (fake, _) = pairedTv("Shield")
            fake.unpairAll()
            controller.start()
            controller.onAppForeground()
            val state = controller.connection.awaitValue { it is ConnectionState.Failed }
            assertEquals(ConnectionState.Failed(FailureReason.NOT_PAIRED), state)
        }

    @Test
    fun `quick keys connect on demand, send and disconnect again`() =
        runBlocking {
            val (fake, _) = pairedTv("Shield")
            controller.start()
            controller.activeTv.awaitValue { it != null }
            assertTrue(controller.sendQuickKey(KeyCodes.POWER))
            awaitUntil { fake.keys.isNotEmpty() }
            assertEquals(KeyCodes.POWER to RemoteDirection.SHORT, fake.keys.single())
            assertEquals(ConnectionState.Idle, controller.connection.value)
        }

    @Test
    fun `quick keys without any saved TV fail cleanly`() =
        runBlocking {
            controller.start()
            assertFalse(controller.sendQuickKey(KeyCodes.POWER))
            assertNull(controller.activeTv.value)
        }

    @Test
    fun `apps and text reach the TV`() =
        runBlocking {
            val (fake, _) = pairedTv("Shield")
            controller.start()
            controller.onAppForeground()
            controller.connection.awaitValue { it == ConnectionState.Connected }
            assertTrue(controller.launchApp("https://www.youtube.com"))
            assertTrue(controller.sendText("breaking bad"))
            awaitUntil { fake.launchedLinks.isNotEmpty() && fake.typedTexts.isNotEmpty() }
            assertEquals(listOf("https://www.youtube.com"), fake.launchedLinks.toList())
            assertEquals(listOf("breaking bad"), fake.typedTexts.toList())
        }

    @Test
    fun `the pin stored for a TV is the one of its certificate`() =
        runBlocking {
            val (fake, saved) = pairedTv("Shield")
            assertTrue(saved.pinBytes.contentEquals(fake.serverCertificate.publicKeyPin()))
        }
}
