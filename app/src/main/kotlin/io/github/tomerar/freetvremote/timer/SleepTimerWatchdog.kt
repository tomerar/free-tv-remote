package io.github.tomerar.freetvremote.timer

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * A second way to reach the deadline while the app process is alive: if the system alarm is late for any reason, this
 * switches the TV off [graceMs] after the deadline. Whichever comes first wins; the manager ignores the other one, so
 * the TV is never switched twice.
 */
class SleepTimerWatchdog(
    private val scope: CoroutineScope,
    private val timers: Flow<SleepTimerState>,
    private val manager: SleepTimerManager,
    private val elapsedRealtimeMs: () -> Long,
    private val log: (String) -> Unit = {},
    private val graceMs: Long = GRACE_MS,
) {
    fun start() {
        scope.launch {
            timers
                .map { it.active?.takeIf { timer -> timer.phase == SleepTimer.Phase.ARMED } }
                .distinctUntilChanged { a, b -> a?.revision == b?.revision && a?.dueElapsedMs == b?.dueElapsedMs }
                .collectLatest { timer ->
                    if (timer == null) return@collectLatest
                    delay(timer.remainingMs(elapsedRealtimeMs()) + graceMs)
                    log("the alarm did not arrive, the app switches the TV off itself")
                    // Its own job: starting the shutdown changes the stored state, which restarts this collector,
                    // and that must not cancel the shutdown it just started.
                    scope.launch { manager.onFire(timer.revision) }
                }
        }
    }

    private companion object {
        const val GRACE_MS = 5_000L
    }
}
