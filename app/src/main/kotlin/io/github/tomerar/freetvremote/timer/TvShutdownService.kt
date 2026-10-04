package io.github.tomerar.freetvremote.timer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import io.github.tomerar.freetvremote.FreeTvRemoteApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Runs the shutdown when the alarm fires. It is a short foreground service (Android's type for a brief, user-requested
 * task that has to finish even though the app is in the background), starts at once, holds the CPU for at most half a
 * minute and ends as soon as the TV was dealt with.
 */
class TvShutdownService : Service() {
    private var job: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (applicationContext as FreeTvRemoteApp).container
        val revision = intent?.getLongExtra(EXTRA_REVISION, NO_REVISION) ?: NO_REVISION
        // Must be called within seconds of the start: do it before anything else.
        ServiceCompat.startForeground(
            this,
            SleepTimerNotifier.SHUTDOWN_ID,
            container.sleepTimerNotifier.shutdownNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE else 0,
        )
        if (revision == NO_REVISION || job?.isActive == true) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        wakeLock =
            getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FreeTvRemote:sleep-timer")
                .apply { acquire(WAKE_LOCK_MS) }
        job =
            container.appScope.launch {
                try {
                    withTimeoutOrNull(WORK_LIMIT_MS) { container.sleepTimer.onFire(revision) }
                } finally {
                    release()
                    stopSelf(startId)
                }
            }
        return START_NOT_STICKY
    }

    /** The platform's limit for a short service was reached: stop at once (the timer reconciles on the next start). */
    override fun onTimeout(startId: Int) {
        release()
        stopSelf(startId)
    }

    override fun onTimeout(startId: Int, fgsType: Int) = onTimeout(startId)

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun release() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        private const val EXTRA_REVISION = "revision"
        private const val NO_REVISION = -1L
        private const val WORK_LIMIT_MS = 30_000L
        private const val WAKE_LOCK_MS = 40_000L

        fun intent(context: Context, revision: Long): Intent =
            Intent(context, TvShutdownService::class.java).putExtra(EXTRA_REVISION, revision)
    }
}
