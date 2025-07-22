package com.zeticai.zetic_mlange_android

import android.content.Context
import com.zeticai.mlange.core.common.DataUtils
import com.zeticai.mlange.core.model.APType
import com.zeticai.mlange.core.model.Target
import com.zeticai.mlange.core.model.ZeticMLangeModel
import org.intellij.lang.annotations.Language
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer

class WhisperDecoder(
    private val startToken: Int,
    private val languageToken: Int,
    private val taskToken: Int,
    private val endToken: Int,
    context: Context,
    modelKey: String,
    private val model: ZeticMLangeModel =
        ZeticMLangeModel(
            context,
            "{INPUT YOUR TOKEN}", modelKey
        )
) {

    fun generateTokens(
        encoderOutput: ByteBuffer,
        maxLength: Int = 448
    ): List<Int> {

        val decoderTokenIds = IntArray(maxLength) { 50256 }
        decoderTokenIds[0] = startToken

        val decoderAttentionMask = IntArray(maxLength) { 0 }
        decoderAttentionMask[0] = 1

        var idx = 1
        val generated = mutableListOf<Int>()
        while (idx < maxLength) {
            println(idx)

            val logits = decodeStep(
                decoderTokenIds,
                encoderOutput,
                decoderAttentionMask
            )

            val vocab = logits.size / maxLength
            val currentLogits =
                logits.slice((vocab * (idx - 1)) until (vocab * idx)).toFloatArray()
            println("current: ${currentLogits[0]}")
            println("current: ${currentLogits[1]}")
            println("current: ${currentLogits[2]}")
            val next = ProbabilityUtils.argmax(currentLogits)

            if (next == endToken) break

            decoderTokenIds[idx] = next
            decoderAttentionMask[idx] = 1
            println(next)
            generated += next
            idx += 1
        }
        return generated
    }

    fun close() = model.deinit()

    private val idsSliceBuffer: ByteBuffer = ByteBuffer
        .allocate(448 * Int.SIZE_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)
    private val attentionMaskBuffer: ByteBuffer = ByteBuffer
        .allocate(448 * Int.SIZE_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)

    private val decoderOutputArray = FloatArray(448 * 51865)

    private fun decodeStep(
        idsSlice: IntArray,
        encoderOutput: ByteBuffer,
        decoderAttentionMask: IntArray
    ): FloatArray {
        idsSliceBuffer.clear()
        attentionMaskBuffer.clear()

        idsSliceBuffer.asIntBuffer().put(idsSlice)
        attentionMaskBuffer.asIntBuffer().put(decoderAttentionMask)

        model.run(
            arrayOf(
                idsSliceBuffer,
                encoderOutput,
                attentionMaskBuffer
            )
        )

        val buffer = model.outputBuffers[0].order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().get(decoderOutputArray)
        return decoderOutputArray
    }
}
