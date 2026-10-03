package io.github.tomerar.freetvremote.remote

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeyboardDraftControllerTest {
    private val sent = mutableListOf<String>()
    private var connected = true
    private var result: suspend (String) -> Boolean = { true }

    private fun kotlinx.coroutines.test.TestScope.controller(timeoutMs: Long = 5_000) =
        KeyboardDraftController(
            scope = this,
            isConnected = { connected },
            sendText = { text ->
                sent += text
                result(text)
            },
            sendTimeoutMs = timeoutMs,
        )

    @Test
    fun `successful send clears the draft and reports sent`() =
        runTest {
            val c = controller()
            c.onDraftChange("hello")
            c.send()
            runCurrent()
            assertEquals(listOf("hello"), sent)
            assertEquals("", c.draft.value)
            assertEquals(KeyboardStatus.Sent, c.status.value)
        }

    @Test
    fun `hebrew and emoji text is delivered unchanged`() =
        runTest {
            val c = controller()
            val text = "שלום עולם 👋🏽 test"
            c.onDraftChange(text)
            c.send()
            runCurrent()
            assertEquals(listOf(text), sent)
        }

    @Test
    fun `sending while disconnected keeps the draft, does not touch the transport and says why`() =
        runTest {
            connected = false
            val c = controller()
            c.onDraftChange("search query")
            c.send()
            runCurrent()
            assertEquals(emptyList<String>(), sent)
            assertEquals("search query", c.draft.value)
            assertEquals(KeyboardStatus.Failed(KeyboardStatus.Failed.Reason.NOT_CONNECTED), c.status.value)
        }

    @Test
    fun `transport failure keeps the draft and a retry can succeed`() =
        runTest {
            result = { false }
            val c = controller()
            c.onDraftChange("abc")
            c.send()
            runCurrent()
            assertEquals("abc", c.draft.value)
            assertEquals(KeyboardStatus.Failed(KeyboardStatus.Failed.Reason.SEND_FAILED), c.status.value)

            result = { true }
            c.send()
            runCurrent()
            assertEquals(listOf("abc", "abc"), sent)
            assertEquals("", c.draft.value)
            assertEquals(KeyboardStatus.Sent, c.status.value)
        }

    @Test
    fun `repeated taps while sending produce a single submission`() =
        runTest {
            val gate = CompletableDeferred<Boolean>()
            result = { gate.await() }
            val c = controller()
            c.onDraftChange("once")
            c.send()
            c.send()
            c.send()
            runCurrent()
            assertEquals(KeyboardStatus.Sending, c.status.value)
            assertEquals(listOf("once"), sent)
            gate.complete(true)
            runCurrent()
            assertEquals(listOf("once"), sent)
            assertEquals("", c.draft.value)
        }

    @Test
    fun `typing is ignored while a send is in flight so a finishing send cannot clear newer text`() =
        runTest {
            val gate = CompletableDeferred<Boolean>()
            result = { gate.await() }
            val c = controller()
            c.onDraftChange("first")
            c.send()
            runCurrent()
            c.onDraftChange("first and more") // blocked: the field is disabled during sending
            assertEquals("first", c.draft.value)
            gate.complete(true)
            runCurrent()
            assertEquals("", c.draft.value)
            c.onDraftChange("second")
            assertEquals("second", c.draft.value)
            assertEquals(KeyboardStatus.Idle, c.status.value)
        }

    @Test
    fun `a send that never completes times out as a failure and keeps the draft`() =
        runTest {
            result = { kotlinx.coroutines.awaitCancellation() }
            val c = controller(timeoutMs = 5_000)
            c.onDraftChange("stuck")
            c.send()
            runCurrent()
            assertEquals(KeyboardStatus.Sending, c.status.value)
            advanceTimeBy(5_001)
            runCurrent()
            assertEquals(KeyboardStatus.Failed(KeyboardStatus.Failed.Reason.SEND_FAILED), c.status.value)
            assertEquals("stuck", c.draft.value)
        }

    @Test
    fun `after a timed-out submission a new submission is independent`() =
        runTest {
            var first = true
            result = {
                if (first) {
                    first = false
                    kotlinx.coroutines.awaitCancellation()
                } else {
                    true
                }
            }
            val c = controller(timeoutMs = 1_000)
            c.onDraftChange("A")
            c.send()
            advanceTimeBy(1_001)
            runCurrent()
            c.onDraftChange("B")
            c.send()
            runCurrent()
            assertEquals(listOf("A", "B"), sent)
            assertEquals("", c.draft.value)
            assertEquals(KeyboardStatus.Sent, c.status.value)
        }

    @Test
    fun `reopening the sheet clears an old result but keeps the draft, and never interrupts a send`() =
        runTest {
            result = { false }
            val c = controller()
            c.onDraftChange("keep me")
            c.send()
            runCurrent()
            c.onSheetOpened()
            assertEquals(KeyboardStatus.Idle, c.status.value)
            assertEquals("keep me", c.draft.value)

            val gate = CompletableDeferred<Boolean>()
            result = { gate.await() }
            c.send()
            runCurrent()
            c.onSheetOpened() // dismissed and reopened while in flight
            assertEquals(KeyboardStatus.Sending, c.status.value)
            gate.complete(true)
            runCurrent()
            assertEquals(KeyboardStatus.Sent, c.status.value)
        }

    @Test
    fun `empty drafts are never sent`() =
        runTest {
            val c = controller()
            c.send()
            runCurrent()
            assertEquals(emptyList<String>(), sent)
            assertEquals(KeyboardStatus.Idle, c.status.value)
        }
}
