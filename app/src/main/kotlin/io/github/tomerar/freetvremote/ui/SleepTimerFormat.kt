package io.github.tomerar.freetvremote.ui

import io.github.tomerar.freetvremote.timer.SleepTimerLimits

private const val MS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val MINUTES_PER_HOUR = 60
private const val SECONDS_PER_HOUR = 3_600L
private const val STEP_MINUTES = 5

/** A running countdown as `H:MM:SS`, or `MM:SS` below one hour. Rounded up, so it never shows 0:00 before the end. */
fun formatCountdown(remainingMs: Long): String {
    val total = (remainingMs.coerceAtLeast(0L) + MS_PER_SECOND - 1) / MS_PER_SECOND
    val hours = total / SECONDS_PER_HOUR
    val minutes = total % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = total % SECONDS_PER_MINUTE
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

/** The minutes typed by the user, or `null` when empty or outside the allowed range. */
fun parseMinutes(text: String): Int? =
    text.toIntOrNull()?.takeIf { it in SleepTimerLimits.MIN_MINUTES..SleepTimerLimits.MAX_MINUTES }

/** The next multiple of five above (or below) [minutes], kept inside the allowed range. */
fun stepMinutes(minutes: Int, up: Boolean): Int {
    val stepped = if (up) (minutes / STEP_MINUTES + 1) * STEP_MINUTES else (minutes - 1) / STEP_MINUTES * STEP_MINUTES
    return SleepTimerLimits.clamp(stepped)
}

/** Splits minutes into whole hours and the rest, for "1 h 30 min". */
fun hoursAndMinutes(minutes: Int): Pair<Int, Int> = minutes / MINUTES_PER_HOUR to minutes % MINUTES_PER_HOUR
