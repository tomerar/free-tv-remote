package io.github.tomerar.freetvremote.remote

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeyGesturesTest {
    private class Recorder : KeySender {
        val events = mutableListOf<String>()

        override suspend fun tap(code: Int) {
            events += "tap$code"
        }

        override suspend fun holdStart(code: Int) {
            events += "start$code"
        }

        override suspend fun holdEnd(code: Int) {
            events += "end$code"
        }
    }

    private val timing = GestureTiming(repeatDelayMs = 400, repeatIntervalMs = 100, longPressMs = 500)

    private fun TestScope.gestures(recorder: Recorder) = KeyGestures(this, recorder, timing)

    @Test
    fun `quick tap on a repeat key sends exactly one key`() = runTest {
        val r = Recorder()
        val g = gestures(r)
        g.down(19, KeyBehavior.REPEAT)
        runCurrent()
        advanceTimeBy(100)
        g.up(19, KeyBehavior.REPEAT)
        advanceTimeBy(2_000)
        assertEquals(listOf("tap19"), r.events)
    }

    @Test
    fun `holding a repeat key repeats after the delay at the interval`() = runTest {
        val r = Recorder()
        val g = gestures(r)
        g.down(20, KeyBehavior.REPEAT)
        runCurrent()
        advanceTimeBy(400)
        runCurrent() // first repeat at t=400
        advanceTimeBy(300)
        runCurrent() // t=500, 600, 700
        g.up(20, KeyBehavior.REPEAT)
        advanceTimeBy(1_000)
        assertEquals(listOf("tap20", "tap20", "tap20", "tap20", "tap20"), r.events)
    }

    @Test
    fun `short press of a tap-or-long key taps on release`() = runTest {
        val r = Recorder()
        val g = gestures(r)
        g.down(23, KeyBehavior.TAP_OR_LONG)
        advanceTimeBy(200)
        g.up(23, KeyBehavior.TAP_OR_LONG)
        runCurrent()
        advanceTimeBy(2_000)
        assertEquals(listOf("tap23"), r.events)
    }

    @Test
    fun `long press of a tap-or-long key starts and ends a hold`() = runTest {
        val r = Recorder()
        val g = gestures(r)
        g.down(23, KeyBehavior.TAP_OR_LONG)
        advanceTimeBy(501)
        runCurrent()
        assertEquals(listOf("start23"), r.events)
        g.up(23, KeyBehavior.TAP_OR_LONG)
        runCurrent()
        assertEquals(listOf("start23", "end23"), r.events)
    }

    @Test
    fun `releaseAll ends a hold in flight and stops repeats`() = runTest {
        val r = Recorder()
        val g = gestures(r)
        g.down(23, KeyBehavior.TAP_OR_LONG)
        g.down(19, KeyBehavior.REPEAT)
        advanceTimeBy(1_000)
        runCurrent()
        val before = r.events.count { it == "tap19" }
        g.releaseAll()
        runCurrent()
        advanceTimeBy(2_000)
        assertEquals(before, r.events.count { it == "tap19" })
        assertEquals(1, r.events.count { it == "end23" })
    }

    @Test
    fun `a duplicate down for a pressed key is ignored`() = runTest {
        val r = Recorder()
        val g = gestures(r)
        g.down(19, KeyBehavior.REPEAT)
        g.down(19, KeyBehavior.REPEAT)
        runCurrent()
        g.up(19, KeyBehavior.REPEAT)
        advanceTimeBy(1_000)
        assertEquals(listOf("tap19"), r.events)
    }
}
