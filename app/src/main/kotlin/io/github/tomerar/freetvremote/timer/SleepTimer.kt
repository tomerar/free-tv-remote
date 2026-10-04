package io.github.tomerar.freetvremote.timer

import kotlinx.serialization.Serializable

/** The one running sleep timer. It belongs to one saved TV, whatever remote is open when it runs out. */
@Serializable
data class SleepTimer(
    /** Changes whenever the timer is started or extended: an alarm that carries an old value is ignored. */
    val revision: Long,
    val tvId: String,
    /** Kept so the result can name the TV even if it was removed meanwhile. */
    val tvName: String,
    val durationMs: Long,
    /** The deadline on the clock that keeps running in deep sleep and is not changed by the user. Valid until reboot. */
    val dueElapsedMs: Long,
    /** The same deadline as wall-clock time: rebuilds the deadline after a reboot and tells the user when it ends. */
    val dueWallMs: Long,
    /** The system boot count when the timer was armed (to notice a reboot). */
    val bootCount: Int,
    /** The alarm is exact. `false` means the user did not allow exact alarms and it may run late. */
    val exact: Boolean,
    val phase: Phase = Phase.ARMED,
) {
    enum class Phase {
        /** Counting down. */
        ARMED,

        /** The deadline passed and the TV is being switched off; it can no longer be cancelled. */
        RUNNING,
    }

    fun remainingMs(nowElapsedMs: Long): Long = (dueElapsedMs - nowElapsedMs).coerceAtLeast(0L)
}

/** How the last timer ended. Honest about what is known: a sent command is not a confirmed shutdown. */
enum class SleepOutcome {
    /** The TV reported that it went off. */
    TURNED_OFF,

    /** The power command was sent but the TV did not report back. */
    SENT,

    /** The TV was already off: nothing was sent. */
    ALREADY_OFF,

    /** The TV could not be reached: nothing was sent. */
    UNREACHABLE,

    /** The TV did not say whether it is on, so no toggle was sent. */
    UNKNOWN_STATE,

    /** The command could not be written. */
    FAILED,

    /** The app was stopped while it was switching the TV off; whether it happened is unknown. */
    INTERRUPTED,

    /** The deadline passed while the phone was off or the alarm was lost: no late shutdown is attempted. */
    MISSED,

    /** The TV was removed from the app before the timer ran out. */
    TV_REMOVED,
}

@Serializable
data class SleepTimerResult(
    val outcome: SleepOutcome,
    val tvName: String,
    val atWallMs: Long,
)

/** What the repository holds: the running timer, if any, and how the previous one ended. */
data class SleepTimerState(
    val active: SleepTimer? = null,
    val last: SleepTimerResult? = null,
)

object SleepTimerLimits {
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 720
    const val DEFAULT_MINUTES = 30
    const val EXTEND_MINUTES = 15
    val PRESETS: List<Int> = listOf(15, 30, 45, 60, 90, 120)

    fun clamp(minutes: Int): Int = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
}
