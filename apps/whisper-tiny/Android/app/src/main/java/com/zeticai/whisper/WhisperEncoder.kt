package com.zeticai.whisper

import android.content.Context
import com.zeticai.mlange.core.model.ZeticMLangeModel
import com.zeticai.mlange.core.tensor.Tensor
import java.nio.ByteBuffer

class WhisperEncoder(
    context: Context,
    modelKey: String,
    private val model: ZeticMLangeModel = ZeticMLangeModel(context, "YOUR_MLANGE_KEY", modelKey)
) {

    fun process(audioData: FloatArray): ByteBuffer {
        val byteArrayFeatures =  arrayOf(
            Tensor.of(audioData)
        )

        return model.run(byteArrayFeatures)[0].data
    }

    fun close() {
        model.deinit()
    }
}