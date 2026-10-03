package io.github.tomerar.freetvremote.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Callback ordering of the legacy (API 26-33) resolver, driven by hand: the fake engine records every
 * resolution request and the test decides when (and in which order) each one completes.
 */
class ServiceResolutionTest {
    private class Scan {
        val published = mutableListOf<List<DiscoveredTv>>()
        val book = ServiceBook { published += it }
        val requests = mutableListOf<Pair<String, LegacyResolveQueue.Callback>>()
        var throwOnNext = false
        val queue =
            LegacyResolveQueue<String>(book) { handle, callback ->
                if (throwOnNext) {
                    throwOnNext = false
                    error("platform refused")
                }
                requests += handle to callback
            }

        val names get() = published.lastOrNull()?.map { it.name } ?: emptyList()
    }

    private fun tv(name: String, host: String = "10.0.0.1") = DiscoveredTv(name, host, 6466)

    @Test
    fun `service lost while its resolution is pending is not restored by the late result`() {
        val scan = Scan()
        scan.queue.enqueue("Living", "Living")
        assertEquals(1, scan.requests.size)

        scan.queue.lost("Living")
        scan.requests[0].second.onResolved(tv("Living")) // late callback

        assertEquals("a lost device must stay gone", emptyList<List<DiscoveredTv>>(), scan.published)
    }

    @Test
    fun `a device that was published and then lost disappears and a late duplicate does not bring it back`() {
        val scan = Scan()
        scan.queue.enqueue("Living", "Living")
        scan.requests[0].second.onResolved(tv("Living"))
        assertEquals(listOf("Living"), scan.names)

        scan.queue.lost("Living")
        assertEquals(emptyList<String>(), scan.names)
        scan.requests[0].second.onResolved(tv("Living")) // a duplicated callback
        assertEquals(emptyList<String>(), scan.names)
    }

    @Test
    fun `closing the scan before a callback arrives publishes nothing`() {
        val scan = Scan()
        scan.queue.enqueue("Living", "Living")
        scan.queue.close()
        scan.requests[0].second.onResolved(tv("Living"))
        assertEquals(emptyList<List<DiscoveredTv>>(), scan.published)
    }

    @Test
    fun `nothing is resolved or queued after close`() {
        val scan = Scan()
        scan.queue.close()
        scan.queue.enqueue("Late", "Late")
        assertEquals(0, scan.requests.size)
    }

    @Test
    fun `results of an earlier scan never reach a restarted scan`() {
        val old = Scan()
        old.queue.enqueue("Living", "Living")
        old.queue.close() // scan restarted: the old collection is cancelled

        val fresh = Scan()
        fresh.queue.enqueue("Bedroom", "Bedroom")

        old.requests[0].second.onResolved(tv("Living")) // obsolete callback finally arrives
        fresh.requests[0].second.onResolved(tv("Bedroom"))

        assertEquals(emptyList<List<DiscoveredTv>>(), old.published)
        assertEquals(listOf("Bedroom"), fresh.names)
    }

    @Test
    fun `lost and rediscovered under the same name only accepts the newest resolution`() {
        val scan = Scan()
        scan.queue.enqueue("Living", "Living#1")
        scan.queue.lost("Living")
        scan.queue.enqueue("Living", "Living#2")
        // #1 is still the outstanding platform call; #2 waits behind it.
        assertEquals(1, scan.requests.size)

        scan.requests[0].second.onResolved(tv("Living", host = "10.0.0.1")) // obsolete result of #1
        assertEquals("the obsolete result must not be published", emptyList<List<DiscoveredTv>>(), scan.published)

        assertEquals(2, scan.requests.size) // the queue advanced to #2
        scan.requests[1].second.onResolved(tv("Living", host = "10.0.0.9"))
        assertEquals(listOf("10.0.0.9"), scan.published.last().map { it.host })
    }

    @Test
    fun `an obsolete result cannot overwrite a newer one for the same name`() {
        val scan = Scan()
        val book = scan.book
        val first = book.found("Living")
        val second = book.found("Living") // re-announced
        assertTrue(book.resolved("Living", second, tv("Living", "10.0.0.9")))
        assertFalse(book.resolved("Living", first, tv("Living", "10.0.0.1")))
        assertEquals(
            "10.0.0.9",
            scan.published
                .last()
                .single()
                .host,
        )
    }

    @Test
    fun `a failed resolution is followed by the next queued service`() {
        val scan = Scan()
        scan.queue.enqueue("A", "A")
        scan.queue.enqueue("B", "B")
        assertEquals("only one resolution at a time", 1, scan.requests.size)

        scan.requests[0].second.onFailed()
        assertEquals(2, scan.requests.size)
        scan.requests[1].second.onResolved(tv("B"))
        assertEquals(listOf("B"), scan.names)
    }

    @Test
    fun `the queue keeps one resolution in flight and advances in order`() {
        val scan = Scan()
        listOf("A", "B", "C").forEach { scan.queue.enqueue(it, it) }
        assertEquals(listOf("A"), scan.requests.map { it.first })
        scan.requests[0].second.onResolved(tv("A"))
        assertEquals(listOf("A", "B"), scan.requests.map { it.first })
        scan.requests[1].second.onResolved(tv("B"))
        scan.requests[2].second.onResolved(tv("C"))
        assertEquals(listOf("A", "B", "C"), scan.names)
    }

    @Test
    fun `a service lost while it waits in the queue is never resolved`() {
        val scan = Scan()
        scan.queue.enqueue("A", "A")
        scan.queue.enqueue("B", "B")
        scan.queue.lost("B") // B vanishes while A is being resolved
        scan.requests[0].second.onResolved(tv("A"))
        assertEquals("B must be skipped", listOf("A"), scan.requests.map { it.first })
        assertEquals(listOf("A"), scan.names)
    }

    @Test
    fun `an engine that throws does not stall the queue`() {
        val scan = Scan()
        scan.throwOnNext = true
        scan.queue.enqueue("A", "A") // the platform refuses this request
        scan.queue.enqueue("B", "B")
        assertEquals(listOf("B"), scan.requests.map { it.first })
        scan.requests[0].second.onResolved(tv("B"))
        assertEquals(listOf("B"), scan.names)
    }

    @Test
    fun `a duplicated completion callback is harmless`() {
        val scan = Scan()
        scan.queue.enqueue("A", "A")
        scan.queue.enqueue("B", "B")
        scan.requests[0].second.onFailed()
        scan.requests[0].second.onFailed() // the platform delivering twice must not start extra resolutions
        assertEquals(2, scan.requests.size)
    }

    @Test
    fun `per-service callbacks of the modern API ignore a lost event from an obsolete registration`() {
        val scan = Scan()
        val book = scan.book
        val old = book.found("Living")
        book.resolved("Living", old, tv("Living"))
        book.lost("Living")
        val fresh = book.found("Living")
        book.resolved("Living", fresh, tv("Living", "10.0.0.9"))

        book.lostIfCurrent("Living", old) // the stale registration reports "lost" late
        assertEquals(listOf("Living"), scan.names)
        book.lostIfCurrent("Living", fresh)
        assertEquals(emptyList<String>(), scan.names)
    }
}
