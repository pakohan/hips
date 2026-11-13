package com.pakohan.hips.codec

import kotlin.random.Random

/**
 * One of the four glyph variants a carrier letter can take (ADR 0006). A letter carries two bits:
 * vertical strokes thin or thick, horizontal strokes thin or thick.
 *
 * [candidate] is the index of the variant font (`HIPSFlexNN`, ADR 0001).
 */
enum class StrokeVariant(val candidate: Int) {
    THIN_THIN(12),
    THIN_THICK(15),
    THICK_THIN(21),
    THICK_THICK(24),
    ;

    companion object {
        fun of(verticalBit: Int, horizontalBit: Int): StrokeVariant = entries[2 * verticalBit + horizontalBit]
    }
}

/** A character as the reader saw it: its name and, for carrier letters, a score per [StrokeVariant]. */
class ReadSymbol(val char: Char, val scores: FloatArray?)

/** What a page yielded. [message] is null when no frame with a matching checksum was found. */
class Reading(
    val message: ByteArray?,
    val packetSlots: Int,
    val packetsDecoded: Int,
    val correctedBits: Int,
)

/**
 * The print codec (ADR 0006), bit-compatible with `poc/hips/smallcode.py`.
 *
 * 32 carrier letters hold one packet: a [Bch] word whose 36 data bits are a 5-bit sequence number,
 * 27 payload bits and a 4-bit check. A packet never crosses a line and depends on no other line, so
 * a symbol the reader merges or splits costs the packets of that line only. The framed message is
 * cut into packets and repeated down the page.
 */
object PrintCodec {
    /** Lowercase letters without "l" (reads like "I") and "i" (has no horizontal stroke). */
    const val CARRIERS = "abcdefghjkmnopqrstuvwxyz"
    const val LETTERS_PER_PACKET = 32
    const val MAX_PACKETS = 32

    private const val SEQUENCE_BITS = 5
    private const val PAYLOAD_BITS = 27
    private const val CHECK_BITS = 4
    private const val CHECK_POLYNOMIAL = 0b10011

    /** The framed message as 64-bit packets, in sequence order. */
    fun packetsOf(message: ByteArray): List<List<Int>> {
        val frame = MessageFrame.encode(message)
        val padded = frame + List((PAYLOAD_BITS - frame.size % PAYLOAD_BITS) % PAYLOAD_BITS) { 0 }
        val count = padded.size / PAYLOAD_BITS
        require(count <= MAX_PACKETS) { "message needs $count packets, the format allows $MAX_PACKETS" }
        return List(count) { sequence ->
            val head = (SEQUENCE_BITS - 1 downTo 0).map { shift -> (sequence shr shift) and 1 }
            val body = head + padded.subList(sequence * PAYLOAD_BITS, (sequence + 1) * PAYLOAD_BITS)
            Bch.encode(body + check(body))
        }
    }

    /** How many packets the lines of a text have room for. */
    fun packetSlots(lines: List<String>): Int = lines.sumOf { line -> line.count { it in CARRIERS } / LETTERS_PER_PACKET }

    /**
     * Variant per character, line by line; null means the base font. Carrier letters outside a
     * packet get a random variant so the whole text looks equally uneven.
     *
     * @throws IllegalArgumentException when the text has no room for the message once.
     */
    fun embed(lines: List<String>, message: ByteArray, random: Random): List<List<StrokeVariant?>> {
        val packets = packetsOf(message)
        require(packetSlots(lines) >= packets.size) {
            "message needs ${packets.size} packets, the text has room for ${packetSlots(lines)}"
        }
        var nextPacket = 0
        return lines.map { line ->
            val variants = line.map<StrokeVariant?> { char ->
                if (char in CARRIERS) StrokeVariant.entries[random.nextInt(StrokeVariant.entries.size)] else null
            }.toMutableList()
            val carriers = line.indices.filter { line[it] in CARRIERS }
            for (start in 0..carriers.size - LETTERS_PER_PACKET step LETTERS_PER_PACKET) {
                val packet = packets[nextPacket % packets.size]
                nextPacket += 1
                for (j in 0 until LETTERS_PER_PACKET) {
                    variants[carriers[start + j]] = StrokeVariant.of(packet[2 * j], packet[2 * j + 1])
                }
            }
            variants
        }
    }

    fun read(lines: List<List<ReadSymbol>>): Reading {
        val payloads = mutableMapOf<Int, MutableList<List<Int>>>()
        var slots = 0
        var decoded = 0
        var corrected = 0
        for (line in lines) {
            val carriers = line.mapNotNull { symbol -> symbol.scores.takeIf { symbol.char in CARRIERS } }
            for (start in 0..carriers.size - LETTERS_PER_PACKET step LETTERS_PER_PACKET) {
                slots += 1
                val received = carriers.subList(start, start + LETTERS_PER_PACKET).flatMap(::bitsOf)
                val word = Bch.decode(received) ?: continue
                val body = word.data.take(SEQUENCE_BITS + PAYLOAD_BITS)
                if (check(body) != word.data.drop(SEQUENCE_BITS + PAYLOAD_BITS)) continue
                val sequence = body.take(SEQUENCE_BITS).fold(0) { value, bit -> (value shl 1) or bit }
                payloads.getOrPut(sequence) { mutableListOf() } += body.drop(SEQUENCE_BITS)
                decoded += 1
                corrected += word.correctedBits
            }
        }
        val bits = generateSequence(0) { it + 1 }
            .takeWhile { it in payloads }
            .flatMap { sequence -> mostFrequent(payloads.getValue(sequence)) }
            .toList()
        return Reading(MessageFrame.decode(bits), slots, decoded, corrected)
    }

    private fun bitsOf(scores: FloatArray): List<Int> {
        val (thinThin, thinThick, thickThin, thickThick) = scores
        return listOf(
            if (thickThin + thickThick > thinThin + thinThick) 1 else 0,
            if (thinThick + thickThick > thinThin + thickThin) 1 else 0,
        )
    }

    private fun mostFrequent(candidates: List<List<Int>>): List<Int> =
        candidates.maxBy { candidate -> candidates.count { it == candidate } }

    /** 4-bit CRC: catches most packets that BCH "corrects" out of garbage (about 6% of random words). */
    private fun check(bits: List<Int>): List<Int> {
        var register = 0
        for (bit in bits + List(CHECK_BITS) { 0 }) {
            register = (register shl 1) or bit
            if (register and (1 shl CHECK_BITS) != 0) register = register xor CHECK_POLYNOMIAL
        }
        return (CHECK_BITS - 1 downTo 0).map { shift -> (register shr shift) and 1 }
    }
}
