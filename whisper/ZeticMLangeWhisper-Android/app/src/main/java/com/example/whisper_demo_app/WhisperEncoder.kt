package com.example.whisper_demo_app

import android.content.Context
import com.zeticai.mlange.core.model.ZeticMLangeModel
import com.zeticai.mlange.core.tensor.Tensor
import java.nio.ByteBuffer

class WhisperEncoder(
    context: Context,
    modelKey: String,
    private val model: ZeticMLangeModel = ZeticMLangeModel(
        context,
        BuildConfig.PERSONAL_KEY,
        modelKey
    )
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