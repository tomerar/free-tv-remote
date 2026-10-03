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
                    if (!finished.get()) runCatching { socket.close() }
                }
            }
        try {
            withContext(Dispatchers.IO) { block() }
        } finally {
            finished.set(true)
            watcher.cancel()
        }
    }
