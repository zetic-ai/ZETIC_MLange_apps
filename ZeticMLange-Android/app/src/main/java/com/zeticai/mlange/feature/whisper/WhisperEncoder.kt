package com.zeticai.mlange.feature.whisper

import android.content.Context
import com.zeticai.mlange.core.common.DataUtils
import com.zeticai.mlange.core.model.ZeticMLangeModel
import java.nio.ByteBuffer

class WhisperEncoder(
    context: Context,
    modelKey: String,
    private val model: ZeticMLangeModel = ZeticMLangeModel(context, modelKey)
) {

    fun process(audioData: FloatArray): ByteBuffer {
        val byteArrayFeatures = DataUtils.convertFloatArrayToByteBufferArray(arrayOf(audioData))
        model.run(byteArrayFeatures)
        return model.outputBuffers[0]
    }

    fun close() {
        model.deinit()
    }
}