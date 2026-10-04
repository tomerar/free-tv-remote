package io.github.tomerar.freetvremote.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.content.getSystemService
import androidx.core.net.toUri

/**
 * Wakes the app at the deadline with an alarm that works while the phone idles (Doze). An exact alarm needs the
 * "alarms and reminders" special access on Android 12 and up; without it the alarm is allowed to run late and the
 * timer says so.
 */
class AlarmSleepTimerScheduler(
    private val context: Context,
) : SleepTimerScheduler {
    private val alarms get() = context.getSystemService<AlarmManager>()

    override val canScheduleExact: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms?.canScheduleExactAlarms() == true

    override fun schedule(timer: SleepTimer) {
        val manager = alarms ?: return
        val operation = pendingIntent(context, timer.revision, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        // The deadline may be a little in the past after a reconcile; the alarm then fires at once.
        val trigger = timer.dueElapsedMs.coerceAtLeast(SystemClock.elapsedRealtime())
        if (timer.exact && canScheduleExact) {
            try {
                manager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, operation)
                return
            } catch (_: SecurityException) {
                // The special access was withdrawn a moment ago: fall back to the approximate alarm below.
            }
        }
        manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, operation)
    }

    override fun cancel(timer: SleepTimer) {
        val manager = alarms ?: return
        pendingIntent(context, timer.revision, PendingIntent.FLAG_NO_CREATE)?.let {
            manager.cancel(it)
            it.cancel()
        }
    }

    companion object {
        /** One alarm per revision: its identity is the `data` URI, never an extra that could be mixed up. */
        internal fun pendingIntent(context: Context, revision: Long, flag: Int): PendingIntent? =
            PendingIntent.getBroadcast(
                context,
                0,
                SleepTimerReceiver.intent(context, SleepTimerReceiver.ACTION_FIRE, revision),
                flag or PendingIntent.FLAG_IMMUTABLE,
            )

        internal fun uriFor(revision: Long): Uri = "freetvremote://sleep-timer/$revision".toUri()
    }
}
