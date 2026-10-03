package io.github.tomerar.freetvremote

import io.github.tomerar.freetvremote.diagnostics.EventLog
import java.io.PrintWriter
import java.io.StringWriter

/** Writes crashes and unexpected background errors into the [EventLog], so they show up in Settings > Diagnostics. */
class CrashReporter(
    private val log: EventLog,
) {
    fun record(thread: String, error: Throwable, fatal: Boolean) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString().trimEnd()
        val kind = if (fatal) "CRASH (the app stopped)" else "background error (the app kept running)"
        log.logBlocking("Crash", "$kind in thread $thread\n$trace", level = if (fatal) 'F' else 'E')
    }

    /** Records uncaught exceptions of any thread, then lets Android handle them as usual. */
    fun installAsDefaultHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(thread.name, error, fatal = true) }
            previous?.uncaughtException(thread, error)
        }
    }
}
