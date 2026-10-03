package io.github.tomerar.freetvremote.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException

class MessageFramingTest {
    @Test
    fun `short payload gets a single byte prefix`() {
        assertArrayEquals(byteArrayOf(3, 1, 2, 3), MessageFraming.frame(byteArrayOf(1, 2, 3)))
    }

    @Test
    fun `empty payload is just a zero length`() {
        assertArrayEquals(byteArrayOf(0), MessageFraming.frame(ByteArray(0)))
    }

    @Test
    fun `payload of 300 bytes uses a two byte varint`() {
        val framed = MessageFraming.frame(ByteArray(300) { 7 })
        // 300 = 0b1_0010_1100 -> 0xAC 0x02
        assertArrayEquals(byteArrayOf(0xAC.toByte(), 0x02), framed.copyOfRange(0, 2))
        assertArrayEquals(ByteArray(300) { 7 }, MessageFraming.read(ByteArrayInputStream(framed)))
    }

    @Test
    fun `several frames are read back in order and the end is null`() {
        val out = ByteArrayOutputStream()
        MessageFraming.write(out, byteArrayOf(1))
        MessageFraming.write(out, byteArrayOf(2, 2))
        val input = ByteArrayInputStream(out.toByteArray())
        assertArrayEquals(byteArrayOf(1), MessageFraming.read(input))
        assertArrayEquals(byteArrayOf(2, 2), MessageFraming.read(input))
        assertNull(MessageFraming.read(input))
    }

    @Test
    fun `truncated payload is an error`() {
        val input = ByteArrayInputStream(byteArrayOf(5, 1, 2))
        assertThrows(EOFException::class.java) { MessageFraming.read(input) }
    }

    @Test
    fun `truncated length prefix is an error`() {
        val input = ByteArrayInputStream(byteArrayOf(0x80.toByte()))
        assertThrows(EOFException::class.java) { MessageFraming.read(input) }
    }

    @Test
    fun `oversized frames are rejected`() {
        // 2 MiB as varint: 0x80 0x80 0x80 0x01
        val input = ByteArrayInputStream(byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0x01))
        assertThrows(IOException::class.java) { MessageFraming.read(input) }
    }

    @Test
    fun `endless varint is rejected`() {
        val input = ByteArrayInputStream(ByteArray(8) { 0xFF.toByte() })
        assertThrows(IOException::class.java) { MessageFraming.read(input) }
    }
}
