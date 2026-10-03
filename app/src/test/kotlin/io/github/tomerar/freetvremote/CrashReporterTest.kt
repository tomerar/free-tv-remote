package io.github.tomerar.freetvremote

import io.github.tomerar.freetvremote.diagnostics.EventLog
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.Executor

class CrashReporterTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val log get() = EventLog(File(tmp.root, "logs"), executor = Executor { it.run() })

    @Test
    fun `a crash is written into the event log with the stack trace`() {
        val log = log
        CrashReporter(log).record("main", IllegalStateException("boom"), fatal = true)
        val text = log.full()
        assertTrue("CRASH" in text)
        assertTrue("thread main" in text)
        assertTrue("IllegalStateException: boom" in text)
    }

    @Test
    fun `a background error is labelled as one the app survived`() {
        val log = log
        CrashReporter(log).record("app-scope", RuntimeException("x"), fatal = false)
        assertTrue("the app kept running" in log.full())
    }
}
