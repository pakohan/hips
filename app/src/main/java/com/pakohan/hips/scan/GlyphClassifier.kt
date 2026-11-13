package com.pakohan.hips.scan

import android.content.Context
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.Environment
import com.google.ai.edge.litert.TensorBuffer

private val environment by lazy { Environment.create() }

/**
 * The glyph network (`assets/glyphnet.tflite`, exported by `poc/11_export_model.py`) on the CPU.
 *
 * Inputs: crops float32 [64, 1, 64, 64] and features float32 [64, 2]; output float32 [64, 722].
 * The batch size is fixed, so the last batch of a page is padded.
 *
 * Create, use and close it on one and the same background thread.
 */
class GlyphClassifier(context: Context) : GlyphScorer, AutoCloseable {
    private val model = CompiledModel.create(context.assets, MODEL_ASSET, CompiledModel.Options(Accelerator.CPU), environment)
    private val inputs = mutableListOf<TensorBuffer>()
    private val outputs = mutableListOf<TensorBuffer>()
    private val cropBatch = FloatArray(BATCH * CROP_PIXELS)
    private val featureBatch = FloatArray(BATCH * FEATURES)

    init {
        try {
            inputs += model.createInputBuffers()
            outputs += model.createOutputBuffers()
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    override fun score(crops: List<GlyphCrop>): List<FloatArray> =
        crops.chunked(BATCH).flatMap { batch ->
            cropBatch.fill(0f)
            featureBatch.fill(0f)
            batch.forEachIndexed { index, crop ->
                crop.pixels.copyInto(cropBatch, index * CROP_PIXELS)
                featureBatch[index * FEATURES] = crop.relativeHeight
                featureBatch[index * FEATURES + 1] = crop.relativeBottom
            }
            inputs[0].writeFloat(cropBatch)
            inputs[1].writeFloat(featureBatch)
            model.run(inputs, outputs)
            val probabilities = outputs[0].readFloat()
            List(batch.size) { probabilities.copyOfRange(it * GlyphClasses.COUNT, (it + 1) * GlyphClasses.COUNT) }
        }

    override fun close() {
        (inputs + outputs).forEach { it.close() }
        model.close()
    }

    private companion object {
        const val MODEL_ASSET = "glyphnet.tflite"
        const val BATCH = 64
        const val CROP_PIXELS = SegmentedPage.CROP_SIZE * SegmentedPage.CROP_SIZE
        const val FEATURES = 2
    }
}
