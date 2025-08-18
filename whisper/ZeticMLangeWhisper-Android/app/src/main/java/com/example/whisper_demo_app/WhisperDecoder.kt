package com.example.whisper_demo_app

import android.content.Context
import com.zeticai.mlange.core.model.ZeticMLangeModel
import com.zeticai.mlange.core.tensor.Tensor
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WhisperDecoder(
    private val startToken: Int,
    private val endToken: Int,
    context: Context,
    modelKey: String,
    private val model: ZeticMLangeModel =
        ZeticMLangeModel(
            context,
            BuildConfig.PERSONAL_KEY, modelKey, null
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

            val logits = decodeStep(
                decoderTokenIds,
                encoderOutput,
                decoderAttentionMask
            )

            val vocab = logits.size / maxLength
            val currentLogits =
                logits.slice((vocab * (idx - 1)) until (vocab * idx)).toFloatArray()
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

        val outputs = model.run(
            arrayOf(
                Tensor.of(idsSliceBuffer),
                Tensor.of(encoderOutput),
                Tensor.of(attentionMaskBuffer)
            )
        )

        val buffer = outputs[0].data.order(ByteOrder.LITTLE_ENDIAN)
        buffer.asFloatBuffer().get(decoderOutputArray)
        return decoderOutputArray
    }
}
