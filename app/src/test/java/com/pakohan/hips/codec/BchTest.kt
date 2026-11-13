package com.pakohan.hips.codec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class BchTest {
    @Test
    fun `encodes like the Python reference`() {
        for ((data, word, _) in vectors()) {
            assertEquals(word, Bch.encode(data))
        }
    }

    @Test
    fun `corrects the damaged words of the Python reference`() {
        for ((data, word, damaged) in vectors()) {
            val decoded = Bch.decode(damaged)
            assertEquals(data, decoded?.data)
            assertEquals(word.indices.count { word[it] != damaged[it] }, decoded?.correctedBits)
        }
    }

    @Test
    fun `corrects every pattern of up to five wrong bits`() {
        val random = Random(1)
        repeat(500) {
            val data = List(Bch.DATA_BITS) { random.nextInt(2) }
            val wrong = (0 until Bch.WORD_BITS).shuffled(random).take(random.nextInt(Bch.CORRECTABLE + 1)).toSet()
            val received = Bch.encode(data).mapIndexed { index, bit -> if (index in wrong) bit xor 1 else bit }
            assertEquals(data, Bch.decode(received)?.data)
        }
    }

    @Test
    fun `never returns the sent data as corrected when more than five bits are wrong`() {
        val random = Random(2)
        var rejected = 0
        repeat(500) {
            val data = List(Bch.DATA_BITS) { random.nextInt(2) }
            val wrong = (0 until Bch.WORD_BITS).shuffled(random).take(6 + random.nextInt(4)).toSet()
            val decoded = Bch.decode(Bch.encode(data).mapIndexed { index, bit -> if (index in wrong) bit xor 1 else bit })
            if (decoded == null) rejected += 1 else assertNotEquals(data, decoded.data)
        }
        assert(rejected > 375) { "only $rejected of 500 damaged words were rejected" }
    }

    @Test
    fun `rejects a word with six wrong bits next to a codeword`() {
        val word = Bch.encode(List(Bch.DATA_BITS) { 0 }).toMutableList()
        for (index in 0 until 6) word[index] = 1
        assertNull(Bch.decode(word))
    }

    private fun vectors(): List<Triple<List<Int>, List<Int>, List<Int>>> =
        resourceLines("codec/bch.txt").map { line ->
            val (data, word, damaged) = line.split(" ").map(::bits)
            Triple(data, word, damaged)
        }
}

internal fun bits(text: String): List<Int> = text.map { it - '0' }

internal fun resourceLines(name: String): List<String> =
    checkNotNull(BchTest::class.java.classLoader?.getResource(name)) { "missing test resource $name" }
        .readText()
        .lines()
        .filter { it.isNotEmpty() }
