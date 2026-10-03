package io.github.tomerar.freetvremote.protocol

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CloseOffThreadTest {
    @Test
    fun `the socket is closed on another thread than the caller`() {
        val closedOn = arrayOfNulls<Thread>(1)
        val done = CountDownLatch(1)
        val target =
            Closeable {
                closedOn[0] = Thread.currentThread()
                done.countDown()
            }
        target.closeOffThread()
        assertTrue("close was never called", done.await(5, TimeUnit.SECONDS))
        assertNotEquals(Thread.currentThread(), closedOn[0])
    }

    @Test
    fun `a failing close does not propagate`() {
        val done = CountDownLatch(1)
        Closeable {
            done.countDown()
            error("boom")
        }.closeOffThread()
        assertTrue(done.await(5, TimeUnit.SECONDS))
    }
}
