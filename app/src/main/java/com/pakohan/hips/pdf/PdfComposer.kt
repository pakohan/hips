package com.pakohan.hips.pdf

import android.content.res.AssetManager
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.pakohan.hips.codec.PrintCodec
import com.pakohan.hips.codec.StrokeVariant
import java.io.OutputStream
import kotlin.random.Random

/** Why a text and a message could not become a PDF. */
sealed interface ComposeProblem {
    /** The fonts and the reader only know these characters. */
    data class UnsupportedCharacters(val characters: Set<Char>) : ComposeProblem

    data object MessageTooLong : ComposeProblem

    /** The message needs [packetsNeeded] packets of 32 carrier letters on one line each; the text offers [packetSlots]. */
    data class TextTooShort(val packetsNeeded: Int, val packetSlots: Int) : ComposeProblem
}

sealed interface ComposeResult {
    data class Written(val pages: Int, val packetsNeeded: Int, val packetSlots: Int) : ComposeResult

    data class Refused(val problem: ComposeProblem) : ComposeResult
}

/**
 * Sets a text in the HIPS font and writes it as a PDF whose glyph variants carry a message
 * (ADR 0006). Uses the platform PDF writer: every character is drawn on its own with the font of
 * its variant, at the position the base font gives it.
 */
class PdfComposer(private val assets: AssetManager, private val format: PageFormat = PageFormat()) {
    private val baseFont: Typeface by lazy { font(BASE_CANDIDATE) }
    private val variantFonts: Map<StrokeVariant, Typeface> by lazy { StrokeVariant.entries.associateWith { font(it.candidate) } }

    /** What stops [text] and [message] from becoming a PDF, or null. Cheap enough to call before asking for a file. */
    fun check(text: String, message: ByteArray): ComposeProblem? {
        val unsupported = text.filterNot { it in SUPPORTED || it.isWhitespace() }.toSet()
        if (unsupported.isNotEmpty()) return ComposeProblem.UnsupportedCharacters(unsupported)
        if (message.size > MAX_MESSAGE_BYTES) return ComposeProblem.MessageTooLong
        val packetsNeeded = PrintCodec.packetsOf(message).size
        val packetSlots = PrintCodec.packetSlots(lineTexts(layout(text)))
        return if (packetSlots < packetsNeeded) ComposeProblem.TextTooShort(packetsNeeded, packetSlots) else null
    }

    fun compose(text: String, message: ByteArray, output: OutputStream, random: Random = Random.Default): ComposeResult {
        check(text, message)?.let { return ComposeResult.Refused(it) }
        val lines = layout(text)
        val glyphs = lines.flatten()
        val packetsNeeded = PrintCodec.packetsOf(message).size
        val packetSlots = PrintCodec.packetSlots(lineTexts(lines))
        val variants = PrintCodec.embed(lineTexts(lines), message, random)

        val paint = textPaint()
        val document = PdfDocument()
        try {
            val pages = (glyphs.maxOfOrNull { it.page } ?: 0) + 1
            for (pageIndex in 0 until pages) {
                val info = PdfDocument.PageInfo.Builder(format.width.toInt(), format.height.toInt(), pageIndex + 1).create()
                val page = document.startPage(info)
                lines.forEachIndexed { lineIndex, line ->
                    line.forEachIndexed { index, glyph ->
                        if (glyph.page == pageIndex) {
                            paint.typeface = variants[lineIndex][index]?.let(variantFonts::getValue) ?: baseFont
                            page.canvas.drawText(glyph.char.toString(), glyph.x, glyph.baseline, paint)
                        }
                    }
                }
                document.finishPage(page)
            }
            document.writeTo(output)
            return ComposeResult.Written(pages, packetsNeeded, packetSlots)
        } finally {
            document.close()
        }
    }

    private fun textPaint() =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            textSize = format.textSize
            typeface = baseFont
        }

    private fun layout(text: String): List<List<PlacedGlyph>> {
        val paint = textPaint()
        return TextLayout.lines(TextLayout.layout(text, format) { paint.measureText(it.toString()) })
    }

    private fun lineTexts(lines: List<List<PlacedGlyph>>): List<String> =
        lines.map { line -> String(CharArray(line.size) { line[it].char }) }

    private fun font(candidate: Int): Typeface = Typeface.createFromAsset(assets, "fonts/HIPSFlex%02d.ttf".format(candidate))

    companion object {
        /** The characters the reader's network was trained to name. */
        const val SUPPORTED = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.,;:!?'-()"
        const val MAX_MESSAGE_BYTES = 105
        private const val BASE_CANDIDATE = 0
    }
}
