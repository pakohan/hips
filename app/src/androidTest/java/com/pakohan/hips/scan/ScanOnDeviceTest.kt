package com.pakohan.hips.scan

import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pakohan.hips.pdf.ComposeResult
import com.pakohan.hips.pdf.PdfComposer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * The parts of the scan that only exist on a device: the platform image decoder and LiteRT.
 * Fixtures are those of the JVM tests (`app/src/test/resources/scan`, from `poc/tools_export_segmentation.py`).
 */
@RunWith(AndroidJUnit4::class)
class ScanOnDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val fixtures = instrumentation.context.assets

    /**
     * LiteRT 2.3.2 dies with SIGILL on an emulator that advertises SME2 without supporting it, which
     * is every arm64 emulator on an Apple M4 host. The crash takes the whole test run down, so these
     * tests are skipped there; they are meant for a real phone.
     */
    @Before
    fun skipWhereLiteRtCrashes() {
        val emulator = Build.HARDWARE == "ranchu"
        val advertisesSme = File("/proc/cpuinfo").readText().contains(" sme2")
        assumeFalse("LiteRT crashes on emulators that advertise SME2", emulator && advertisesSme)
    }

    @Test
    fun theNetworkAnswersLikeThePythonReference() {
        val crops = fixtures.open("scan/crops.bin").use { it.readBytes() }
        val record = SegmentedPage.CROP_SIZE * SegmentedPage.CROP_SIZE + 8
        val inputs = List(crops.size / record) { index ->
            val start = index * record
            val features = ByteBuffer.wrap(crops, start + record - 8, 8).order(ByteOrder.LITTLE_ENDIAN)
            GlyphCrop(FloatArray(record - 8) { (crops[start + it].toInt() and 0xFF) / 255f }, features.float, features.float)
        }
        val expected = ByteBuffer.wrap(fixtures.open("scan/probabilities.bin").use { it.readBytes() })
            .order(ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()

        val actual = GlyphClassifier(instrumentation.targetContext).use { it.score(inputs) }

        assertEquals(expected.capacity(), actual.size * GlyphClasses.COUNT)
        var largest = 0f
        for ((index, probabilities) in actual.withIndex()) {
            for (cls in probabilities.indices) {
                largest = maxOf(largest, abs(probabilities[cls] - expected.get(index * GlyphClasses.COUNT + cls)))
            }
        }
        assert(largest < 1e-3f) { "a probability differs from the reference by $largest" }
    }

    /** The app's own loop without paper: write a PDF, draw its page as the PDF scan does, read it. */
    @Test
    fun aPdfWrittenByTheAppBecomesItsMessage() {
        val context = instrumentation.targetContext
        val composer = PdfComposer(context.assets)
        val message = "read back from a PDF".encodeToByteArray()
        val file = File(context.cacheDir, "loop.pdf")
        val written = file.outputStream().use { composer.compose(composer.placeholderText(message), message, it) }
        assertTrue("$written", written is ComposeResult.Written)

        val result = GlyphClassifier(context).use { classifier ->
            PdfPages(context.contentResolver, Uri.fromFile(file)).use { pages ->
                assertEquals(1, pages.count)
                PageReader(classifier).readUpright(pages.page(0))
            }
        }
        assertEquals("read back from a PDF", result.reading.message?.decodeToString())
        assertEquals(result.reading.packetSlots, result.reading.packetsDecoded)
    }

    @Test
    fun aPhotoBecomesItsMessage() {
        val image = PhotoLoader.load(ImageDecoder.createSource(fixtures, "scan/page.png"))
        val result = GlyphClassifier(instrumentation.targetContext).use { PageReader(it).read(image) }
        assertEquals("hi", result.reading.message?.decodeToString())
        assertEquals(result.reading.packetSlots, result.reading.packetsDecoded)
    }
}
