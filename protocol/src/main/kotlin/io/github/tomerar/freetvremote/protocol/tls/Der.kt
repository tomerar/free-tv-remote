package io.github.tomerar.freetvremote.protocol.tls

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Minimal DER writer, just enough to build a self-signed X.509 certificate. */
internal object Der {
    private const val TAG_INTEGER = 0x02
    private const val TAG_BIT_STRING = 0x03
    private const val TAG_NULL = 0x05
    private const val TAG_OID = 0x06
    private const val TAG_UTF8 = 0x0C
    private const val TAG_UTC_TIME = 0x17
    private const val TAG_GENERALIZED_TIME = 0x18
    private const val TAG_SEQUENCE = 0x30
    private const val TAG_SET = 0x31
    private const val TAG_CONTEXT_0 = 0xA0
    private const val LONG_FORM = 0x80
    private const val SHORT_FORM_MAX = 0x7F
    private const val OID_FIRST_BASE = 40
    private const val BASE128_MASK = 0x7F
    private const val BASE128_SHIFT = 7
    private const val UTC_TIME_LAST_YEAR = 2049

    fun tlv(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        writeLength(out, content.size)
        out.write(content)
        return out.toByteArray()
    }

    private fun writeLength(out: ByteArrayOutputStream, length: Int) {
        if (length <= SHORT_FORM_MAX) {
            out.write(length)
            return
        }
        val bytes = BigInteger.valueOf(length.toLong()).toByteArray().dropWhile { it == 0.toByte() }
        out.write(LONG_FORM or bytes.size)
        out.write(bytes.toByteArray())
    }

    private fun concat(parts: Array<out ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        parts.forEach { out.write(it) }
        return out.toByteArray()
    }

    fun sequence(vararg parts: ByteArray): ByteArray = tlv(TAG_SEQUENCE, concat(parts))

    fun set(vararg parts: ByteArray): ByteArray = tlv(TAG_SET, concat(parts))

    fun explicit0(content: ByteArray): ByteArray = tlv(TAG_CONTEXT_0, content)

    fun integer(value: BigInteger): ByteArray = tlv(TAG_INTEGER, value.toByteArray())

    fun nullValue(): ByteArray = tlv(TAG_NULL, ByteArray(0))

    fun utf8(value: String): ByteArray = tlv(TAG_UTF8, value.toByteArray(Charsets.UTF_8))

    fun bitString(content: ByteArray): ByteArray = tlv(TAG_BIT_STRING, byteArrayOf(0) + content)

    fun oid(dotted: String): ByteArray {
        val arcs = dotted.split('.').map { it.toLong() }
        val out = ByteArrayOutputStream()
        out.write((arcs[0] * OID_FIRST_BASE + arcs[1]).toInt())
        for (arc in arcs.drop(2)) {
            val groups = ArrayList<Int>()
            var v = arc
            do {
                groups.add((v and BASE128_MASK.toLong()).toInt())
                v = v shr BASE128_SHIFT
            } while (v > 0)
            for (i in groups.indices.reversed()) {
                out.write(if (i == 0) groups[i] else groups[i] or LONG_FORM)
            }
        }
        return tlv(TAG_OID, out.toByteArray())
    }

    fun time(date: Date): ByteArray {
        val calendar =
            java.util.Calendar
                .getInstance(TimeZone.getTimeZone("UTC"))
                .apply { time = date }
        val useUtcTime = calendar.get(java.util.Calendar.YEAR) <= UTC_TIME_LAST_YEAR
        val pattern = if (useUtcTime) "yyMMddHHmmss'Z'" else "yyyyMMddHHmmss'Z'"
        val format = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        return tlv(if (useUtcTime) TAG_UTC_TIME else TAG_GENERALIZED_TIME, format.format(date).toByteArray())
    }
}
