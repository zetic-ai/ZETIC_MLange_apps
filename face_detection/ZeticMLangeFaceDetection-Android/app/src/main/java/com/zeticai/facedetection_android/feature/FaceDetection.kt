package com.zeticai.facedetection_android.feature

import android.content.Context
import com.zetic.ZeticMLange.ZeticMLangeModel
import com.zetic.ZeticMLangeFeature.ZeticMLangeFeatureFaceDetection
import com.zetic.ZeticMLangeFeature.type.FaceDetectionResult

class FaceDetection @JvmOverloads constructor(
    context: Context,
    modelKey: String,
    private val model: ZeticMLangeModel = ZeticMLangeModel(context, modelKey),
    private val featureModel: ZeticMLangeFeatureFaceDetection = ZeticMLangeFeatureFaceDetection()
) {
    fun run(imagePtr: Long): FaceDetectionResult {
        val preprocess = featureModel.preprocess(imagePtr)
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