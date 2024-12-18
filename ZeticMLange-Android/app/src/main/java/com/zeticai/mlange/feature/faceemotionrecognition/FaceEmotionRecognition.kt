package com.zeticai.mlange.feature.faceemotionrecognition

import android.content.Context
import com.zeticai.mlange.core.model.ZeticMLangeModel
import com.zeticai.mlange.feature.entity.Box

class FaceEmotionRecognition @JvmOverloads constructor(
    context: Context,
    modelKey: String,
    private val model: ZeticMLangeModel = ZeticMLangeModel(context, modelKey),
    private val wrapper: FaceEmotionRecognitionWrapper = FaceEmotionRecognitionWrapper()
) {
    fun run(imagePtr: Long, roi: Box): FaceEmotionRecognitionResult {
        val preprocess = wrapper.preprocess(imagePtr, roi)
        model.run(arrayOf(preprocess))
        val modelOutput = model.outputBuffers
        return wrapper.postprocess(modelOutput.map {
            val byteArray = ByteArray(it.remaining())
            it.get(byteArray)
            return@map byteArray
        }.toTypedArray())
    }

    fun close() {
        model.deinit()
        wrapper.deinit()
    }
}