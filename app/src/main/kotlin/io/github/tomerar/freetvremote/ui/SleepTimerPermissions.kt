package io.github.tomerar.freetvremote.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

/** What the sleep timer asks the user for before it starts. The timer still starts when something is refused. */
object SleepTimerPermissions {
    /** Runtime permissions that are not granted yet: notifications (Android 13+) and local network access (Android 17+). */
    fun missing(context: Context): List<String> =
        buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !granted(context, Manifest.permission.POST_NOTIFICATIONS)) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (!LocalNetworkPermission.isGranted(context)) add(LocalNetworkPermission.PERMISSION)
        }

    /** The system page where the user allows exact alarms for this app (Android 12+); `null` where nothing is needed. */
    fun exactAlarmSettings(context: Context): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
        } else {
            null
        }

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
