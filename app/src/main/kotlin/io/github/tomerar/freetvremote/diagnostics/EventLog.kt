package io.github.tomerar.freetvremote.diagnostics

import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * A small rolling log of what the app did (pairing steps, connection changes, errors) kept on the phone so a user
 * can copy or share it when something goes wrong. It never contains pairing codes, keys, typed text or key presses,
 * and nothing is sent anywhere: the log leaves the device only when the user shares it.
 *
 * It lives in two files of at most [maxBytesPerFile] each (default 5 MB, 10 MB in total): when the current file
 * is full it becomes the "older" file and the previous older file is deleted.
 */
class EventLog(
    private val dir: File,
    private val maxBytesPerFile: Long = DEFAULT_MAX_BYTES_PER_FILE,
    private val executor: Executor = Executors.newSingleThreadExecutor { r -> Thread(r, "event-log").apply { isDaemon = true } },
    private val clock: () -> Instant = Instant::now,
) {
    private val current get() = File(dir, "events.log")
    private val older get() = File(dir, "events.old.log")
    private val lock = Any()

    /** Queues a line; writing happens on a background thread so callers (also the main thread) never wait on disk. */
    fun log(tag: String, message: String, level: Char = 'I') {
        val line = format(tag, message, level)
        executor.execute { runCatching { append(line) } }
    }

    /** Writes immediately; for crashes, where the process may die before a queued write runs. */
    fun logBlocking(tag: String, message: String, level: Char = 'E') {
        runCatching { append(format(tag, message, level)) }
    }

    private fun format(tag: String, message: String, level: Char) =
        "${clock().truncatedTo(ChronoUnit.MILLIS)} $level $tag: $message\n"

    private fun append(line: String) {
        synchronized(lock) {
            dir.mkdirs()
            if (current.length() + line.length > maxBytesPerFile) {
                older.delete()
                current.renameTo(older)
            }
            current.appendText(line)
        }
    }

    /** The newest part of the log, at most [maxBytes] bytes, starting at a line boundary. */
    fun recent(maxBytes: Int = COPY_LIMIT_BYTES): String =
        synchronized(lock) {
            val tail = readTail(current, maxBytes)
            val missing = maxBytes - tail.length
            val head = if (missing > MIN_USEFUL_BYTES && older.exists()) readTail(older, missing) else ""
            head + tail
        }

    /** Everything that is stored, oldest first. */
    fun full(): String =
        synchronized(lock) {
            (if (older.exists()) older.readText() else "") +
                (if (current.exists()) current.readText() else "")
        }

    /** Writes the whole log (with [header] on top) to [target] for sharing as a file. */
    fun exportTo(target: File, header: String) {
        synchronized(lock) {
            target.parentFile?.mkdirs()
            target.writeText(header + "\n\n" + full())
        }
    }

    fun sizeBytes(): Long = synchronized(lock) { current.length() + (if (older.exists()) older.length() else 0L) }

    fun isEmpty(): Boolean = sizeBytes() == 0L

    fun clear() {
        synchronized(lock) {
            current.delete()
            older.delete()
        }
    }

    private fun readTail(file: File, maxBytes: Int): String {
        if (!file.isFile || maxBytes <= 0) return ""
        RandomAccessFile(file, "r").use { f ->
            val length = f.length()
            val start = (length - maxBytes).coerceAtLeast(0)
            f.seek(start)
            val bytes = ByteArray((length - start).toInt())
            f.readFully(bytes)
            val text = String(bytes, Charsets.UTF_8)
            // Do not start in the middle of a line (or in the middle of a multi-byte character).
            return if (start > 0) text.substringAfter('\n', "") else text
        }
    }

    companion object {
        const val DEFAULT_MAX_BYTES_PER_FILE: Long = 5L * 1024 * 1024
        const val COPY_LIMIT_BYTES: Int = 100 * 1024
        private const val MIN_USEFUL_BYTES = 1024
    }
}

/** `192.168.1.20` becomes `192.168.1.x`: the network stays visible, the exact device does not. Names stay as they are. */
fun maskHost(host: String): String {
    val parts = host.split('.')
    return if (parts.size == 4 && parts.all { p -> p.isNotEmpty() && p.all(Char::isDigit) }) {
        parts.take(3).joinToString(".") + ".x"
    } else {
        host
    }
}
