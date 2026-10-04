package io.github.tomerar.freetvremote.remote

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
import io.github.tomerar.freetvremote.sharedIdentity
import io.github.tomerar.freetvremote.testDataStore
import io.github.tomerar.freetvremote.testIdentityProvider
import io.github.tomerar.freetvremote.timer.SleepOutcome
import io.github.tomerar.freetvremote.warmUpTestIdentity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The safe power-off against fake TVs: a toggle key only goes out when the TV says, on this connection, that it is on. */
class TvShutdownUseCaseTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fakes = mutableListOf<FakeTv>()
    private lateinit var repo: TvRepository
    private lateinit var controller: RemoteController
    private lateinit var useCase: TvShutdownUseCase

    private val fastSessions =
        SessionFactory { s, tv ->
            RemoteSession(
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
        useCase =
            TvShutdownUseCase(
                scope,
                repo,
                controller,
                fastSessions,
                connectTimeoutMs = 2_000,
                reportTimeoutMs = 800,
                offReportTimeoutMs = 600,
                totalTimeoutMs = 10_000,
            )
    }

    @After
    fun tearDown() {
        scope.cancel()
        fakes.forEach { it.close() }
    }

    private suspend fun pairedTv(name: String = "Shield"): Pair<FakeTv, SavedTv> {
        val fake = FakeTv(pingIntervalMs = 100).also { fakes += it }
        val coordinator = PairingCoordinator(scope, testIdentityProvider, repo)
        coordinator.start(name, "127.0.0.1", fake.pairingPort, fake.remotePort)
        coordinator.state.awaitValue { it is PairingState.AwaitingCode }
        awaitUntil { fake.displayedCode != null }
        coordinator.submit(fake.displayedCode!!)
        val ok = coordinator.state.awaitValue { it is PairingState.Success } as PairingState.Success
        return fake to ok.tv
    }

    private fun FakeTv.powerKeys() = keys.count { it.first == KeyCodes.POWER }

    @Test
    fun `a TV that reports it is on gets exactly one power key`() =
        runBlocking {
            val (fake, tv) = pairedTv()
            assertEquals(SleepOutcome.SENT, useCase.run(tv.id))
            awaitUntil { fake.powerKeys() == 1 }
            assertEquals(KeyCodes.POWER to RemoteDirection.SHORT, fake.keys.single { it.first == KeyCodes.POWER })
        }

    @Test
    fun `a TV that reports it is off gets nothing`() =
        runBlocking {
            val (fake, tv) = pairedTv()
            fake.powerOnAtConnect = false
            assertEquals(SleepOutcome.ALREADY_OFF, useCase.run(tv.id))
            delay(200)
            assertEquals(0, fake.powerKeys())
        }

    @Test
    fun `a TV that sends no power report gets nothing`() =
        runBlocking {
            val (fake, tv) = pairedTv()
            fake.powerOnAtConnect = null
            assertEquals(SleepOutcome.UNKNOWN_STATE, useCase.run(tv.id))
            delay(200)
            assertEquals(0, fake.powerKeys())
        }

    @Test
    fun `a TV that cannot be reached is reported as unreachable`() =
        runBlocking {
            val (fake, tv) = pairedTv()
            fake.setRemoteReachable(false)
            assertEquals(SleepOutcome.UNREACHABLE, useCase.run(tv.id))
            assertEquals(0, fake.powerKeys())
        }

    @Test
    fun `running out of time after the key went out is reported as sent, not unreachable`() =
        runBlocking {
            val (fake, tv) = pairedTv()
            val slow =
                TvShutdownUseCase(
                    scope,
                    repo,
                    controller,
                    fastSessions,
                    connectTimeoutMs = 2_000,
                    reportTimeoutMs = 800,
                    offReportTimeoutMs = 5_000,
                    totalTimeoutMs = 1_500,
                )
            assertEquals(SleepOutcome.SENT, slow.run(tv.id))
            assertEquals(1, fake.powerKeys())
        }

    @Test
    fun `a TV that confirms standby is reported as turned off`() =
        runBlocking {
            val (fake, tv) = pairedTv()
            scope.launchPowerReport(fake)
            assertEquals(SleepOutcome.TURNED_OFF, useCase.run(tv.id))
        }

    @Test
    fun `a removed TV is reported and nothing is sent`() =
        runBlocking {
            val (fake, _) = pairedTv()
            assertEquals(SleepOutcome.TV_REMOVED, useCase.run("no-such-tv"))
            assertEquals(0, fake.remoteConnections.get())
        }

    @Test
    fun `the open remote's connection is reused instead of opening a second one`() =
        runBlocking {
            val (fake, tv) = pairedTv()
            controller.start()
            controller.onAppForeground()
            controller.connection.awaitValue { it == ConnectionState.Connected }
            controller.tvState.awaitValue { it.isOnFresh }
            useCase.run(tv.id)
            awaitUntil { fake.powerKeys() == 1 }
            assertEquals(1, fake.peakRemoteConnections.get())
        }

    /** After the TV gets its power key it announces standby, as a real TV does. */
    private fun CoroutineScope.launchPowerReport(fake: FakeTv) {
        launch {
            while (fake.powerKeys() == 0) delay(10)
            fake.sendPower(started = false)
        }
    }
}
