package io.github.tomerar.freetvremote.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import io.github.tomerar.freetvremote.data.ThemeMode

private val Teal = Color(0xFF4FD8C4)
private val TealDark = Color(0xFF006B5F)

private val DarkColors =
    darkColorScheme(
        primary = Teal,
        onPrimary = Color(0xFF00382F),
        primaryContainer = Color(0xFF1F4A44),
        onPrimaryContainer = Color(0xFFB4F2E8),
        secondary = Color(0xFF9ECAC3),
        background = Color(0xFF0E1214),
        onBackground = Color(0xFFE2E8E7),
        surface = Color(0xFF0E1214),
        onSurface = Color(0xFFE2E8E7),
        surfaceVariant = Color(0xFF1B2326),
        onSurfaceVariant = Color(0xFFB4C0BE),
        surfaceContainer = Color(0xFF151C1E),
        surfaceContainerHigh = Color(0xFF1B2326),
        surfaceContainerHighest = Color(0xFF232D30),
        outline = Color(0xFF5E6D6B),
        error = Color(0xFFFFB4AB),
        errorContainer = Color(0xFF5C1A15),
        onErrorContainer = Color(0xFFFFDAD6),
    )

private val LightColors =
    lightColorScheme(
        primary = TealDark,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFB4F2E8),
        onPrimaryContainer = Color(0xFF00201C),
        secondary = Color(0xFF486460),
        background = Color(0xFFF6FAF9),
        surface = Color(0xFFF6FAF9),
        surfaceVariant = Color(0xFFDBE5E3),
        surfaceContainer = Color(0xFFEAF0EF),
        surfaceContainerHigh = Color(0xFFE4EBE9),
        surfaceContainerHighest = Color(0xFFDDE5E3),
    )

/**
 * Uses the user's wallpaper colors (Material You) on Android 12+ when [dynamicColor] is on, like Google's own apps,
 * and the app's teal scheme everywhere else.
 */
@Composable
fun FreeTvRemoteTheme(mode: ThemeMode, dynamicColor: Boolean = true, content: @Composable () -> Unit) {
    val dark =
        when (mode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }
    val context = LocalContext.current
    val colors =
        when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            dark -> {
                DarkColors
            }

            else -> {
                LightColors
            }
        }
    MaterialTheme(colorScheme = colors, content = content)
}
