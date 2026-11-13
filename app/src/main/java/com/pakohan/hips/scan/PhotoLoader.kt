package com.pakohan.hips.scan

import android.content.ContentResolver
import android.graphics.ImageDecoder
import android.net.Uri

/** Loads a photo as greyscale, upright according to its EXIF orientation. */
object PhotoLoader {
    private const val ROWS_PER_CHUNK = 64

    fun load(resolver: ContentResolver, uri: Uri): GrayImage = load(ImageDecoder.createSource(resolver, uri))

    fun load(source: ImageDecoder.Source): GrayImage {
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            // getPixels cannot read the hardware bitmaps ImageDecoder returns by default.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        try {
            val width = bitmap.width
            val gray = ByteArray(width * bitmap.height)
            val argb = IntArray(width * ROWS_PER_CHUNK)
            for (top in 0 until bitmap.height step ROWS_PER_CHUNK) {
                val rows = minOf(ROWS_PER_CHUNK, bitmap.height - top)
                bitmap.getPixels(argb, 0, width, 0, top, width, rows)
                for (i in 0 until width * rows) {
                    val pixel = argb[i]
                    // The luma weights OpenCV uses, which the training photos went through.
                    val luma = 299 * (pixel shr 16 and 0xFF) + 587 * (pixel shr 8 and 0xFF) + 114 * (pixel and 0xFF)
                    gray[top * width + i] = ((luma + 500) / 1000).toByte()
                }
            }
            return GrayImage(width, bitmap.height, gray)
        } finally {
            bitmap.recycle()
        }
    }
}
