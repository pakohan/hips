package com.pakohan.hips.scan

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** A greyscale photo, row by row, 0 = black. */
class GrayImage(val width: Int, val height: Int, val pixels: ByteArray) {
    init {
        require(pixels.size == width * height) { "expected ${width * height} pixels, got ${pixels.size}" }
    }
}

/** A character found in the photo. [right] and [bottom] are exclusive. */
class Symbol(var left: Int, var top: Int, var right: Int, var bottom: Int) {
    var baseline: Float = 0f
        internal set

    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/** What the network gets for one symbol: a 64 x 64 crop (0 = paper, 1 = ink) and two size features. */
class GlyphCrop(val pixels: FloatArray, val relativeHeight: Float, val relativeBottom: Float)

/** A photo split into lines of symbols in reading order. */
class SegmentedPage internal constructor(
    private val darkness: Plane,
    val xHeight: Float,
    val lines: List<List<Symbol>>,
) {
    /**
     * The crop of [symbol], scaled so that the x-height of the page becomes [CROP_X_HEIGHT] pixels
     * rather than to the symbol's own box: stroke widths stay comparable between crops.
     */
    fun crop(symbol: Symbol): GlyphCrop {
        val scale = CROP_X_HEIGHT / xHeight
        val left = max(0, symbol.left - 1)
        val top = max(0, symbol.top - 1)
        val right = min(darkness.width, symbol.right + 1)
        val bottom = min(darkness.height, symbol.bottom + 1)
        val patch = Plane(right - left, bottom - top)
        for (y in top until bottom) {
            darkness.data.copyInto(patch.data, (y - top) * patch.width, y * darkness.width + left, y * darkness.width + right)
        }
        val width = Math.rint(patch.width * scale.toDouble()).toInt().coerceIn(1, CROP_SIZE)
        val height = Math.rint(patch.height * scale.toDouble()).toInt().coerceIn(1, CROP_SIZE)
        val resized = if (scale < 1) ImageOps.resizeArea(patch, width, height) else ImageOps.resizeCubic(patch, width, height)
        val pixels = FloatArray(CROP_SIZE * CROP_SIZE)
        val offsetX = (CROP_SIZE - width) / 2
        val offsetY = (CROP_SIZE - height) / 2
        for (y in 0 until height) {
            for (x in 0 until width) {
                // The network was trained on crops stored as bytes: same quantisation here.
                val level = (resized[x, y].coerceIn(0f, 1f) * 255f).toInt()
                pixels[(offsetY + y) * CROP_SIZE + offsetX + x] = level / 255f
            }
        }
        return GlyphCrop(pixels, symbol.height / xHeight, (symbol.bottom - symbol.baseline) / xHeight)
    }

    companion object {
        const val CROP_SIZE = 64
        const val CROP_X_HEIGHT = 26f
    }
}

/**
 * Finds the characters of a photographed page: a port of `poc/hips/segment.py`, which is the
 * reference (ADR 0003). No OCR engine: lighting is flattened, ink is thresholded, connected
 * components are traced into lines from left to right.
 */
object Segmenter {
    private const val SEPARATION_THRESHOLD = 0.6f
    private const val MASK_WIDTH = 400
    private const val MASK_EROSION = 21
    private const val MIN_SYMBOLS = 5

    fun segment(image: GrayImage): SegmentedPage {
        val gray = Plane(image.width, image.height, FloatArray(image.pixels.size) { (image.pixels[it].toInt() and 0xFF).toFloat() })
        val darkness = darkness(gray)
        val mask = paperMask(gray)
        val boxes = components(darkness, mask)
        if (boxes.size < MIN_SYMBOLS) return SegmentedPage(darkness, 0f, emptyList())

        val xHeight = xHeight(boxes.map { it.height })
        val minArea = max(2.0, (0.07 * xHeight) * (0.07 * xHeight))
        val symbols = boxes
            .filter { it.area >= minArea && it.height <= 3 * xHeight && it.width <= 4 * xHeight }
            .map { Symbol(it.left, it.top, it.right, it.bottom) }
            .sortedBy { it.left }
        return SegmentedPage(darkness, xHeight, traceLines(symbols, xHeight))
    }

    /** Removes uneven light and scales solid ink to 1. */
    private fun darkness(gray: Plane): Plane {
        val small = ImageOps.resizeArea(gray, max(1, gray.width / 4), max(1, gray.height / 4)).rounded()
        val kernel = max(31, small.width / 20) or 1
        val closed = ImageOps.erode(ImageOps.dilate(small, kernel), kernel)
        val background = ImageOps.resizeLinear(ImageOps.gaussianBlur(closed, kernel / 2.0).rounded(), gray.width, gray.height).rounded()
        val darkness = Plane(gray.width, gray.height)
        val histogram = IntArray(256)
        for (i in darkness.data.indices) {
            val value = 1f - (gray.data[i] / max(background.data[i], 1f)).coerceIn(0f, 1f)
            darkness.data[i] = value
            histogram[(value * 255f).toInt()] += 1
        }
        val threshold = ImageOps.otsu(histogram)
        val ink = FloatArray((threshold + 1..255).sumOf { histogram[it] })
        var count = 0
        for (value in darkness.data) {
            if ((value * 255f).toInt() > threshold) ink[count++] = value
        }
        ink.sort()
        val inkLevel = if (ink.isEmpty()) 1f else ImageOps.percentile(ink, 0.9)
        for (i in darkness.data.indices) darkness.data[i] = (darkness.data[i] / max(inkLevel, 1e-3f)).coerceIn(0f, 1.25f)
        return darkness
    }

    /**
     * Where the sheet of paper is, shrunk a little so its edge and shadow stay outside: the largest
     * region brighter than 60% of the brightest areas. Kept at low resolution.
     */
    private fun paperMask(gray: Plane): PaperMask {
        val height = max(1, Math.rint(gray.height * MASK_WIDTH.toDouble() / gray.width).toInt())
        val small = ImageOps.gaussianBlur(ImageOps.resizeArea(gray, MASK_WIDTH, height).rounded(), 4.0).rounded()
        val limit = 0.6f * ImageOps.percentile(small.data.sortedArray(), 0.95)
        val bright = BooleanArray(small.data.size) { small.data[it] > limit }

        // Largest 8-connected bright region.
        val label = IntArray(bright.size)
        var bestLabel = 0
        var bestArea = 0
        var nextLabel = 0
        val stack = IntArray(bright.size)
        for (start in bright.indices) {
            if (!bright[start] || label[start] != 0) continue
            nextLabel += 1
            var area = 0
            var top = 0
            stack[top++] = start
            label[start] = nextLabel
            while (top > 0) {
                val index = stack[--top]
                area += 1
                val x = index % MASK_WIDTH
                val y = index / MASK_WIDTH
                for (ny in max(0, y - 1)..min(height - 1, y + 1)) {
                    for (nx in max(0, x - 1)..min(MASK_WIDTH - 1, x + 1)) {
                        val neighbour = ny * MASK_WIDTH + nx
                        if (bright[neighbour] && label[neighbour] == 0) {
                            label[neighbour] = nextLabel
                            stack[top++] = neighbour
                        }
                    }
                }
            }
            if (area > bestArea) {
                bestArea = area
                bestLabel = nextLabel
            }
        }
        if (bestLabel == 0) return PaperMask(MASK_WIDTH, height, BooleanArray(bright.size) { true }, gray.width, gray.height)

        // Fill its holes (the text): everything not reachable from the image border without crossing it.
        val outside = BooleanArray(bright.size)
        var top = 0
        fun visit(index: Int) {
            if (!outside[index] && label[index] != bestLabel) {
                outside[index] = true
                stack[top++] = index
            }
        }
        for (x in 0 until MASK_WIDTH) {
            visit(x)
            visit((height - 1) * MASK_WIDTH + x)
        }
        for (y in 0 until height) {
            visit(y * MASK_WIDTH)
            visit(y * MASK_WIDTH + MASK_WIDTH - 1)
        }
        while (top > 0) {
            val index = stack[--top]
            val x = index % MASK_WIDTH
            val y = index / MASK_WIDTH
            if (x > 0) visit(index - 1)
            if (x < MASK_WIDTH - 1) visit(index + 1)
            if (y > 0) visit(index - MASK_WIDTH)
            if (y < height - 1) visit(index + MASK_WIDTH)
        }

        // Erode, counting everything beyond the image border as "not paper".
        val pad = MASK_EROSION / 2
        val padded = Plane(MASK_WIDTH + 2 * pad, height + 2 * pad)
        for (y in 0 until height) {
            for (x in 0 until MASK_WIDTH) {
                if (!outside[y * MASK_WIDTH + x]) padded.data[(y + pad) * padded.width + x + pad] = 1f
            }
        }
        val eroded = ImageOps.erode(padded, MASK_EROSION)
        val paper = BooleanArray(bright.size) { eroded[it % MASK_WIDTH + pad, it / MASK_WIDTH + pad] > 0.5f }
        return PaperMask(MASK_WIDTH, height, paper, gray.width, gray.height)
    }

    private class PaperMask(val width: Int, val height: Int, val paper: BooleanArray, val fullWidth: Int, val fullHeight: Int) {
        /** Row of the mask that covers row [y] of the photo (nearest-neighbour upscaling). */
        fun row(y: Int): Int = min(height - 1, (y.toLong() * height / fullHeight).toInt()) * width

        fun column(x: Int): Int = min(width - 1, (x.toLong() * width / fullWidth).toInt())
    }

    private class Box(var left: Int, var top: Int, var right: Int, var bottom: Int, var area: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    /**
     * Bounding boxes of the 8-connected ink regions on the paper, in the order their first pixel
     * appears. Works on runs of ink per row, so no label image of the photo's size is needed.
     */
    private fun components(darkness: Plane, mask: PaperMask): List<Box> {
        val width = darkness.width
        val columnOfMask = IntArray(width) { mask.column(it) }
        var parent = IntArray(1024)
        var boxes = arrayOfNulls<Box>(1024)
        var runCount = 0

        fun find(run: Int): Int {
            var root = run
            while (parent[root] != root) root = parent[root]
            var node = run
            while (parent[node] != root) {
                val next = parent[node]
                parent[node] = root
                node = next
            }
            return root
        }

        // Runs of the previous and the current row: start, end (exclusive), id.
        var previous = IntArray(3 * (width / 2 + 1))
        var current = IntArray(previous.size)
        var previousCount = 0
        for (y in 0 until darkness.height) {
            val maskRow = mask.row(y)
            val offset = y * width
            var currentCount = 0
            var x = 0
            var scan = 0
            while (x < width) {
                if (darkness.data[offset + x] <= SEPARATION_THRESHOLD || !mask.paper[maskRow + columnOfMask[x]]) {
                    x += 1
                    continue
                }
                val start = x
                while (x < width && darkness.data[offset + x] > SEPARATION_THRESHOLD && mask.paper[maskRow + columnOfMask[x]]) x += 1
                if (runCount == parent.size) {
                    parent = parent.copyOf(2 * runCount)
                    boxes = boxes.copyOf(2 * runCount)
                }
                val id = runCount++
                parent[id] = id
                boxes[id] = Box(start, y, x, y + 1, x - start)
                // Join with every run of the row above that touches this one, diagonals included.
                while (scan < previousCount && previous[3 * scan + 1] < start) scan += 1
                var touching = scan
                while (touching < previousCount && previous[3 * touching] <= x) {
                    val a = find(previous[3 * touching + 2])
                    val b = find(id)
                    if (a != b) {
                        val root = min(a, b)
                        val child = max(a, b)
                        parent[child] = root
                        val into = boxes[root]!!
                        val from = boxes[child]!!
                        into.left = min(into.left, from.left)
                        into.top = min(into.top, from.top)
                        into.right = max(into.right, from.right)
                        into.bottom = max(into.bottom, from.bottom)
                        into.area += from.area
                    }
                    touching += 1
                }
                current[3 * currentCount] = start
                current[3 * currentCount + 1] = x
                current[3 * currentCount + 2] = id
                currentCount += 1
            }
            val swap = previous
            previous = current
            current = swap
            previousCount = currentCount
        }
        return (0 until runCount).filter { parent[it] == it }.map { boxes[it]!! }
    }

    /** Most common height among the letter-sized components. */
    private fun xHeight(heights: List<Int>): Float {
        val sorted = FloatArray(heights.size) { heights[it].toFloat() }.also { it.sort() }
        val limit = 0.35f * ImageOps.percentile(sorted, 0.9)
        val histogram = IntArray(heights.max() + 2)
        for (height in heights) if (height >= limit) histogram[height] += 1
        var best = 0
        var bestCount = -1
        for (height in histogram.indices) {
            val smoothed = histogram[height] + (if (height > 0) histogram[height - 1] else 0) +
                (if (height + 1 < histogram.size) histogram[height + 1] else 0)
            if (smoothed > bestCount) {
                bestCount = smoothed
                best = height
            }
        }
        return best.toFloat()
    }

    private fun traceLines(symbols: List<Symbol>, xHeight: Float): List<List<Symbol>> {
        val (big, small) = symbols.partition { it.height >= 0.7f * xHeight }

        // The running baseline of a line is the median bottom of its last few letters: descenders
        // are a minority, and a short window follows the tilt of the line.
        val members = mutableListOf<MutableList<Symbol>>()
        for (symbol in big) {
            var line = -1
            var distance = Float.MAX_VALUE
            members.forEachIndexed { index, lineMembers ->
                val candidate = abs(symbol.bottom - median(lineMembers.takeLast(5).map { it.bottom.toFloat() }))
                if (candidate < distance) {
                    distance = candidate
                    line = index
                }
            }
            if (line < 0 || distance > 0.9f * xHeight) {
                members += mutableListOf<Symbol>()
                line = members.lastIndex
            }
            members[line] += symbol
        }
        for (lineMembers in members) {
            val bottoms = lineMembers.map { it.bottom.toFloat() }
            lineMembers.forEachIndexed { index, symbol ->
                symbol.baseline = median(bottoms.subList(max(0, index - 4), min(bottoms.size, index + 5)))
            }
        }

        // Dots, commas, hyphens: attach to the line whose nearby letters they sit on or above.
        for (symbol in small) {
            val centreX = (symbol.left + symbol.right) / 2f
            val centreY = (symbol.top + symbol.bottom) / 2f
            var best = -1
            var bestDistance = 1.6f * xHeight
            members.forEachIndexed { index, lineMembers ->
                val neighbour = lineMembers.minBy { abs((it.left + it.right) / 2f - centreX) }
                val distance = abs(centreY - (neighbour.baseline - 0.5f * xHeight))
                if (distance < bestDistance) {
                    best = index
                    bestDistance = distance
                    symbol.baseline = neighbour.baseline
                }
            }
            if (best >= 0) members[best] += symbol
        }

        return members.map { lineMembers ->
            val merged = mutableListOf<Symbol>()
            for (symbol in lineMembers.sortedBy { it.left }) {
                val previous = merged.lastOrNull()
                // Only a dot-sized part joins its neighbour: the hook of a "j" may reach under the
                // letter before it, and two letters must never merge.
                if (previous != null && min(previous.height, symbol.height) < 0.7f * xHeight) {
                    val overlap = min(previous.right, symbol.right) - max(previous.left, symbol.left)
                    if (overlap >= 0.5f * min(previous.width, symbol.width)) {
                        previous.left = min(previous.left, symbol.left)
                        previous.top = min(previous.top, symbol.top)
                        previous.right = max(previous.right, symbol.right)
                        previous.bottom = max(previous.bottom, symbol.bottom)
                        continue
                    }
                }
                merged += symbol
            }
            merged
        }.sortedBy { it.first().baseline }
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2f
    }
}
