package io.github.tomerar.freetvremote.timer

import android.app.AlarmManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.core.content.getSystemService

/**
 * One line about what the phone is doing that can hold an alarm back: battery saver, idle (Doze) mode, the
 * "ignore battery optimizations" exemption and the app standby bucket. For the diagnostics log; nothing personal.
 */
fun describePowerState(context: Context): String {
    val power = context.getSystemService<PowerManager>()
    val usage = context.getSystemService<UsageStatsManager>()
    val alarms = context.getSystemService<AlarmManager>()
    val parts =
        listOf(
            "battery saver=${power?.isPowerSaveMode}",
            "idle mode=${power?.isDeviceIdleMode}",
            "unrestricted=${power?.isIgnoringBatteryOptimizations(context.packageName)}",
            "standby bucket=${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) usage?.appStandbyBucket else null}",
            "next alarm clock=${alarms?.nextAlarmClock != null}",
        )
    return parts.joinToString(", ")
}
