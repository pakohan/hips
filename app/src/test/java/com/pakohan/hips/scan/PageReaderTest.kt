package com.pakohan.hips.scan

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PageReaderTest {
    /** The network's answers for the fixture page, computed by the Python reference from its own crops. */
    private val referenceProbabilities: List<FloatArray> = run {
        val bytes = checkNotNull(javaClass.classLoader?.getResource("scan/probabilities.bin")).readBytes()
        val floats = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        List(floats.capacity() / GlyphClasses.COUNT) { FloatArray(GlyphClasses.COUNT).also(floats::get) }
    }

    @Test
    fun `reads the message of the fixture page from the reference network's answers`() {
        val reader = PageReader { crops ->
            assertEquals(referenceProbabilities.size, crops.size)
            referenceProbabilities
        }
        val result = reader.read(ScanFixture.image())
        assertEquals("hi", result.reading.message?.decodeToString())
        assertEquals(result.reading.packetSlots, result.reading.packetsDecoded)
        assertEquals(8, result.lines)
        assertEquals(267, result.symbols)
    }

    @Test
    fun `reads a photo that lies on its side or stands on its head`() {
        // The reference answers only fit the upright page; any other view gets answers that name nothing useful.
        val blank = FloatArray(GlyphClasses.COUNT).also { it[GlyphClasses.COUNT - 1] = 1f }
        val reader = PageReader { crops ->
            if (crops.size == referenceProbabilities.size) referenceProbabilities else List(crops.size) { blank }
        }
        for (quarterTurns in 1..3) {
            val result = reader.read(ScanFixture.image().rotated(quarterTurns))
            assertEquals("after $quarterTurns quarter turns", "hi", result.reading.message?.decodeToString())
        }
    }

    @Test
    fun `names the characters like the Python reference`() {
        // Class 0 is "a" in variant 0; the first class after the varied letters is "l".
        val a = FloatArray(GlyphClasses.COUNT).also { it[0] = 0.4f; it[5] = 0.3f; it[25 * 27] = 0.3f }
        assertEquals('a' to 0, GlyphClasses.bestCharacter(a))
        val l = FloatArray(GlyphClasses.COUNT).also { it[25 * 27] = 0.6f; it[0] = 0.4f }
        assertEquals('l' to -1, GlyphClasses.bestCharacter(l))
        val closingBracket = FloatArray(GlyphClasses.COUNT).also { it[GlyphClasses.COUNT - 1] = 1f }
        assertEquals(')' to -1, GlyphClasses.bestCharacter(closingBracket))
    }
}
