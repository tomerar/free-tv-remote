package io.github.tomerar.freetvremote.discovery

/**
 * Bookkeeping for one discovery scan, free of Android types so it can be unit tested.
 *
 * Network discovery events arrive in awkward orders: a service can be lost while its resolution is
 * still pending, or lost and found again under the same name. Every `found` event therefore gets a
 * *token*; a resolution result is applied only if its token is still the current one for that
 * service name and the scan is open. Late results of lost, superseded or closed scans are dropped.
 */
internal class ServiceBook(
    private val publish: (List<DiscoveredTv>) -> Unit,
) {
    private val tvs = LinkedHashMap<String, DiscoveredTv>()
    private val tokens = HashMap<String, Long>()
    private var sequence = 0L
    private var closed = false

    /** The service was announced: it is live and a resolution is wanted. Returns the token for that resolution. */
    @Synchronized
    fun found(name: String): Long {
        val token = ++sequence
        if (!closed) tokens[name] = token
        return token
    }

    /** The service disappeared: forget it and invalidate any resolution still in flight. */
    @Synchronized
    fun lost(name: String) {
        tokens.remove(name)
        if (tvs.remove(name) != null) publishLocked()
    }

    /** Like [lost], but only when [token] is still the current one (used by per-service callbacks). */
    @Synchronized
    fun lostIfCurrent(name: String, token: Long) {
        if (isCurrentLocked(name, token)) lost(name)
    }

    @Synchronized
    fun isCurrent(name: String, token: Long): Boolean = isCurrentLocked(name, token)

    /** Applies a resolution result. Returns `false` (and changes nothing) if it is stale. */
    @Synchronized
    fun resolved(name: String, token: Long, tv: DiscoveredTv): Boolean {
        if (!isCurrentLocked(name, token)) return false
        tvs[name] = tv
        publishLocked()
        return true
    }

    /** Ends the scan: nothing can be published afterwards. */
    @Synchronized
    fun close() {
        closed = true
        tokens.clear()
        tvs.clear()
    }

    private fun isCurrentLocked(name: String, token: Long) = !closed && tokens[name] == token

    private fun publishLocked() {
        publish(tvs.values.sortedBy { it.name.lowercase() })
    }
}

/**
 * Android's legacy `resolveService` (API 26-33) allows a single resolution at a time, so requests are
 * queued. Requests whose service was lost or superseded while waiting are skipped, a failing or throwing
 * resolution never stalls the queue, and results go through [ServiceBook] so stale ones are ignored.
 */
internal class LegacyResolveQueue<H>(
    private val book: ServiceBook,
    private val engine: Engine<H>,
) {
    fun interface Engine<H> {
        /** Starts resolving [handle]; must eventually call exactly one of the [Callback] methods. */
        fun resolve(handle: H, callback: Callback)
    }

    interface Callback {
        fun onResolved(tv: DiscoveredTv)

        fun onFailed()
    }

    private class Request<H>(
        val name: String,
        val token: Long,
        val handle: H,
    )

    private val queue = ArrayDeque<Request<H>>()
    private var busy = false
    private var closed = false

    fun enqueue(name: String, handle: H) {
        val token = book.found(name)
        synchronized(this) {
            if (closed) return
            queue.addLast(Request(name, token, handle))
        }
        pump()
    }

    fun lost(name: String) = book.lost(name)

    fun close() {
        synchronized(this) {
            closed = true
            queue.clear()
        }
        book.close()
    }

    private fun pump() {
        val request =
            synchronized(this) {
                if (busy || closed) return
                var next: Request<H>? = null
                while (next == null) {
                    val candidate = queue.removeFirstOrNull() ?: return
                    // Lost or re-announced while waiting in the queue: do not even resolve it.
                    if (book.isCurrent(candidate.name, candidate.token)) next = candidate
                }
                busy = true
                next
            }
        var finished = false

        fun finish() {
            val first =
                synchronized(this) {
                    val wasFirst = !finished
                    finished = true
                    if (wasFirst) busy = false
                    wasFirst
                }
            if (first) pump()
        }
        try {
            engine.resolve(
                request.handle,
                object : Callback {
                    override fun onResolved(tv: DiscoveredTv) {
                        book.resolved(request.name, request.token, tv)
                        finish()
                    }

                    override fun onFailed() = finish()
                },
            )
        } catch (e: RuntimeException) {
            finish() // e.g. the platform refused the request; move on to the next service
        }
    }
}
