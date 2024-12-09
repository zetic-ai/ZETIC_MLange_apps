package com.zeticai.zeticmlangewhisper_android

import android.content.Context
import com.zetic.ZeticMLange.ZeticMLangeDataUtils
import com.zetic.ZeticMLange.ZeticMLangeModel
import java.nio.ByteBuffer

class WhisperEncoder(
    context: Context,
    modelKey: String,
    private val model: ZeticMLangeModel = ZeticMLangeModel(context, modelKey)
) {

    fun process(audioData: FloatArray): ByteBuffer {
        val byteArrayFeatures = ZeticMLangeDataUtils.convertFloatArrayToByteBufferArray(arrayOf(audioData))
        model.run(byteArrayFeatures)
        return model.outputBuffers[0]
    }
}