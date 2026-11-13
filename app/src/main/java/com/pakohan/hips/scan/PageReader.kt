package com.pakohan.hips.scan

import com.pakohan.hips.codec.PrintCodec
import com.pakohan.hips.codec.ReadSymbol
import com.pakohan.hips.codec.Reading
import com.pakohan.hips.codec.StrokeVariant

/** Scores glyph crops: per crop one probability per class of [GlyphClasses]. */
fun interface GlyphScorer {
    fun score(crops: List<GlyphCrop>): List<FloatArray>
}

/**
 * The classes of the network, in the order of `poc/hips/text.py`: every carrier letter of the
 * many-variant codebook in each of the 27 candidate variants, then one class per other character.
 */
object GlyphClasses {
    const val VARIANTS = 27
    private const val VARIED = "abcdefghijkmnopqrstuvwxyz"
    private const val OTHERS = "lABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.,;:!?'-()"
    const val COUNT = 25 * VARIANTS + 47

    /** The character with the largest summed probability, and its index among the varied letters or -1. */
    fun bestCharacter(probabilities: FloatArray): Pair<Char, Int> {
        var best = ' '
        var bestIndex = -1
        var bestScore = -1f
        for (letter in VARIED.indices) {
            var sum = 0f
            for (variant in 0 until VARIANTS) sum += probabilities[letter * VARIANTS + variant]
            if (sum > bestScore) {
                bestScore = sum
                best = VARIED[letter]
                bestIndex = letter
            }
        }
        for (other in OTHERS.indices) {
            val score = probabilities[VARIED.length * VARIANTS + other]
            if (score > bestScore) {
                bestScore = score
                best = OTHERS[other]
                bestIndex = -1
            }
        }
        return best to bestIndex
    }

    fun probability(probabilities: FloatArray, letterIndex: Int, variant: StrokeVariant): Float =
        probabilities[letterIndex * VARIANTS + variant.candidate]
}

/** What a photo yielded, with what a person needs to judge a failed attempt. */
class PageReading(val reading: Reading, val lines: Int, val symbols: Int, val xHeight: Float)

/** Photo to message: segmentation, the network, then the print codec (ADR 0006). */
class PageReader(private val scorer: GlyphScorer) {
    /**
     * Reads a photo whatever way up it is stored: a phone held flat over a page records no reliable
     * orientation. Text lying on its side breaks into many short "lines", so the segmentation alone
     * tells upright or upside down from sideways; the network then runs once, and a second time on
     * the half-turned photo only when the first reading holds no message.
     */
    fun read(image: GrayImage): PageReading {
        var quarterTurns = 0
        var page = Segmenter.segment(image)
        Segmenter.segment(image.rotated(1)).let { sideways ->
            if (symbolsPerLine(sideways) > symbolsPerLine(page)) {
                quarterTurns = 1
                page = sideways
            }
        }
        val first = read(page)
        if (first.reading.message != null) return first
        val halfTurned = read(Segmenter.segment(image.rotated(quarterTurns + 2)))
        val better = halfTurned.reading.message != null || halfTurned.reading.packetsDecoded > first.reading.packetsDecoded
        return if (better) halfTurned else first
    }

    private fun symbolsPerLine(page: SegmentedPage): Float =
        if (page.lines.isEmpty()) 0f else page.lines.sumOf { it.size }.toFloat() / page.lines.size

    private fun read(page: SegmentedPage): PageReading {
        val symbols = page.lines.flatten()
        val probabilities = scorer.score(symbols.map(page::crop))

        val characters = CharArray(symbols.size)
        val scores = arrayOfNulls<FloatArray>(symbols.size)
        val pageAverage = FloatArray(StrokeVariant.entries.size)
        var carriers = 0
        for (index in symbols.indices) {
            val (character, letterIndex) = GlyphClasses.bestCharacter(probabilities[index])
            characters[index] = character
            if (character !in PrintCodec.CARRIERS) continue
            val four = FloatArray(StrokeVariant.entries.size) { GlyphClasses.probability(probabilities[index], letterIndex, StrokeVariant.entries[it]) }
            val total = maxOf(four.sum(), 1e-9f)
            for (i in four.indices) {
                four[i] /= total
                pageAverage[i] += four[i]
            }
            scores[index] = four
            carriers += 1
        }
        // A message uses the four variants equally often. A variant favoured across the whole page
        // is favoured by the printer or the camera, so the page average is divided out.
        for (four in scores) {
            if (four != null) for (i in four.indices) four[i] /= maxOf(pageAverage[i] / carriers, 1e-9f)
        }

        var next = 0
        val lines = page.lines.map { line -> line.map { ReadSymbol(characters[next], scores[next]).also { next += 1 } } }
        return PageReading(PrintCodec.read(lines), page.lines.size, symbols.size, page.xHeight)
    }
}
