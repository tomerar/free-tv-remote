package io.github.tomerar.freetvremote.timer

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.tomerar.freetvremote.FreeTvRemoteApp
import kotlinx.coroutines.launch

/**
 * Receives the deadline alarm, the Cancel / +15 min buttons of the notification, and the system events after which the
 * timer has to be checked again (reboot, app update, exact-alarm access granted or withdrawn). Not exported: only this
 * app's own PendingIntents and the system reach it.
 */
class SleepTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as FreeTvRemoteApp).container
        val manager = container.sleepTimer
        val revision = intent.data?.lastPathSegment?.toLongOrNull()
        when (intent.action) {
            ACTION_FIRE -> {
                val id = revision ?: return
                // A service gives the TV connection the time it needs; a receiver may only run for a few seconds.
                ContextCompat.startForegroundService(context, TvShutdownService.intent(context, id))
            }

            ACTION_CANCEL -> {
                async(container) { if (isCurrent(manager, revision)) manager.cancel() }
            }

            ACTION_EXTEND -> {
                async(container) { if (isCurrent(manager, revision)) manager.extend(SleepTimerLimits.EXTEND_MINUTES) }
            }

            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> {
                async(container) { manager.reconcile() }
            }
        }
    }

    private suspend fun isCurrent(manager: SleepTimerManager, revision: Long?): Boolean =
        revision != null && manager.activeRevision() == revision

    private fun BroadcastReceiver.async(container: io.github.tomerar.freetvremote.AppContainer, block: suspend () -> Unit) {
        val pending = goAsync()
        container.appScope.launch {
            try {
                block()
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "io.github.tomerar.freetvremote.SLEEP_TIMER_FIRE"
        const val ACTION_CANCEL = "io.github.tomerar.freetvremote.SLEEP_TIMER_CANCEL"
        const val ACTION_EXTEND = "io.github.tomerar.freetvremote.SLEEP_TIMER_EXTEND"

        /** An explicit intent whose identity is its action and revision. */
        fun intent(context: Context, action: String, revision: Long): Intent =
            Intent(context, SleepTimerReceiver::class.java)
                .setAction(action)
                .setData(AlarmSleepTimerScheduler.uriFor(revision))
    }
}
