package io.github.tomerar.freetvremote.timer

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import io.github.tomerar.freetvremote.MainActivity

/**
 * Wakes the app at the deadline with an *alarm clock* alarm, the kind a clock app uses for its alarms. Android treats
 * it as the user's own wake-up call: it is delivered on time in Doze and when the phone is idle, and phone makers do
 * not hold it back like ordinary alarms. The system shows an alarm icon and the time as the "next alarm" while the
 * timer runs. From Android 12 it needs the "Alarms and reminders" access; without it the timer falls back to an
 * ordinary alarm that may run late (and says so).
 */
class AlarmSleepTimerScheduler(
    private val context: Context,
) : SleepTimerScheduler {
    private val alarms get() = context.getSystemService<AlarmManager>()

    override val canScheduleExact: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms?.canScheduleExactAlarms() == true

    @SuppressLint("MissingPermission") // guarded by canScheduleExact, and SecurityException is handled
    override fun schedule(timer: SleepTimer) {
        val manager = alarms ?: return
        val operation = pendingIntent(context, timer.revision, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        // The deadline may be a little in the past after a reconcile; the alarm then fires at once.
        val remaining = (timer.dueElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        val showIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        if (timer.exact && canScheduleExact) {
            try {
                manager.setAlarmClock(AlarmManager.AlarmClockInfo(System.currentTimeMillis() + remaining, showIntent), operation)
                return
            } catch (_: SecurityException) {
                // The access was withdrawn a moment ago: use the ordinary alarm below.
            }
        }
        manager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + remaining, operation)
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

        internal fun uriFor(revision: Long) = "freetvremote://sleep-timer/$revision".toUri()
    }
}
