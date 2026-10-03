package io.github.tomerar.freetvremote.protocol

import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Varint length-prefixed message framing used by both the pairing and the
 * remote channels (the same layout as protobuf's `writeDelimitedTo`).
 */
public object MessageFraming {
    /** Upper bound for a single frame; anything larger is treated as a protocol error. */
    public const val MAX_FRAME_SIZE: Int = 1 shl 20

    private const val MAX_VARINT_BYTES = 5
    private const val VARINT_PAYLOAD_MASK = 0x7F
    private const val VARINT_CONTINUATION = 0x80
    private const val VARINT_SHIFT = 7

    /** Returns [payload] prefixed with its length as a varint. */
    public fun frame(payload: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(payload.size + MAX_VARINT_BYTES)
        write(out, payload)
        return out.toByteArray()
    }

    public fun write(out: OutputStream, payload: ByteArray) {
        require(payload.size <= MAX_FRAME_SIZE) { "Frame too large: ${payload.size}" }
        var value = payload.size
        while (value >= VARINT_CONTINUATION) {
            out.write((value and VARINT_PAYLOAD_MASK) or VARINT_CONTINUATION)
            value = value ushr VARINT_SHIFT
        }
        out.write(value)
        out.write(payload)
    }

    /**
     * Reads one frame. Returns `null` when the stream ended cleanly before the
     * first byte of a frame; throws [IOException] for truncated or oversized frames.
     */
    public fun read(input: InputStream): ByteArray? {
        val length = readVarint(input) ?: return null
        if (length < 0 || length > MAX_FRAME_SIZE) {
            throw IOException("Invalid frame length: $length")
        }
        val buffer = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val n = input.read(buffer, offset, length - offset)
            if (n < 0) throw EOFException("Stream ended inside a frame ($offset/$length bytes)")
            offset += n
        }
        return buffer
    }

    private fun readVarint(input: InputStream): Int? {
        var result = 0
        var shift = 0
        for (i in 0 until MAX_VARINT_BYTES) {
            val b = input.read()
            if (b < 0) {
                if (i == 0) return null
                throw EOFException("Stream ended inside a length prefix")
            }
            result = result or ((b and VARINT_PAYLOAD_MASK) shl shift)
            if (b and VARINT_CONTINUATION == 0) return result
            shift += VARINT_SHIFT
        }
        throw IOException("Length prefix is too long")
    }
}
