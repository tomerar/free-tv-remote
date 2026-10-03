package io.github.tomerar.freetvremote.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Low level key transport the gestures drive. */
interface KeySender {
    suspend fun tap(code: Int)

    suspend fun holdStart(code: Int)

    suspend fun holdEnd(code: Int)
}

enum class KeyBehavior {
    /** Navigation / volume: one tap on press, then repeated taps while held. */
    REPEAT,

    /** A tap on release; if held past the long press timeout, a long press (hold start/end) instead. */
    TAP_OR_LONG,
}

data class GestureTiming(
    val repeatDelayMs: Long = 400,
    val repeatIntervalMs: Long = 110,
    val longPressMs: Long = 500,
)

/**
 * Turns touch down/up events into key traffic. Pure coroutine logic, independent
 * of Compose, so it can be tested with virtual time. Each key code is tracked
 * independently, a lost "up" is recovered by [releaseAll].
 */
class KeyGestures(
    private val scope: CoroutineScope,
    private val sender: KeySender,
    private val timing: GestureTiming = GestureTiming(),
) {
    private class Active(val job: Job, val longStarted: BooleanHolder)

    private class BooleanHolder(@Volatile var value: Boolean = false)

    private val active = HashMap<Int, Active>()

    @Synchronized
    fun down(code: Int, behavior: KeyBehavior) {
        if (active.containsKey(code)) return
        val longStarted = BooleanHolder()
        val job = scope.launch {
            when (behavior) {
                KeyBehavior.REPEAT -> {
                    sender.tap(code)
                    delay(timing.repeatDelayMs)
                    while (true) {
                        sender.tap(code)
                        delay(timing.repeatIntervalMs)
                    }
                }
                KeyBehavior.TAP_OR_LONG -> {
                    delay(timing.longPressMs)
                    longStarted.value = true
                    sender.holdStart(code)
                }
            }
        }
        active[code] = Active(job, longStarted)
    }

    @Synchronized
    fun up(code: Int, behavior: KeyBehavior) {
        val current = active.remove(code) ?: return
        current.job.cancel()
        if (behavior == KeyBehavior.TAP_OR_LONG) {
            scope.launch {
                if (current.longStarted.value) sender.holdEnd(code) else sender.tap(code)
            }
        }
    }

    /** Safety net for lost touch events (lifecycle changes, disconnects): ends everything in flight. */
    @Synchronized
    fun releaseAll() {
        val snapshot = active.toMap()
        active.clear()
        snapshot.forEach { (code, state) ->
            state.job.cancel()
            if (state.longStarted.value) scope.launch { sender.holdEnd(code) }
        }
    }
}
