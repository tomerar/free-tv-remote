package io.github.tomerar.freetvremote.ui

import androidx.compose.ui.graphics.Color
import io.github.tomerar.freetvremote.data.AppShortcut
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutStyleTest {
    @Test
    fun `letters are the first letter of each of the first two words`() {
        assertEquals("N", shortcutLetters("Netflix"))
        assertEquals("PV", shortcutLetters("Prime Video"))
        assertEquals("AT", shortcutLetters("Apple TV"))
        assertEquals("AB", shortcutLetters("a b c"))
    }

    @Test
    fun `a trailing plus is kept for a one word name`() {
        assertEquals("D+", shortcutLetters("Disney+"))
        assertEquals("PV", shortcutLetters("Paramount Video+"))
    }

    @Test
    fun `non latin names and odd input give sensible letters`() {
        assertEquals("ק", shortcutLetters("קשת"))
        assertEquals("\uD83C\uDFAC", shortcutLetters("\uD83C\uDFAC movies").take(2))
        assertEquals("?", shortcutLetters("   "))
        assertEquals("?", shortcutLetters(""))
    }

    @Test
    fun `built in shortcuts keep their own distinct colors`() {
        val colors = AppShortcut.defaults.map { shortcutStyle(it).color }
        assertEquals(colors.size, colors.toSet().size)
        assertEquals(Color(0xFFB81D24), shortcutStyle(AppShortcut.defaults.first { it.id == "netflix" }).color)
    }

    @Test
    fun `a custom shortcut gets a stable color from its name`() {
        val a = AppShortcut("custom-1", "My player", "myapp://x")
        val b = AppShortcut("custom-2", "My player", "myapp://y")
        val c = AppShortcut("custom-3", "Something else", "other://z")
        assertEquals(shortcutStyle(a).color, shortcutStyle(b).color)
        assertNotEquals(shortcutStyle(a).color, shortcutStyle(c).color)
    }

    @Test
    fun `text on every color is readable`() {
        (AppShortcut.defaults + AppShortcut("c", "Custom", "x://y")).forEach {
            val style = shortcutStyle(it)
            assertTrue("${it.name}: contrast ${contrast(style.color, style.onColor)}", contrast(style.color, style.onColor) >= 3.0)
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        fun luminance(c: Color): Double {
            fun channel(v: Float): Double = if (v <= 0.03928f) v / 12.92 else Math.pow(((v + 0.055) / 1.055), 2.4)
            return 0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)
        }
        val (hi, lo) = luminance(a).coerceAtLeast(luminance(b)) to luminance(a).coerceAtMost(luminance(b))
        return (hi + 0.05) / (lo + 0.05)
    }
}
