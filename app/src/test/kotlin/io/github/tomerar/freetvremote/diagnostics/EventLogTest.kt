package io.github.tomerar.freetvremote.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.util.concurrent.Executor

class EventLogTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val inline = Executor { it.run() }
    private val dir get() = File(tmp.root, "logs")

    private fun log(maxBytesPerFile: Long = EventLog.DEFAULT_MAX_BYTES_PER_FILE) =
        EventLog(dir, maxBytesPerFile, inline, clock = { Instant.parse("2026-10-03T18:00:00Z") })

    @Test
    fun `lines are stored with time, level and tag`() {
        val log = log()
        log.log("Pairing", "connecting")
        assertEquals("2026-10-03T18:00:00Z I Pairing: connecting\n", log.full())
    }

    @Test
    fun `an empty log reports empty and clear removes everything`() {
        val log = log()
        assertTrue(log.isEmpty())
        log.log("A", "x")
        assertFalse(log.isEmpty())
        log.clear()
        assertTrue(log.isEmpty())
        assertEquals("", log.full())
    }

    @Test
    fun `recent returns only the newest lines within the limit and never half a line`() {
        val log = log()
        repeat(2_000) { log.log("T", "line number $it with some padding to take space") }
        val recent = log.recent(maxBytes = 4_000)
        assertTrue(recent.length <= 4_000)
        assertTrue(recent.lines().filter { it.isNotBlank() }.all { it.contains(" T: line number ") })
        assertTrue(recent.contains("line number 1999 "))
        assertFalse(recent.contains("line number 0 "))
    }

    @Test
    fun `the log is rotated so it never grows beyond twice the file limit`() {
        val log = log(maxBytesPerFile = 1_000)
        repeat(500) { log.log("T", "entry $it ".padEnd(40, '.')) }
        assertTrue("size ${log.sizeBytes()}", log.sizeBytes() <= 2_000)
        val full = log.full()
        assertTrue(full.contains("entry 499 "))
        assertFalse(full.contains("entry 0 "))
        // Older lines come first.
        assertTrue(full.indexOf("entry 450 ") < full.indexOf("entry 499 "))
    }

    @Test
    fun `recent continues into the older file when the current one is short`() {
        val log = log(maxBytesPerFile = 3_000)
        repeat(100) { log.log("T", "entry $it ".padEnd(40, '.')) }
        val recent = log.recent(maxBytes = 100 * 1024)
        assertEquals(log.full(), recent)
    }

    @Test
    fun `export writes the header followed by the whole log`() {
        val log = log()
        log.log("T", "one")
        val target = File(tmp.root, "out/log.txt")
        log.exportTo(target, "HEADER")
        assertEquals("HEADER\n\n2026-10-03T18:00:00Z I T: one\n", target.readText())
    }

    @Test
    fun `the default capacity is 10 MB in two files`() {
        assertEquals(5L * 1024 * 1024, EventLog.DEFAULT_MAX_BYTES_PER_FILE)
    }

    @Test
    fun `host addresses are masked to the network but names are kept`() {
        assertEquals("192.168.1.x", maskHost("192.168.1.20"))
        assertEquals("tv.local", maskHost("tv.local"))
        assertEquals("not-an-ip", maskHost("not-an-ip"))
    }
}
