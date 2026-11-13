package com.pakohan.hips.pdf

import com.pakohan.hips.codec.PrintCodec
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class LoremIpsumTest {
    private val supported = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ., "

    @Test
    fun `sentences use only characters the font has and read like sentences`() {
        val sentences = LoremIpsum.sentences(Random(1)).take(200).toList()
        assert(sentences.first().startsWith("Lorem ipsum dolor sit amet"))
        for (sentence in sentences) {
            assert(sentence.all { it in supported }) { sentence }
            assert(sentence.first().isUpperCase() && sentence.endsWith(".")) { sentence }
            assert(!sentence.contains(",.") && !sentence.contains(" ,")) { sentence }
        }
    }

    @Test
    fun `the same seed gives the same text`() {
        assertEquals(
            LoremIpsum.sentences(Random(7)).take(20).toList(),
            LoremIpsum.sentences(Random(7)).take(20).toList(),
        )
    }

    @Test
    fun `placeholder text carries data in most of its letters`() {
        val text = LoremIpsum.sentences(Random(1)).take(50).joinToString(" ").filter { it.isLetter() }
        val carriers = text.count { it in PrintCodec.CARRIERS }
        assert(carriers > 0.7 * text.length) { "$carriers of ${text.length} letters are carriers" }
    }
}
