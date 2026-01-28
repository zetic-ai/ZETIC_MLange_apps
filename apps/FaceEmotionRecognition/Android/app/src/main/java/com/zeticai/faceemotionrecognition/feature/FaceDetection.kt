package com.zeticai.faceemotionrecognition.feature

import android.content.Context
import com.zeticai.mlange.core.model.ZeticMLangeModel
import com.zeticai.mlange.feature.facedetection.FaceDetectionResults
import com.zeticai.mlange.feature.facedetection.FaceDetectionWrapper

class FaceDetection(
    context: Context,
) {
    private val model: ZeticMLangeModel = ZeticMLangeModel(context, "YOUR_MLANGE_KEY", modelKey)
    private val wrapper: FaceDetectionWrapper = FaceDetectionWrapper()

    fun run(imagePtr: Long): FaceDetectionResults {
        val preprocess = wrapper.preprocess(imagePtr)
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
        val modelKey: String = "ElenaRyumina/FaceEmotionRecognition"
    }
}