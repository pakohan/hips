package com.pakohan.hips.scan

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The pages of a PDF as images for the reader. A page is drawn, not parsed: the reader then sees a
 * PDF the same way it sees a photo, and no PDF library is needed.
 */
class PdfPages(resolver: ContentResolver, uri: Uri) : AutoCloseable {
    private val descriptor = resolver.openFileDescriptor(uri, "r") ?: throw IOException("cannot open $uri")
    private val renderer = try {
        PdfRenderer(descriptor)
    } catch (e: IOException) {
        descriptor.close()
        throw e
    } catch (e: SecurityException) {
        // What PdfRenderer throws for a password-protected file.
        descriptor.close()
        throw IOException(e.message, e)
    }

    val count: Int get() = renderer.pageCount

    fun page(index: Int): GrayImage =
        renderer.openPage(index).use { page ->
            // 14 pt text gets the x-height the network knows best; a larger sheet is drawn smaller rather than huge.
            val scale = min(PIXELS_PER_POINT, MAX_SIDE / max(page.width, page.height))
            val bitmap = Bitmap.createBitmap((page.width * scale).roundToInt(), (page.height * scale).roundToInt(), Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.WHITE) // pages are transparent where nothing is drawn
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                PhotoLoader.gray(bitmap)
            } finally {
                bitmap.recycle()
            }
        }

    override fun close() {
        renderer.close()
        descriptor.close()
    }

    private companion object {
        /** 14 pt type has an x-height of 7.2 pt; this makes it 38 px, as in a photo that fills the frame with the page. */
        const val PIXELS_PER_POINT = 5.3f
        const val MAX_SIDE = 4800f
    }
}
