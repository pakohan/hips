package com.pakohan.hips.scan

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Compares the Kotlin segmentation with the Python reference on the same pixels. The fixture comes
 * from `poc/tools_export_segmentation.py`.
 */
class SegmenterTest {
    private val page = Segmenter.segment(ScanFixture.image())
    private val expected = ScanFixture.expectedLines()

    @Test
    fun `finds the x-height, lines and symbols of the Python reference`() {
        assertEquals(ScanFixture.expectedXHeight(), page.xHeight, 0.01f)
        assertEquals(expected.map { it.size }, page.lines.map { it.size })
        var worst = 0
        for ((line, wanted) in page.lines.zip(expected)) {
            for ((symbol, box) in line.zip(wanted)) {
                val found = listOf(symbol.left, symbol.top, symbol.right, symbol.bottom)
                worst = maxOf(worst, found.zip(box.take(4)).maxOf { (a, b) -> abs(a - b.toInt()) })
                assertEquals(box[4], symbol.baseline, 1.01f)
            }
        }
        assert(worst <= 1) { "a symbol box is $worst pixels off the reference" }
    }

    @Test
    fun `cuts crops close to the Python reference`() {
        val reference = ScanFixture.expectedCrops()
        val symbols = page.lines.flatten()
        assertEquals(reference.size, symbols.size)
        var total = 0.0
        var worstCrop = 0.0
        for ((symbol, wanted) in symbols.zip(reference)) {
            val crop = page.crop(symbol)
            val difference = crop.pixels.indices.sumOf { abs(crop.pixels[it] - wanted.pixels[it]).toDouble() } / crop.pixels.size
            total += difference
            worstCrop = maxOf(worstCrop, difference)
            assertEquals(wanted.relativeHeight, crop.relativeHeight, 0.03f)
            assertEquals(wanted.relativeBottom, crop.relativeBottom, 0.03f)
        }
        val mean = total / symbols.size
        // In units of the full ink range: 0.004 is one grey level of 255.
        assert(mean < 0.004) { "crops differ from the reference by $mean on average" }
        assert(worstCrop < 0.02) { "one crop differs from the reference by $worstCrop on average" }
    }
}

internal object ScanFixture {
    fun image(): GrayImage {
        val picture = ImageIO.read(checkNotNull(javaClass.classLoader?.getResource("scan/page.png")))
        val pixels = ByteArray(picture.width * picture.height)
        picture.raster.getDataElements(0, 0, picture.width, picture.height, pixels)
        return GrayImage(picture.width, picture.height, pixels)
    }

    private fun text(): List<String> =
        checkNotNull(javaClass.classLoader?.getResource("scan/page.txt")).readText().lines().filter { it.isNotEmpty() }

    fun expectedXHeight(): Float = text().first().toFloat()

    /** Per line, per symbol: left, top, right, bottom, baseline. */
    fun expectedLines(): List<List<List<Float>>> =
        text().drop(1).map { line -> line.split(" ").map { symbol -> symbol.split(",").map(String::toFloat) } }

    fun expectedCrops(): List<GlyphCrop> {
        val bytes = checkNotNull(javaClass.classLoader?.getResource("scan/crops.bin")).readBytes()
        val record = SegmentedPage.CROP_SIZE * SegmentedPage.CROP_SIZE + 8
        return List(bytes.size / record) { index ->
            val start = index * record
            val pixels = FloatArray(record - 8) { (bytes[start + it].toInt() and 0xFF) / 255f }
            val features = ByteBuffer.wrap(bytes, start + record - 8, 8).order(ByteOrder.LITTLE_ENDIAN)
            GlyphCrop(pixels, features.float, features.float)
        }
    }
}
