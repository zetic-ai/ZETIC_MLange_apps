package com.zeticai.faceemotionrecognition.feature

import android.content.Context
import com.zeticai.mlange.core.model.ZeticMLangeModel
import com.zeticai.mlange.feature.entity.Box
import com.zeticai.mlange.feature.faceemotionrecognition.FaceEmotionRecognitionResult
import com.zeticai.mlange.feature.faceemotionrecognition.FaceEmotionRecognitionWrapper

class FaceEmotionRecognition(
    context: Context,
) {
    private val model: ZeticMLangeModel = ZeticMLangeModel(context, "YOUR_PERSONAL_KEY", "YOUR_PROJECT_NAME")
    private val wrapper: FaceEmotionRecognitionWrapper = FaceEmotionRecognitionWrapper()

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

    companion object {
        val modelKey: String = "223fed6191c848df8b2b707b76707baa"
    }
}