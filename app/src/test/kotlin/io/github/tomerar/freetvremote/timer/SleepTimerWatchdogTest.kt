package io.github.tomerar.freetvremote.timer

import io.github.tomerar.freetvremote.awaitValue
import io.github.tomerar.freetvremote.testDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The backup path: if the system alarm never arrives, the app still switches the TV off, once. */
class SleepTimerWatchdogTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var elapsed = System.nanoTime() / 1_000_000
    private val calls = mutableListOf<String>()
    private lateinit var repo: SleepTimerRepository
    private lateinit var manager: SleepTimerManager

    @Before
    fun setUp() {
        repo = SleepTimerRepository(testDataStore(scope, tmp.root))
        manager =
            SleepTimerManager(
                repo,
                object : SleepTimerScheduler {
                    override val canScheduleExact = true

                    override fun schedule(timer: SleepTimer) = Unit

                    override fun cancel(timer: SleepTimer) = Unit
                },
                object : SleepTimerNotifications {
                    override fun showCountdown(timer: SleepTimer) = Unit

                    override fun clearCountdown() = Unit

                    override fun showResult(result: SleepTimerResult) = Unit
                },
                { id ->
                    calls += id
                    SleepOutcome.TURNED_OFF
                },
                SleepTimerClock({ elapsed }, { 1_700_000_000_000L }, { 1 }),
            )
    }

    @After
    fun tearDown() = scope.cancel()

    private fun armed(dueInMs: Long, revision: Long = 1) =
        SleepTimer(revision, "tv1", "TV", 60_000, elapsed + dueInMs, 1_700_000_000_000L + dueInMs, 1, true)

    private fun watchdog(graceMs: Long) = SleepTimerWatchdog(scope, manager.state, manager, { elapsed }, graceMs = graceMs)

    @Test
    fun `switches the TV off itself when the alarm never comes`() =
        runBlocking {
            repo.save(armed(dueInMs = 150))
            watchdog(graceMs = 50).start()
            val state = manager.state.awaitValue { it.last != null }
            assertEquals(listOf("tv1"), calls)
            assertEquals(SleepOutcome.TURNED_OFF, state.last?.outcome)
        }

    @Test
    fun `does nothing when the alarm came first`() =
        runBlocking {
            val timer = armed(dueInMs = 300)
            repo.save(timer)
            watchdog(graceMs = 100).start()
            delay(50)
            manager.onFire(timer.revision) // the system alarm
            delay(600)
            assertEquals(1, calls.size)
        }

    @Test
    fun `a cancelled timer is never fired`() =
        runBlocking {
            repo.save(armed(dueInMs = 200))
            watchdog(graceMs = 50).start()
            delay(50)
            assertTrue(manager.cancel())
            delay(500)
            assertTrue(calls.isEmpty())
        }

    @Test
    fun `an extended timer waits for its new deadline`() =
        runBlocking {
            repo.save(armed(dueInMs = 200))
            watchdog(graceMs = 50).start()
            delay(50)
            repo.save(armed(dueInMs = 1_500, revision = 2))
            delay(700)
            assertTrue("the old deadline must not fire", calls.isEmpty())
        }
}
