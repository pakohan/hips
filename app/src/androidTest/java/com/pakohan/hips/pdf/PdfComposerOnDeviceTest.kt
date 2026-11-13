package com.pakohan.hips.pdf

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random

/**
 * Writes a PDF with the platform PDF writer. The file is left in the app's files directory as
 * `composed.pdf`, so the Python reference can decode it:
 * `adb exec-out run-as com.pakohan.hips cat files/composed.pdf > composed.pdf`.
 */
@RunWith(AndroidJUnit4::class)
class PdfComposerOnDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val composer = PdfComposer(context.assets)

    @Test
    fun writesAPdfForATextThatFitsTheMessage() {
        val file = File(context.filesDir, "composed.pdf")
        val result = file.outputStream().use { composer.compose(TEXT, MESSAGE, it, Random(1)) }
        assertTrue("$result", result is ComposeResult.Written)
        result as ComposeResult.Written
        assertEquals(1, result.pages)
        assertTrue(result.packetSlots >= result.packetsNeeded)
        assertTrue(file.length() > 10_000)
    }

    @Test
    fun refusesWhatCannotBeWritten() {
        val sink = ByteArrayOutputStream()
        assertEquals(
            ComposeResult.Refused(ComposeProblem.UnsupportedCharacters(setOf('ü', 'ß', '"'))),
            composer.compose("Grüße \"so\"", MESSAGE, sink),
        )
        assertEquals(ComposeResult.Refused(ComposeProblem.MessageTooLong), composer.compose(TEXT, ByteArray(106), sink))
        val refused = composer.compose("Too short a text.", MESSAGE, sink)
        assertEquals(ComposeResult.Refused(ComposeProblem.TextTooShort(packetsNeeded = 5, packetSlots = 0)), refused)
        assertEquals(0, sink.size())
    }

    private companion object {
        val MESSAGE = "from the app".encodeToByteArray()
        const val TEXT =
            "The harbour at the end of the valley was quiet when the first boats came back that morning. " +
                "Nobody on the quay expected them before noon, and the fog had not yet lifted from the water, " +
                "so the lamps along the pier were still burning. A dog ran ahead of the fishermen, barking at " +
                "the gulls, while the children counted the crates as they were carried up the slope. Every box " +
                "was weighed twice, once by the buyer and once by the crew, because trust is cheap in fine " +
                "weather and expensive in a storm. By the time the sun broke through, the market was full of " +
                "voices, and the price of mackerel had dropped by a third. Old Jakob, who kept the books for " +
                "the cooperative, wrote down each sale in a narrow hand and never looked up from the page."
    }
}
