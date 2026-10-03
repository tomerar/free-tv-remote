package io.github.tomerar.freetvremote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CrashReporterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun reporter(file: File = File(tmp.root, "crash.txt")) = CrashReporter(file) { "Free TV Remote test" }

    @Test
    fun `nothing is reported before anything happened`() {
        assertNull(reporter().read())
    }

    @Test
    fun `a recorded error can be read with version, thread and stack trace`() {
        val r = reporter()
        r.record("main", IllegalStateException("boom"), fatal = true)
        val text = r.read()!!
        assertTrue(text.startsWith("Free TV Remote test"))
        assertTrue("Kind: crash" in text)
        assertTrue("Thread: main" in text)
        assertTrue("IllegalStateException: boom" in text)
    }

    @Test
    fun `a background error is labelled as non fatal`() {
        val r = reporter()
        r.record("app-scope", RuntimeException("x"), fatal = false)
        assertTrue("app kept running" in r.read()!!)
    }

    @Test
    fun `the report is size limited and can be cleared`() {
        val r = reporter()
        r.record("main", RuntimeException("y".repeat(CrashReporter.MAX_CHARS * 2)), fatal = true)
        assertEquals(CrashReporter.MAX_CHARS, r.read()!!.length)
        r.clear()
        assertNull(r.read())
    }
}
