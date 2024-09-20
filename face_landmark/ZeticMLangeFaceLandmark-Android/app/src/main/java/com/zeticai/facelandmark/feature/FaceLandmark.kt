package com.zeticai.facelandmark.feature

import android.content.Context
import com.zetic.ZeticMLange.ZeticMLangeModel
import com.zetic.ZeticMLangeFeature.ZeticMLangeFeatureFaceLandmark
import com.zetic.ZeticMLangeFeature.type.Box
import com.zetic.ZeticMLangeFeature.type.FaceLandmarkResult

class FaceLandmark @JvmOverloads constructor(
    context: Context,
    modelKey: String,
    private val model: ZeticMLangeModel = ZeticMLangeModel(context, modelKey),
    private val featureModel: ZeticMLangeFeatureFaceLandmark = ZeticMLangeFeatureFaceLandmark()
) {
    fun run(imagePtr: Long, roi: Box): FaceLandmarkResult {
        return runCatching {
            val preprocess = featureModel.preprocess(imagePtr, roi)
            model.run(arrayOf(preprocess))
            val modelOutput = model.outputBuffers
            return featureModel.postprocess(modelOutput.map {
                val byteArray = ByteArray(it.remaining())
                it.get(byteArray)
                return@map byteArray
            }.sortedBy {
                it.size
            }.toTypedArray())
        }.getOrElse {
            FaceLandmarkResult(emptyList(), 0.0f)
        }
    }

    fun close() {
        model.deinit()
        featureModel.deinit()
    }
}