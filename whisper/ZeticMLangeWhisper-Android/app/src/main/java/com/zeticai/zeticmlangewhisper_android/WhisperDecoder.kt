package com.zeticai.zeticmlangewhisper_android

import android.content.Context
import com.zetic.ZeticMLange.ZeticMLangeDataUtils
import com.zetic.ZeticMLange.ZeticMLangeModel
import java.nio.ByteBuffer
import kotlin.time.measureTimedValue

class WhisperDecoder(
    private val startToken: Int,
    private val languageToken: Int,
    private val taskToken: Int,
    private val endToken: Int,
    context: Context,
    modelKey: String,
    private val model: ZeticMLangeModel = ZeticMLangeModel(context, modelKey)
) {
    fun generateTokens(
        encoderOutput: ByteBuffer,
        maxLength: Int = 80,
        temperature: Float = 1.0f
    ): List<Int> {
        // Initialize with special tokens
        var decoderInputIds = LongArray(80).apply {
            set(0, startToken.toLong())
            set(1, languageToken.toLong())
            set(2, taskToken.toLong())
        }

        val generatedIds = mutableListOf<Int>()

        for (i in 3 until maxLength) {
            val logits = measureTimedValue {
                decodeStep(encoderOutput, decoderInputIds)
            } .also {
                println("decode inference : ${it.duration}")
            }.value

            val logitsSize = logits.size / maxLength

            val scaledLogits = if (temperature > 0) {
                FloatArray(logits.size) { j -> logits[j] / temperature }
            } else {
                logits
            }
            val currentLogits = scaledLogits.slice((logitsSize * (i - 1)) until (logitsSize * (i))).toFloatArray()

            val probs = ProbabilityUtils.softmax(currentLogits)

            val nextToken = if (temperature > 0) {
                ProbabilityUtils.sampleFromDistribution(probs)
            } else {
                ProbabilityUtils.argmax(probs)
            }

            if (nextToken == endToken) {
                break
            }

            generatedIds.add(nextToken)

            decoderInputIds[i] = nextToken.toLong()
        }

        return generatedIds
    }

    private fun decodeStep(encoderOutput: ByteBuffer, decoderInputIds: LongArray): FloatArray {
        model.run(arrayOf(
            ZeticMLangeDataUtils.convertLongArrayToByteBuffer(decoderInputIds),
            encoderOutput,
            )
        )

        val outputs = model.outputBuffers
        return ZeticMLangeDataUtils.convertByteBufferToFloatArray(outputs[0])
    }
}