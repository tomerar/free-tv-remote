package io.github.tomerar.freetvremote.timer

import io.github.tomerar.freetvremote.data.SavedTv
import io.github.tomerar.freetvremote.testDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The timer's rules with fake clocks, alarms and TV: deadlines, stale alarms, reboot, missed timers. */
class SleepTimerManagerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tv = SavedTv(id = "tv1", name = "Living Room", host = "10.0.0.5", pin = "AA==")

    private var elapsed = 1_000_000L
    private var wall = 1_700_000_000_000L
    private var boot = 7
    private var exact = true
    private var outcome = SleepOutcome.TURNED_OFF
    private val shutdownCalls = mutableListOf<String>()
    private val scheduled = mutableListOf<SleepTimer>()
    private val cancelled = mutableListOf<SleepTimer>()
    private val shown = mutableListOf<SleepTimer>()
    private val results = mutableListOf<SleepTimerResult>()
    private var cleared = 0

    private lateinit var repo: SleepTimerRepository
    private lateinit var manager: SleepTimerManager

    @Before
    fun setUp() {
        repo = SleepTimerRepository(testDataStore(scope, tmp.root))
        manager =
            SleepTimerManager(
                repo,
                object : SleepTimerScheduler {
                    override val canScheduleExact get() = exact

                    override fun schedule(timer: SleepTimer) {
                        scheduled += timer
                    }

                    override fun cancel(timer: SleepTimer) {
                        cancelled += timer
                    }
                },
                object : SleepTimerNotifications {
                    override fun showCountdown(timer: SleepTimer) {
                        shown += timer
                    }

                    override fun clearCountdown() {
                        cleared++
                    }

                    override fun showResult(result: SleepTimerResult) {
                        results += result
                    }
                },
                { id ->
                    shutdownCalls += id
                    outcome
                },
                SleepTimerClock({ elapsed }, { wall }, { boot }),
            )
    }

    @After
    fun tearDown() = scope.cancel()

    private fun minutes(n: Int) = n * 60_000L

    @Test
    fun `starting arms the deadline and shows the countdown`() =
        runBlocking {
            val timer = manager.start(tv, 45)!!
            assertEquals(elapsed + minutes(45), timer.dueElapsedMs)
            assertEquals(wall + minutes(45), timer.dueWallMs)
            assertEquals(tv.id, timer.tvId)
            assertEquals(timer, scheduled.single())
            assertEquals(timer, shown.single())
            assertEquals(timer, repo.current().active)
        }

    @Test
    fun `any number of minutes works but stays inside the limits`() =
        runBlocking {
            assertEquals(minutes(37), manager.start(tv, 37)!!.durationMs)
            assertEquals(minutes(SleepTimerLimits.MAX_MINUTES), manager.start(tv, 100_000)!!.durationMs)
            assertEquals(minutes(SleepTimerLimits.MIN_MINUTES), manager.start(tv, 0)!!.durationMs)
        }

    @Test
    fun `starting a second timer replaces the first one`() =
        runBlocking {
            val first = manager.start(tv, 30)!!
            val second = manager.start(tv, 60)!!
            assertTrue(second.revision != first.revision)
            assertTrue(first in cancelled)
            assertEquals(second, repo.current().active)
        }

    @Test
    fun `the alarm switches the TV off once and records the outcome`() =
        runBlocking {
            val timer = manager.start(tv, 30)!!
            elapsed += minutes(30)
            manager.onFire(timer.revision)
            manager.onFire(timer.revision)
            assertEquals(listOf(tv.id), shutdownCalls)
            assertNull(repo.current().active)
            assertEquals(SleepOutcome.TURNED_OFF, repo.current().last?.outcome)
            assertEquals(SleepOutcome.TURNED_OFF, results.single().outcome)
        }

    @Test
    fun `an alarm of a replaced or cancelled timer does nothing`() =
        runBlocking {
            val first = manager.start(tv, 30)!!
            manager.start(tv, 60)
            manager.onFire(first.revision)
            assertTrue(shutdownCalls.isEmpty())

            val second = repo.current().active!!
            assertTrue(manager.cancel())
            manager.onFire(second.revision)
            assertTrue(shutdownCalls.isEmpty())
            assertNull(repo.current().active)
        }

    @Test
    fun `cancel stops the alarm and clears the countdown`() =
        runBlocking {
            val timer = manager.start(tv, 30)!!
            assertTrue(manager.cancel())
            assertTrue(timer in cancelled)
            assertNull(repo.current().active)
            assertNull(repo.current().last)
            assertTrue(cleared > 0)
        }

    @Test
    fun `extending adds time to what is left`() =
        runBlocking {
            val timer = manager.start(tv, 30)!!
            elapsed += minutes(10)
            wall += minutes(10)
            val extended = manager.extend(15)!!
            assertEquals(timer.dueElapsedMs + minutes(15), extended.dueElapsedMs)
            assertEquals(timer.dueWallMs + minutes(15), extended.dueWallMs)
            assertEquals(extended, repo.current().active)
            // The alarm of the old deadline is stale now.
            manager.onFire(timer.revision)
            assertTrue(shutdownCalls.isEmpty())
        }

    @Test
    fun `extending without a timer does nothing`() =
        runBlocking {
            assertNull(manager.extend(15))
        }

    @Test
    fun `cancelling while the TV is being switched off is too late`() =
        runBlocking {
            val timer = manager.start(tv, 30)!!
            repo.save(timer.copy(phase = SleepTimer.Phase.RUNNING))
            assertFalse(manager.cancel())
            assertNotNull(repo.current().active)
            assertNull(manager.start(tv, 10))
        }

    @Test
    fun `without exact alarm access the timer says it is approximate`() =
        runBlocking {
            exact = false
            assertFalse(manager.start(tv, 30)!!.exact)
        }

    @Test
    fun `after a reboot a future timer keeps its wall-clock deadline`() =
        runBlocking {
            val timer = manager.start(tv, 60)!!
            // The phone was off for 20 minutes: new boot, the elapsed clock starts again from a small value.
            boot++
            wall += minutes(20)
            elapsed = 5_000L
            scheduled.clear()
            manager.reconcile()
            val rebuilt = repo.current().active!!
            assertEquals(timer.revision, rebuilt.revision)
            assertEquals(5_000L + minutes(40), rebuilt.dueElapsedMs)
            assertEquals(boot, rebuilt.bootCount)
            assertEquals(rebuilt, scheduled.single())
        }

    @Test
    fun `after a reboot an expired timer is reported missed and never fires late`() =
        runBlocking {
            manager.start(tv, 30)
            boot++
            wall += minutes(45)
            elapsed = 5_000L
            manager.reconcile()
            assertTrue(shutdownCalls.isEmpty())
            assertNull(repo.current().active)
            assertEquals(SleepOutcome.MISSED, repo.current().last?.outcome)
        }

    @Test
    fun `a lost alarm in the same boot is missed after a grace period`() =
        runBlocking {
            manager.start(tv, 30)
            elapsed += minutes(30) + minutes(1)
            manager.reconcile()
            assertNotNull("within the grace period the alarm may still come", repo.current().active)
            elapsed += minutes(5)
            manager.reconcile()
            assertEquals(SleepOutcome.MISSED, repo.current().last?.outcome)
            assertTrue(shutdownCalls.isEmpty())
        }

    @Test
    fun `a shutdown cut short by the process dying is reported as interrupted, not repeated`() =
        runBlocking {
            val timer = manager.start(tv, 30)!!
            repo.save(timer.copy(phase = SleepTimer.Phase.RUNNING))
            manager.reconcile()
            assertEquals(SleepOutcome.INTERRUPTED, repo.current().last?.outcome)
            assertTrue(shutdownCalls.isEmpty())
        }

    @Test
    fun `reconcile on an ordinary start keeps the timer and puts its alarm back`() =
        runBlocking {
            val timer = manager.start(tv, 30)!!
            scheduled.clear()
            manager.reconcile()
            assertEquals(timer, repo.current().active)
            assertEquals(timer, scheduled.single())
        }

    @Test
    fun `a shutdown that is cut off by the time limit ends as interrupted, not stuck`() =
        runBlocking {
            val slow =
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
                    { awaitCancellation() },
                    SleepTimerClock({ elapsed }, { wall }, { boot }),
                )
            val timer = slow.start(tv, 30)!!
            withTimeoutOrNull(300) { slow.onFire(timer.revision) }
            assertNull(repo.current().active)
            assertEquals(SleepOutcome.INTERRUPTED, repo.current().last?.outcome)
        }

    @Test
    fun `the next timer starts from the minutes asked for last, also after cancelling`() =
        runBlocking {
            assertNull(repo.current().lastMinutes)
            manager.start(tv, 1)
            assertEquals(1, repo.current().lastMinutes)
            assertTrue(manager.cancel())
            assertEquals("cancel must not bring back the default", 1, repo.current().lastMinutes)
            manager.start(tv, 45)
            manager.extend(15)
            assertEquals("extending does not change what was asked for", 45, repo.current().lastMinutes)
        }

    @Test
    fun `the result can be dismissed`() =
        runBlocking {
            val timer = manager.start(tv, 30)!!
            manager.onFire(timer.revision)
            manager.dismissResult()
            assertNull(repo.current().last)
        }
}
