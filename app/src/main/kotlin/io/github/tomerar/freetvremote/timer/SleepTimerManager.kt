package io.github.tomerar.freetvremote.timer

import io.github.tomerar.freetvremote.data.SavedTv
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Sets and clears the alarm that wakes the app at the deadline. */
interface SleepTimerScheduler {
    /** Exact alarms are allowed. When not, [schedule] falls back to an alarm that may run late. */
    val canScheduleExact: Boolean

    fun schedule(timer: SleepTimer)

    fun cancel(timer: SleepTimer)
}

/** What the user sees outside the app: a countdown notification and the result. */
interface SleepTimerNotifications {
    fun showCountdown(timer: SleepTimer)

    fun clearCountdown()

    fun showResult(result: SleepTimerResult)
}

/** Switches a TV off, but only when it says it is on. Never throws for an unreachable TV. */
fun interface TvShutdown {
    suspend fun run(tvId: String): SleepOutcome
}

/** The clocks the timer needs; replaced in tests. */
class SleepTimerClock(
    val elapsedRealtimeMs: () -> Long,
    val wallClockMs: () -> Long,
    val bootCount: () -> Int,
)

/**
 * The sleep timer's rules. One timer at a time; it survives the process being killed (state lives in the repository,
 * the deadline in an alarm) and a reboot (rebuilt from the wall-clock deadline). A timer that could not run on time is
 * reported as missed and never fires late, because by then someone else may be watching.
 */
class SleepTimerManager(
    private val repository: SleepTimerRepository,
    private val scheduler: SleepTimerScheduler,
    private val notifications: SleepTimerNotifications,
    private val shutdown: TvShutdown,
    private val clock: SleepTimerClock,
    /** Short, non-sensitive notes for the diagnostics log (no names or addresses). */
    private val log: (String) -> Unit = {},
    /** A one-line description of the phone's power state (battery saver, idle mode...) for the log. */
    private val environment: () -> String = { "" },
) {
    private val lock = Mutex()

    /** The revision this process is switching off right now: a RUNNING timer with another revision was cut short. */
    @Volatile
    private var firing: Long? = null

    val state: Flow<SleepTimerState> = repository.state

    /** Starts a timer for [tv], replacing a running one. Returns the timer, or `null` if a shutdown is already running. */
    suspend fun start(tv: SavedTv, minutes: Int): SleepTimer? =
        lock.withLock {
            if (repository.current().active?.phase == SleepTimer.Phase.RUNNING) return@withLock null
            val duration = SleepTimerLimits.clamp(minutes) * MS_PER_MINUTE
            arm(tv.id, tv.name, duration, duration).also {
                log("timer started: ${duration / MS_PER_MINUTE} min, exact alarm=${it.exact}, ${environment()}")
            }
        }

    /** Adds [minutes] to the running timer. Returns the new timer, or `null` when there is none or it is too late. */
    suspend fun extend(minutes: Int): SleepTimer? =
        lock.withLock {
            val timer = repository.current().active?.takeIf { it.phase == SleepTimer.Phase.ARMED } ?: return@withLock null
            val now = clock.elapsedRealtimeMs()
            val extra = SleepTimerLimits.clamp(minutes) * MS_PER_MINUTE
            val remaining = timer.remainingMs(now) + extra
            val maxMs = SleepTimerLimits.MAX_MINUTES * MS_PER_MINUTE
            arm(timer.tvId, timer.tvName, timer.durationMs + extra, remaining.coerceAtMost(maxMs)).also {
                log("timer extended by ${extra / MS_PER_MINUTE} min, ${it.remainingMs(now) / MS_PER_MINUTE} min left")
            }
        }

    /** Cancels the running timer. Returns `false` when it is already switching the TV off. */
    suspend fun cancel(): Boolean =
        lock.withLock {
            val timer = repository.current().active ?: return@withLock true
            if (timer.phase == SleepTimer.Phase.RUNNING) {
                log("cancel refused: the TV is already being switched off")
                return@withLock false
            }
            log("timer cancelled")
            scheduler.cancel(timer)
            repository.clearActive()
            notifications.clearCountdown()
            true
        }

    suspend fun dismissResult() = repository.clearLast()

    /** The revision of the running timer, which a notification button must match to act on it. */
    suspend fun activeRevision(): Long? = repository.current().active?.revision

    /** The alarm for [revision] went off: switch the TV off (unless the timer was cancelled or extended meanwhile). */
    suspend fun onFire(revision: Long) {
        val timer =
            lock.withLock {
                val current = repository.current().active
                if (current == null || current.revision != revision || current.phase != SleepTimer.Phase.ARMED) {
                    log("alarm ignored: no matching timer (cancelled, replaced or already running)")
                    return
                }
                val late = (clock.elapsedRealtimeMs() - current.dueElapsedMs) / MS_PER_SECOND
                log("alarm fired $late s after the deadline, ${environment()}")
                firing = revision
                current.copy(phase = SleepTimer.Phase.RUNNING).also { repository.save(it) }
            }
        // Outside the lock: a cancel arriving now learns that it is too late instead of waiting for the TV.
        try {
            val outcome =
                try {
                    shutdown.run(timer.tvId)
                } catch (e: CancellationException) {
                    // The service ran out of time: record that it is unknown what happened, never leave it RUNNING.
                    withContext(NonCancellable) { lock.withLock { finish(timer, SleepOutcome.INTERRUPTED) } }
                    throw e
                }
            log("shutdown finished: $outcome")
            lock.withLock { finish(timer, outcome) }
        } finally {
            firing = null
        }
    }

    /**
     * Brings stored state and alarm back in line after a reboot, an update, a changed permission or an app start.
     * Safe to call at any time.
     */
    suspend fun reconcile() =
        lock.withLock {
            val timer = repository.current().active ?: return@withLock
            val now = clock.elapsedRealtimeMs()
            val wall = clock.wallClockMs()
            log(
                "reconcile: timer ${timer.phase}, ${timer.remainingMs(
                    now,
                ) / MS_PER_SECOND} s left, rebooted=${timer.bootCount != clock.bootCount()}",
            )
            when {
                timer.phase == SleepTimer.Phase.RUNNING -> {
                    if (firing != timer.revision) finish(timer, SleepOutcome.INTERRUPTED)
                }

                timer.bootCount != clock.bootCount() -> {
                    val remaining = timer.dueWallMs - wall
                    if (remaining > 0) {
                        rearm(timer, timer.copy(dueElapsedMs = now + remaining, bootCount = clock.bootCount()))
                    } else {
                        finish(timer, SleepOutcome.MISSED)
                    }
                }

                timer.dueElapsedMs + MISSED_AFTER_MS < now -> {
                    finish(timer, SleepOutcome.MISSED)
                }

                else -> {
                    rearm(timer, timer)
                }
            }
        }

    private suspend fun arm(tvId: String, tvName: String, durationMs: Long, remainingMs: Long): SleepTimer {
        val now = clock.elapsedRealtimeMs()
        val wall = clock.wallClockMs()
        val previous = repository.current().active
        previous?.let { scheduler.cancel(it) }
        val timer =
            SleepTimer(
                revision = maxOf(wall, (previous?.revision ?: 0L) + 1),
                tvId = tvId,
                tvName = tvName,
                durationMs = durationMs,
                dueElapsedMs = now + remainingMs,
                dueWallMs = wall + remainingMs,
                bootCount = clock.bootCount(),
                exact = scheduler.canScheduleExact,
            )
        repository.save(timer)
        scheduler.schedule(timer)
        notifications.showCountdown(timer)
        return timer
    }

    /** Stores [updated] if it differs from what is saved ([stored]) and sets its alarm and notification again. */
    private suspend fun rearm(stored: SleepTimer, updated: SleepTimer) {
        val timer = updated.copy(exact = scheduler.canScheduleExact)
        if (timer != stored) repository.save(timer)
        scheduler.schedule(timer)
        notifications.showCountdown(timer)
    }

    private suspend fun finish(timer: SleepTimer, outcome: SleepOutcome) {
        log("timer ended: $outcome")
        scheduler.cancel(timer)
        val result = SleepTimerResult(outcome, timer.tvName, clock.wallClockMs())
        repository.finish(result)
        notifications.clearCountdown()
        notifications.showResult(result)
    }

    private companion object {
        const val MS_PER_MINUTE = 60_000L
        const val MS_PER_SECOND = 1_000L

        /** An alarm this late is not going to come; running it now would surprise whoever watches the TV. */
        const val MISSED_AFTER_MS = 2 * MS_PER_MINUTE
    }
}
