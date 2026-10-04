package io.github.tomerar.freetvremote.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepTimerFormatTest {
    @Test
    fun `countdown shows minutes and seconds, hours only when needed`() {
        assertEquals("00:00", formatCountdown(0))
        assertEquals("00:01", formatCountdown(1))
        assertEquals("00:59", formatCountdown(59_000))
        assertEquals("42:10", formatCountdown(42 * 60_000L + 10_000))
        assertEquals("59:59", formatCountdown(59 * 60_000L + 59_000))
        assertEquals("1:00:00", formatCountdown(3_600_000))
        assertEquals("12:00:00", formatCountdown(12 * 3_600_000L))
    }

    @Test
    fun `countdown rounds up so it never reads zero before the end`() {
        assertEquals("00:01", formatCountdown(400))
        assertEquals("00:00", formatCountdown(-5))
    }

    @Test
    fun `typed minutes are accepted only inside the allowed range`() {
        assertEquals(1, parseMinutes("1"))
        assertEquals(37, parseMinutes("37"))
        assertEquals(720, parseMinutes("720"))
        assertNull(parseMinutes(""))
        assertNull(parseMinutes("0"))
        assertNull(parseMinutes("721"))
        assertNull(parseMinutes("abc"))
    }

    @Test
    fun `steps move to the next multiple of five inside the limits`() {
        assertEquals(35, stepMinutes(30, up = true))
        assertEquals(25, stepMinutes(30, up = false))
        assertEquals(10, stepMinutes(7, up = true))
        assertEquals(5, stepMinutes(7, up = false))
        assertEquals(1, stepMinutes(5, up = false))
        assertEquals(1, stepMinutes(1, up = false))
        assertEquals(720, stepMinutes(720, up = true))
        assertEquals(720, stepMinutes(718, up = true))
    }

    @Test
    fun `minutes split into hours and rest`() {
        assertEquals(0 to 45, hoursAndMinutes(45))
        assertEquals(1 to 30, hoursAndMinutes(90))
        assertEquals(2 to 0, hoursAndMinutes(120))
    }
}
