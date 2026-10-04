package io.github.tomerar.freetvremote.timer

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.text.format.DateFormat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.tomerar.freetvremote.MainActivity
import io.github.tomerar.freetvremote.R
import java.util.Date

/** The countdown notification (the system draws the ticking clock, so no process has to stay alive) and the result. */
class SleepTimerNotifier(
    private val context: Context,
    private val wallClockMs: () -> Long = System::currentTimeMillis,
    private val elapsedRealtimeMs: () -> Long = SystemClock::elapsedRealtime,
) : SleepTimerNotifications {
    private val manager = NotificationManagerCompat.from(context)

    init {
        val system = context.getSystemService(NotificationManager::class.java)
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_COUNTDOWN,
                context.getString(R.string.timer_channel_countdown),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        system.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RESULT,
                context.getString(R.string.timer_channel_result),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    override fun showCountdown(timer: SleepTimer) {
        val remaining = timer.remainingMs(elapsedRealtimeMs())
        val endsAt = wallClockMs() + remaining
        val time = DateFormat.getTimeFormat(context).format(Date(endsAt))
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_COUNTDOWN)
                .setSmallIcon(R.drawable.ic_power)
                .setContentTitle(context.getString(R.string.timer_notification_title, timer.tvName))
                .setContentText(context.getString(R.string.timer_notification_text, time))
                .setWhen(endsAt)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setContentIntent(openApp())
                .addAction(
                    0,
                    context.getString(R.string.timer_extend, SleepTimerLimits.EXTEND_MINUTES),
                    action(SleepTimerReceiver.ACTION_EXTEND, timer.revision),
                ).addAction(0, context.getString(R.string.timer_cancel), action(SleepTimerReceiver.ACTION_CANCEL, timer.revision))
                .build()
        post(COUNTDOWN_ID, notification)
    }

    /** The user has not switched this app's notifications off (the countdown can be seen outside the app). */
    fun enabled(): Boolean = allowed() && manager.areNotificationsEnabled()

    override fun clearCountdown() {
        manager.cancel(COUNTDOWN_ID)
    }

    override fun showResult(result: SleepTimerResult) {
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_RESULT)
                .setSmallIcon(R.drawable.ic_power)
                .setContentTitle(context.getString(R.string.timer_result_title))
                .setContentText(context.getString(resultText(result.outcome), result.tvName))
                .setAutoCancel(true)
                .setContentIntent(openApp())
                .build()
        post(RESULT_ID, notification)
    }

    /** The notification shown while the app is switching the TV off (a foreground service must show one). */
    fun shutdownNotification(): Notification =
        NotificationCompat
            .Builder(context, CHANNEL_COUNTDOWN)
            .setSmallIcon(R.drawable.ic_power)
            .setContentTitle(context.getString(R.string.timer_shutting_down))
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    // Without the notification permission the system silently drops it; the timer itself works either way.
    @SuppressLint("MissingPermission")
    private fun post(id: Int, notification: Notification) {
        if (enabled()) manager.notify(id, notification)
    }

    private fun allowed(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun action(action: String, revision: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            SleepTimerReceiver.intent(context, action, revision),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    companion object {
        const val CHANNEL_COUNTDOWN = "sleep_timer"
        const val CHANNEL_RESULT = "sleep_timer_result"
        const val COUNTDOWN_ID = 4101
        const val RESULT_ID = 4102
        const val SHUTDOWN_ID = 4103

        fun resultText(outcome: SleepOutcome): Int =
            when (outcome) {
                SleepOutcome.TURNED_OFF -> R.string.timer_result_turned_off
                SleepOutcome.SENT -> R.string.timer_result_sent
                SleepOutcome.ALREADY_OFF -> R.string.timer_result_already_off
                SleepOutcome.UNREACHABLE -> R.string.timer_result_unreachable
                SleepOutcome.UNKNOWN_STATE -> R.string.timer_result_unknown_state
                SleepOutcome.FAILED -> R.string.timer_result_failed
                SleepOutcome.INTERRUPTED -> R.string.timer_result_interrupted
                SleepOutcome.MISSED -> R.string.timer_result_missed
                SleepOutcome.TV_REMOVED -> R.string.timer_result_tv_removed
            }
    }
}
