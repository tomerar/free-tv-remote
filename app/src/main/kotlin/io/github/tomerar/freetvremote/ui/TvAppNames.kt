package io.github.tomerar.freetvremote.ui

import androidx.annotation.StringRes
import io.github.tomerar.freetvremote.R

/** Readable names for the foreground apps TVs report as package ids. Unknown ids are not shown to the user. */
@StringRes
internal fun friendlyAppName(packageName: String): Int? =
    when (packageName) {
        "com.google.android.apps.tv.launcherx",
        "com.google.android.tvlauncher",
        "com.google.android.leanbacklauncher",
        -> R.string.tv_app_home

        "com.netflix.ninja" -> R.string.tv_app_netflix

        "com.google.android.youtube.tv",
        "com.google.android.youtube.tvunplugged",
        -> R.string.tv_app_youtube

        "com.disney.disneyplus" -> R.string.tv_app_disney

        "com.amazon.amazonvideo.livingroom" -> R.string.tv_app_prime

        else -> null
    }
