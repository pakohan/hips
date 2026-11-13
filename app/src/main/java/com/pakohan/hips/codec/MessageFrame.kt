package com.pakohan.hips.codec

/**
 * The message as bits: one length byte, the payload, CRC-16/CCITT-FALSE over both, most significant
 * bit first (ADR 0004). The CRC is what tells a reader that it found a message at all.
 */
object MessageFrame {
    const val MAX_PAYLOAD_BYTES = 255
    private const val OVERHEAD_BYTES = 3

    fun encode(payload: ByteArray): List<Int> {
        require(payload.size <= MAX_PAYLOAD_BYTES) { "payload of ${payload.size} bytes is too long" }
        val body = byteArrayOf(payload.size.toByte()) + payload
        val crc = crc16(body)
        val framed = body + byteArrayOf((crc shr 8).toByte(), crc.toByte())
        return framed.flatMap { byte -> (7 downTo 0).map { shift -> (byte.toInt() shr shift) and 1 } }
    }

    /** The payload, or null when [bits] do not start with a frame whose CRC matches. */
    fun decode(bits: List<Int>): ByteArray? {
        if (bits.size < 8) return null
        val length = byteAt(bits, 0).toInt() and 0xFF
        val frameBytes = length + OVERHEAD_BYTES
        if (bits.size < 8 * frameBytes) return null
        val frame = ByteArray(frameBytes) { byteAt(bits, it) }
        val expected = ((frame[frameBytes - 2].toInt() and 0xFF) shl 8) or (frame[frameBytes - 1].toInt() and 0xFF)
        if (crc16(frame.copyOf(frameBytes - 2)) != expected) return null
        return frame.copyOfRange(1, frameBytes - 2)
    }

    private fun byteAt(bits: List<Int>, index: Int): Byte =
        (0 until 8).fold(0) { value, offset -> (value shl 1) or bits[8 * index + offset] }.toByte()

    private fun crc16(data: ByteArray): Int {
        var crc = 0xFFFF
        for (byte in data) {
            crc = crc xor ((byte.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) ((crc shl 1) xor 0x1021) and 0xFFFF else (crc shl 1) and 0xFFFF
            }
        }
        return crc
    }
}
