package io.github.tomerar.freetvremote

import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/**
 * Keeps the last crash (or unexpected background error) as a small text file on the phone so a user can share it
 * from Settings when something goes wrong. Nothing is sent anywhere: the file never leaves the device unless the
 * user chooses to share it.
 */
class CrashReporter(
    private val file: File,
    private val header: () -> String,
) {
    fun record(thread: String, error: Throwable, fatal: Boolean) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val text =
            buildString {
                appendLine(header())
                appendLine("Time: ${Instant.now()}")
                appendLine("Kind: ${if (fatal) "crash" else "background error (app kept running)"}")
                appendLine("Thread: $thread")
                appendLine()
                append(trace)
            }
        file.writeText(text.take(MAX_CHARS))
    }

    fun read(): String? = file.takeIf { it.isFile }?.readText()?.takeIf { it.isNotBlank() }

    fun clear() {
        file.delete()
    }

    /** Records uncaught exceptions of any thread, then lets Android handle them as usual. */
    fun installAsDefaultHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(thread.name, error, fatal = true) }
            previous?.uncaughtException(thread, error)
        }
    }

    companion object {
        const val MAX_CHARS = 20_000

        fun deviceHeader(): String =
            "Free TV Remote ${BuildConfig.VERSION_NAME} (${BuildConfig.GIT_COMMIT})\n" +
                "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}"
    }
}
