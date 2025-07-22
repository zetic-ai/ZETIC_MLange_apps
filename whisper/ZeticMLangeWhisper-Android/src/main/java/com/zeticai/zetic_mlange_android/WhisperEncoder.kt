package com.zeticai.zetic_mlange_android

import android.content.Context
import com.zeticai.mlange.core.common.DataUtils
import com.zeticai.mlange.core.model.APType
import com.zeticai.mlange.core.model.Target
import com.zeticai.mlange.core.model.ZeticMLangeModel
import java.nio.ByteBuffer

class WhisperEncoder(
    context: Context,
    modelKey: String,
    private val model: ZeticMLangeModel = ZeticMLangeModel(
        context,
        "ztp_68b79702a2524522a4ac799dedaf7854",
        modelKey
    )
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