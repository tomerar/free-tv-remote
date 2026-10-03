package io.github.tomerar.freetvremote.ui

import androidx.compose.ui.graphics.Color
import io.github.tomerar.freetvremote.data.AppShortcut
import java.util.Locale

/** How a shortcut is drawn: a colored circle with one or two letters. No brand logos are used or shipped. */
data class ShortcutStyle(
    val color: Color,
    val onColor: Color,
    val letters: String,
)

private val White = Color.White
private val Dark = Color(0xFF1B1B1B)

/** Familiar colors for the built-in shortcuts (plain colors, not logos), so they can be told apart at a glance. */
private val builtInColors: Map<String, Pair<Color, Color>> =
    mapOf(
        "netflix" to (Color(0xFFB81D24) to White),
        "youtube" to (Color(0xFFC4302B) to White),
        "disney" to (Color(0xFF1F3A93) to White),
        "prime" to (Color(0xFF0B7FB5) to White),
        "spotify" to (Color(0xFF1AA34A) to White),
        "plex" to (Color(0xFFE5A00D) to Dark),
        "appletv" to (Color(0xFF4A4A4F) to White),
        "twitch" to (Color(0xFF7B3FE4) to White),
    )

private const val HUE_RANGE = 360
private const val CUSTOM_SATURATION = 0.55f
private const val CUSTOM_VALUE = 0.62f

fun shortcutStyle(shortcut: AppShortcut): ShortcutStyle {
    val known = builtInColors[shortcut.id]
    if (known != null) return ShortcutStyle(known.first, known.second, shortcutLetters(shortcut.name))
    // A custom shortcut gets a stable color derived from its name.
    val hue = (shortcut.name.hashCode() and Int.MAX_VALUE) % HUE_RANGE
    return ShortcutStyle(Color.hsv(hue.toFloat(), CUSTOM_SATURATION, CUSTOM_VALUE), White, shortcutLetters(shortcut.name))
}

/** One or two letters: the first letter of the name, plus the first letter of a second word or a trailing "+". */
fun shortcutLetters(name: String): String {
    val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return "?"
    val first = firstLetter(words[0])
    val second =
        when {
            words.size > 1 -> firstLetter(words[1])
            words[0].endsWith("+") -> "+"
            else -> ""
        }
    return (first + second).uppercase(Locale.ROOT)
}

private fun firstLetter(word: String): String = String(Character.toChars(word.codePointAt(0)))
