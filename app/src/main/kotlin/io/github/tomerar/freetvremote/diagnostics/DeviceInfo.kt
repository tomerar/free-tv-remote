package io.github.tomerar.freetvremote.diagnostics

import android.os.Build
import io.github.tomerar.freetvremote.BuildConfig

object DeviceInfo {
    fun header(): String =
        "Free TV Remote ${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_COMMIT}), " +
            "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}"
}
