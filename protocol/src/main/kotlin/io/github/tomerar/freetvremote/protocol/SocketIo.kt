package io.github.tomerar.freetvremote.protocol

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs blocking socket [block] on the IO dispatcher. Blocking socket reads do
 * not react to thread interruption, so on cancellation the [socket] is closed
 * to unblock the reader immediately.
 */
internal suspend fun <T> blockingIo(socket: Closeable, block: () -> T): T =
    coroutineScope {
        val finished = AtomicBoolean(false)
        val watcher =
            launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    awaitCancellation()
                } finally {
                    if (!finished.get()) socket.closeOffThread()
                }
            }
        try {
            withContext(Dispatchers.IO) { block() }
        } finally {
            finished.set(true)
            watcher.cancel()
        }
    }

/**
 * Closes the socket on a short-lived background thread. Closing a TLS socket writes a close alert, and Android
 * throws `NetworkOnMainThreadException` for that on the main thread, so callers that may run on the main thread
 * (view-model scopes, UI callbacks) must never close a connected socket directly.
 */
internal fun Closeable.closeOffThread() {
    val target = this
    try {
        Thread({ runCatching { target.close() } }, "socket-close").apply { isDaemon = true }.start()
    } catch (_: OutOfMemoryError) {
        runCatching { target.close() } // cannot start a thread: closing here beats leaking the connection
    }
}
