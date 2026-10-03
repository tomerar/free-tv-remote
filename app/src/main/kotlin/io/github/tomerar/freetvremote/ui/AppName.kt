package io.github.tomerar.freetvremote.ui

import androidx.annotation.StringRes
import io.github.tomerar.freetvremote.protocol.remote.TvState

/** How the foreground app of the TV is shown: its own name if the TV sent one, else a known name, else the raw id. */
sealed interface AppName {
    /** The TV's own readable name, e.g. "Netflix". */
    data class Label(
        val text: String,
    ) : AppName

    /** One of the apps we know by package id. */
    data class Known(
        @StringRes val resId: Int,
    ) : AppName

    /** Neither: only the package id is available. Shown in the details, never in the compact view. */
    data class Package(
        val id: String,
    ) : AppName
}

fun appNameOf(tv: TvState): AppName? {
    val id = tv.currentApp?.takeIf { it.isNotBlank() }
    tv.currentAppLabel?.takeIf { it.isNotBlank() }?.let { return AppName.Label(it) }
    if (id == null) return null
    return friendlyAppName(id)?.let { AppName.Known(it) } ?: AppName.Package(id)
}

/** "TCL Smart TV" style name from the vendor and model the TV reports; skips what is missing or repeated. */
fun tvModelName(tv: TvState): String? {
    val vendor = tv.deviceVendor?.trim().orEmpty()
    val model = tv.deviceModel?.trim().orEmpty()
    return when {
        vendor.isEmpty() && model.isEmpty() -> null
        vendor.isEmpty() -> model
        model.isEmpty() || model.startsWith(vendor, ignoreCase = true) -> if (model.isEmpty()) vendor else model
        else -> "$vendor $model"
    }
}

/** Volume as 0..1, or `null` when the TV did not report both values. */
fun volumeFraction(tv: TvState): Float? {
    val level = tv.volumeLevel ?: return null
    val max = tv.volumeMax?.takeIf { it > 0 } ?: return null
    return (level.toFloat() / max).coerceIn(0f, 1f)
}

/** Whole minutes between two instants, never negative. */
fun connectedMinutes(sinceMs: Long, nowMs: Long): Long = ((nowMs - sinceMs) / MS_PER_MINUTE).coerceAtLeast(0)

private const val MS_PER_MINUTE = 60_000L
