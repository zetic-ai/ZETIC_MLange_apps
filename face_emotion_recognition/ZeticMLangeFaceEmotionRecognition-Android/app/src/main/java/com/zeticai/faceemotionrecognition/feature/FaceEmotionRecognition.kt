package com.zeticai.faceemotionrecognition.feature

import android.content.Context
import com.zetic.ZeticMLange.ZeticMLangeModel
import com.zetic.ZeticMLangeFeature.ZeticMLangeFeatureFaceEmotionRecognition
import com.zetic.ZeticMLangeFeature.type.Box
import com.zetic.ZeticMLangeFeature.type.FaceEmotionRecognitionResult

class FaceEmotionRecognition @JvmOverloads constructor(
    context: Context,
    modelKey: String,
    private val model: ZeticMLangeModel = ZeticMLangeModel(context, modelKey),
    private val featureModel: ZeticMLangeFeatureFaceEmotionRecognition = ZeticMLangeFeatureFaceEmotionRecognition()
) {
    fun run(imagePtr: Long, roi: Box): FaceEmotionRecognitionResult {
        val preprocess = featureModel.preprocess(imagePtr, roi)
        model.run(arrayOf(preprocess))
        val modelOutput = model.outputBuffers
        return featureModel.postprocess(modelOutput.map {
            val byteArray = ByteArray(it.remaining())
            it.get(byteArray)
            return@map byteArray
        }.toTypedArray())
    }

    fun close() {
        model.deinit()
        featureModel.deinit()
    }
}