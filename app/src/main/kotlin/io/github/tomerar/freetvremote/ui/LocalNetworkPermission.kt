package io.github.tomerar.freetvremote.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Android 17 (API 37) protects access to the local network behind a runtime permission for apps
 * targeting it. On older releases nothing is needed.
 */
object LocalNetworkPermission {
    const val PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"
    private const val FIRST_API_WITH_PERMISSION = 37

    val isRequired: Boolean get() = Build.VERSION.SDK_INT >= FIRST_API_WITH_PERMISSION

    fun isGranted(context: Context): Boolean =
        !isRequired || ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED
}
