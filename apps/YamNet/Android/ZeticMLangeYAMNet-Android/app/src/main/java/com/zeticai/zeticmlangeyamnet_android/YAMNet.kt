package com.zeticai.zeticmlangeyamnet_android

import android.content.Context
import com.zeticai.mlange.core.model.ZeticMLangeModel
import com.zeticai.mlange.core.model.ZeticMLangeTarget
import java.nio.ByteBuffer
import java.nio.ByteOrder

class YAMNet(
    context: Context,
) {
    private val model: ZeticMLangeModel = ZeticMLangeModel(context, "YOUR_MLANGE_KEY", "YOUR_PROJECT_NAME")
    private val audioRecord: AudioSampler by lazy {
        AudioSampler()
    }

    private fun postprocess(
        outputBuffer: ByteBuffer
    ): Pair<IntArray, FloatArray> {
        val row = 6
        val column = 521
        val topN = 5

        outputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.rewind()
        val floatBuffer = outputBuffer.asFloatBuffer()
        val floatArray = FloatArray(outputBuffer.remaining() / Float.SIZE_BYTES)
        floatBuffer.get(floatArray)

        val scores = Array(row) { FloatArray(column) }
        for (i in 0 until row) {
            System.arraycopy(floatArray, i * column, scores[i], 0, column)
        }

        val meanScores = FloatArray(column) { index ->
            scores.map { it[index] }.average().toFloat()
        }

        val topClassIndices = meanScores.withIndex()
            .sortedByDescending { it.value }
            .take(topN)
            .map { it.index }
            .toIntArray()

        return Pair(topClassIndices, meanScores)
    }

    fun startRecording(visualize: (Pair<IntArray, FloatArray>) -> Unit) {
        audioRecord.startRecording {
            model.run(arrayOf(it))
            val output = model.outputBuffers[1]
            val outputs = postprocess(output)
            visualize(outputs)
        }
    }

    fun deinit() {
        audioRecord.stopRecording()
        model.deinit()
    }

    companion object {
        val modelKey: String = "0578064c31cf45669c5b1aadc23ed991"
    }
}