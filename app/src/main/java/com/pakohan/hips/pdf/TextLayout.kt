package com.pakohan.hips.pdf

/** A character placed on a page: [x] is the left end of its advance, [baseline] its baseline, in points. */
class PlacedGlyph(val char: Char, val x: Float, val baseline: Float, val page: Int, val line: Int)

/** Page geometry in PDF points. The defaults are A4 with the type size the reader was trained on. */
class PageFormat(
    val width: Float = 595f,
    val height: Float = 842f,
    val margin: Float = 50f,
    val textSize: Float = 14f,
    val leading: Float = 1.45f,
)

/**
 * Greedy word wrap with the advances of the base font, as `poc/hips/pdf.py` lays out its pages.
 * All glyph variants share those advances (ADR 0002), so the layout does not depend on the message.
 */
object TextLayout {
    /** [advance] is the advance width of a character at [PageFormat.textSize]. Spaces are not returned. */
    fun layout(text: String, format: PageFormat, advance: (Char) -> Float): List<PlacedGlyph> {
        val glyphs = mutableListOf<PlacedGlyph>()
        val lineHeight = format.textSize * format.leading
        var page = 0
        var line = 0
        var x = format.margin
        var baseline = format.margin + format.textSize

        fun newLine() {
            line += 1
            x = format.margin
            baseline += lineHeight
            if (baseline > format.height - format.margin) {
                page += 1
                baseline = format.margin + format.textSize
            }
        }

        for ((index, paragraph) in text.split('\n').withIndex()) {
            if (index > 0) newLine()
            for (word in paragraph.split(' ', '\t').filter { it.isNotEmpty() }) {
                val wordWidth = word.sumOf { advance(it).toDouble() }.toFloat()
                if (x > format.margin && x + wordWidth > format.width - format.margin) newLine()
                for (char in word) {
                    glyphs += PlacedGlyph(char, x, baseline, page, line)
                    x += advance(char)
                }
                x += advance(' ')
            }
        }
        return glyphs
    }

    /** The characters of every line that has any, in reading order: what the codec calls the lines of the text. */
    fun lines(glyphs: List<PlacedGlyph>): List<List<PlacedGlyph>> = glyphs.groupBy { it.line }.toSortedMap().values.toList()
}
