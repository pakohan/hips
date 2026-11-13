package com.pakohan.hips.scan

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A single-channel image with float values, row by row. */
internal class Plane(val width: Int, val height: Int, val data: FloatArray = FloatArray(width * height)) {
    operator fun get(x: Int, y: Int): Float = data[y * width + x]

    /** Rounds every value, as OpenCV does when an operation writes back into an 8-bit image. */
    fun rounded(): Plane = apply { for (i in data.indices) data[i] = Math.rint(data[i].toDouble()).toFloat() }
}

/**
 * The few image operations the segmentation needs, written to give the same results as the OpenCV
 * calls in `poc/hips/segment.py`: the network was trained on crops made with those.
 */
internal object ImageOps {
    /** Shrinks by averaging the source area under each target pixel (OpenCV INTER_AREA). */
    fun resizeArea(source: Plane, width: Int, height: Int): Plane {
        if (width > source.width || height > source.height) return resizeLinear(source, width, height)
        val horizontal = Plane(width, source.height)
        val columns = AreaTaps(source.width, width)
        for (y in 0 until source.height) {
            for (x in 0 until width) {
                var sum = 0f
                for (tap in 0 until columns.count[x]) sum += source[columns.start[x] + tap, y] * columns.weight(x, tap)
                horizontal.data[y * width + x] = sum
            }
        }
        val result = Plane(width, height)
        val rows = AreaTaps(source.height, height)
        for (y in 0 until height) {
            for (tap in 0 until rows.count[y]) {
                val weight = rows.weight(y, tap)
                val from = (rows.start[y] + tap) * width
                for (x in 0 until width) result.data[y * width + x] += horizontal.data[from + x] * weight
            }
        }
        return result
    }

    private class AreaTaps(sourceSize: Int, targetSize: Int) {
        private val scale = sourceSize.toDouble() / targetSize
        private val maxTaps = ceil(scale).toInt() + 1
        val start = IntArray(targetSize)
        val count = IntArray(targetSize)
        private val weights = FloatArray(targetSize * maxTaps)

        init {
            for (target in 0 until targetSize) {
                val from = target * scale
                val to = min((target + 1) * scale, sourceSize.toDouble())
                val first = floor(from).toInt()
                val last = min(ceil(to).toInt(), sourceSize) - 1
                start[target] = first
                count[target] = last - first + 1
                for (source in first..last) {
                    val covered = min(to, source + 1.0) - max(from, source.toDouble())
                    weights[target * maxTaps + source - first] = (covered / (to - from)).toFloat()
                }
            }
        }

        fun weight(target: Int, tap: Int): Float = weights[target * maxTaps + tap]
    }

    /** Bilinear interpolation between pixel centres (OpenCV INTER_LINEAR). */
    fun resizeLinear(source: Plane, width: Int, height: Int): Plane {
        val result = Plane(width, height)
        val scaleX = source.width.toFloat() / width
        val scaleY = source.height.toFloat() / height
        for (y in 0 until height) {
            val sourceY = max((y + 0.5f) * scaleY - 0.5f, 0f)
            val y0 = min(sourceY.toInt(), source.height - 1)
            val y1 = min(y0 + 1, source.height - 1)
            val fy = sourceY - y0
            for (x in 0 until width) {
                val sourceX = max((x + 0.5f) * scaleX - 0.5f, 0f)
                val x0 = min(sourceX.toInt(), source.width - 1)
                val x1 = min(x0 + 1, source.width - 1)
                val fx = sourceX - x0
                val top = source[x0, y0] * (1 - fx) + source[x1, y0] * fx
                val bottom = source[x0, y1] * (1 - fx) + source[x1, y1] * fx
                result.data[y * width + x] = top * (1 - fy) + bottom * fy
            }
        }
        return result
    }

    /** Bicubic interpolation with a = -0.75 and replicated borders (OpenCV INTER_CUBIC). */
    fun resizeCubic(source: Plane, width: Int, height: Int): Plane {
        val horizontal = Plane(width, source.height)
        cubicAxis(source.width, width) { x, indices, weights ->
            for (y in 0 until source.height) {
                var sum = 0f
                for (tap in 0 until 4) sum += source[indices[tap], y] * weights[tap]
                horizontal.data[y * width + x] = sum
            }
        }
        val result = Plane(width, height)
        cubicAxis(source.height, height) { y, indices, weights ->
            for (x in 0 until width) {
                var sum = 0f
                for (tap in 0 until 4) sum += horizontal[x, indices[tap]] * weights[tap]
                result.data[y * width + x] = sum
            }
        }
        return result
    }

    private inline fun cubicAxis(sourceSize: Int, targetSize: Int, body: (Int, IntArray, FloatArray) -> Unit) {
        val scale = sourceSize.toFloat() / targetSize
        val indices = IntArray(4)
        val weights = FloatArray(4)
        for (target in 0 until targetSize) {
            val position = (target + 0.5f) * scale - 0.5f
            val base = floor(position).toInt()
            val t = position - base
            weights[0] = ((CUBIC_A * (t + 1) - 5 * CUBIC_A) * (t + 1) + 8 * CUBIC_A) * (t + 1) - 4 * CUBIC_A
            weights[1] = ((CUBIC_A + 2) * t - (CUBIC_A + 3)) * t * t + 1
            weights[2] = ((CUBIC_A + 2) * (1 - t) - (CUBIC_A + 3)) * (1 - t) * (1 - t) + 1
            weights[3] = 1f - weights[0] - weights[1] - weights[2]
            for (tap in 0 until 4) indices[tap] = (base - 1 + tap).coerceIn(0, sourceSize - 1)
            body(target, indices, weights)
        }
    }

    private const val CUBIC_A = -0.75f

    /** Gaussian blur with the kernel size OpenCV derives for 8-bit images, borders mirrored. */
    fun gaussianBlur(source: Plane, sigma: Double): Plane {
        val size = (sigma * 6 + 1).roundToInt() or 1
        val radius = size / 2
        val kernel = FloatArray(size) { exp(-((it - radius) * (it - radius)) / (2 * sigma * sigma)).toFloat() }
        val total = kernel.sum()
        for (i in kernel.indices) kernel[i] /= total

        fun mirror(index: Int, limit: Int): Int {
            if (limit == 1) return 0
            var i = index
            while (i < 0 || i >= limit) i = if (i < 0) -i else 2 * (limit - 1) - i
            return i
        }

        val horizontal = Plane(source.width, source.height)
        for (y in 0 until source.height) {
            for (x in 0 until source.width) {
                var sum = 0f
                for (tap in 0 until size) sum += source[mirror(x + tap - radius, source.width), y] * kernel[tap]
                horizontal.data[y * source.width + x] = sum
            }
        }
        val result = Plane(source.width, source.height)
        for (y in 0 until source.height) {
            for (tap in 0 until size) {
                val from = mirror(y + tap - radius, source.height) * source.width
                val weight = kernel[tap]
                for (x in 0 until source.width) result.data[y * source.width + x] += horizontal.data[from + x] * weight
            }
        }
        return result
    }

    /** Largest value under an elliptical window of [size] x [size] pixels; pixels outside the image do not count. */
    fun dilate(source: Plane, size: Int): Plane = morph(source, size, takeLarger = true)

    /** Smallest value under an elliptical window of [size] x [size] pixels; pixels outside the image do not count. */
    fun erode(source: Plane, size: Int): Plane = morph(source, size, takeLarger = false)

    private fun morph(source: Plane, size: Int, takeLarger: Boolean): Plane {
        val radius = size / 2
        // Half-width of the ellipse in every kernel row, as cv2.getStructuringElement(MORPH_ELLIPSE) builds it.
        val halfWidths = IntArray(size) { row ->
            val dy = row - radius
            if (radius == 0) 0 else Math.rint(radius * sqrt((radius * radius - dy * dy).toDouble()) / radius).toInt()
        }
        val neutral = if (takeLarger) Float.NEGATIVE_INFINITY else Float.POSITIVE_INFINITY
        val width = source.width
        val rowWise = HashMap<Int, FloatArray>()
        for (halfWidth in halfWidths.distinct()) {
            val plane = FloatArray(source.data.size)
            for (y in 0 until source.height) {
                val offset = y * width
                for (x in 0 until width) {
                    var best = neutral
                    for (i in max(0, x - halfWidth)..min(width - 1, x + halfWidth)) {
                        val value = source.data[offset + i]
                        if (if (takeLarger) value > best else value < best) best = value
                    }
                    plane[offset + x] = best
                }
            }
            rowWise[halfWidth] = plane
        }
        val result = Plane(width, source.height)
        result.data.fill(neutral)
        for (y in 0 until source.height) {
            for (row in 0 until size) {
                val from = y + row - radius
                if (from < 0 || from >= source.height) continue
                val plane = rowWise.getValue(halfWidths[row])
                for (x in 0 until width) {
                    val value = plane[from * width + x]
                    val index = y * width + x
                    if (if (takeLarger) value > result.data[index] else value < result.data[index]) result.data[index] = value
                }
            }
        }
        return result
    }

    /** Otsu's threshold of a 256-bin histogram: values above it are foreground. */
    fun otsu(histogram: IntArray): Int {
        val total = histogram.sum().toDouble()
        if (total == 0.0) return 0
        val mean = histogram.indices.sumOf { it * histogram[it].toDouble() } / total
        var weight1 = 0.0
        var mean1 = 0.0
        var best = 0.0
        var threshold = 0
        for (level in histogram.indices) {
            val share = histogram[level] / total
            mean1 *= weight1
            weight1 += share
            val weight2 = 1.0 - weight1
            if (min(weight1, weight2) < 1.2e-7 || max(weight1, weight2) > 1.0 - 1.2e-7) continue
            mean1 = (mean1 + level * share) / weight1
            val mean2 = (mean - weight1 * mean1) / weight2
            val between = weight1 * weight2 * (mean1 - mean2) * (mean1 - mean2)
            if (between > best) {
                best = between
                threshold = level
            }
        }
        return threshold
    }

    /** Percentile with linear interpolation between neighbours, as numpy computes it. [sorted] must be ascending. */
    fun percentile(sorted: FloatArray, share: Double): Float {
        val position = (sorted.size - 1) * share
        val below = floor(position).toInt()
        val above = min(below + 1, sorted.size - 1)
        return (sorted[below] + (sorted[above] - sorted[below]) * (position - below)).toFloat()
    }
}
