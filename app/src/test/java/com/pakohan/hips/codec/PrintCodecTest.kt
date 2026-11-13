package com.pakohan.hips.codec

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.random.Random

class PrintCodecTest {
    private val page = resourceLines("codec/page.txt")
    private val message = hex(page.first())
    private val lines = page.drop(1).map { it.substringBefore('\t') }
    private val marks = page.drop(1).map { it.substringAfter('\t') }

    @Test
    fun `frame round trip, and a flipped bit is rejected`() {
        for (payload in listOf(ByteArray(0), "Grüße".toByteArray(), ByteArray(255) { it.toByte() })) {
            val bits = MessageFrame.encode(payload)
            assertArrayEquals(payload, MessageFrame.decode(bits))
            assertArrayEquals(payload, MessageFrame.decode(bits + List(40) { 1 }))
            assertNull(MessageFrame.decode(bits.mapIndexed { index, bit -> if (index == 9) bit xor 1 else bit }))
        }
        assertNull(MessageFrame.decode(emptyList()))
    }

    @Test
    fun `cuts messages into the packets of the Python reference`() {
        for (line in resourceLines("codec/packets.txt")) {
            val fields = line.split(" ")
            val payload = if (fields.first() == "-") ByteArray(0) else hex(fields.first())
            assertEquals(fields.drop(1).map(::bits), PrintCodec.packetsOf(payload))
        }
    }

    @Test
    fun `refuses a message that needs more than 32 packets`() {
        assertThrows(IllegalArgumentException::class.java) { PrintCodec.packetsOf(ByteArray(106)) }
    }

    @Test
    fun `embeds the packet letters like the Python reference and only varies carrier letters`() {
        val embedded = PrintCodec.embed(lines, message, Random(3))
        for ((lineIndex, line) in lines.withIndex()) {
            for ((index, char) in line.withIndex()) {
                val variant = embedded[lineIndex][index]
                when (val mark = marks[lineIndex][index]) {
                    '.' -> assertNull("$char in line $lineIndex", variant)
                    in '0'..'3' -> assertEquals(StrokeVariant.entries[mark - '0'], variant)
                    else -> assert(variant != null) { "carrier $char in line $lineIndex has no variant" }
                }
            }
        }
    }

    @Test
    fun `refuses a text without room for the message`() {
        assertThrows(IllegalArgumentException::class.java) { PrintCodec.embed(lines.take(3), message, Random(3)) }
    }

    @Test
    fun `reads the page the Python reference wrote`() {
        val reading = PrintCodec.read(observe(wrongShare = 0.0, random = Random(4)))
        assertArrayEquals(message, reading.message)
        assertEquals(PrintCodec.packetSlots(lines), reading.packetSlots)
        assertEquals(reading.packetSlots, reading.packetsDecoded)
        assertEquals(0, reading.correctedBits)
    }

    @Test
    fun `reads through misread letters, a lost letter and a ruined line`() {
        val random = Random(5)
        val observed = observe(wrongShare = 0.02, random).toMutableList()
        observed[0] = observed[0].filterIndexed { index, _ -> index != 3 }
        observed[2] = observed[2].map { symbol ->
            ReadSymbol(symbol.char, symbol.scores?.let { FloatArray(4) { random.nextFloat() } })
        }
        val reading = PrintCodec.read(observed)
        assertArrayEquals(message, reading.message)
        assert(reading.packetsDecoded < reading.packetSlots)
    }

    @Test
    fun `finds no message in a page without one`() {
        val random = Random(6)
        val noise = lines.map { line ->
            line.map { char -> ReadSymbol(char, FloatArray(4) { random.nextFloat() }.takeIf { char in PrintCodec.CARRIERS }) }
        }
        assertNull(PrintCodec.read(noise).message)
    }

    /** The reference page as a reader would report it; [wrongShare] of the carrier letters are misread. */
    private fun observe(wrongShare: Double, random: Random): List<List<ReadSymbol>> =
        lines.mapIndexed { lineIndex, line ->
            line.mapIndexed { index, char ->
                val mark = marks[lineIndex][index]
                if (mark == '.') {
                    ReadSymbol(char, null)
                } else {
                    val truth = if (mark in '0'..'3') mark - '0' else mark - 'a'
                    val seen = if (random.nextDouble() < wrongShare) (truth + 1 + random.nextInt(3)) % 4 else truth
                    ReadSymbol(char, FloatArray(4) { if (it == seen) 0.85f else 0.05f })
                }
            }
        }

    private fun hex(text: String): ByteArray = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
