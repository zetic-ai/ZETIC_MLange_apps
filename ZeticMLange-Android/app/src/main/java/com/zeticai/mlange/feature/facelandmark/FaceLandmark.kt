package com.zeticai.mlange.feature.facelandmark

import android.content.Context
import com.zeticai.mlange.core.model.ZeticMLangeModel
import com.zeticai.mlange.feature.entity.Box

class FaceLandmark @JvmOverloads constructor(
    context: Context,
    modelKey: String,
    private val model: ZeticMLangeModel = ZeticMLangeModel(context, modelKey),
    private val wrapper: FaceLandmarkWrapper = FaceLandmarkWrapper()
) {
    fun run(imagePtr: Long, roi: Box): FaceLandmarkResult {
        return runCatching {
            val preprocess = wrapper.preprocess(imagePtr, roi)
            model.run(arrayOf(preprocess))
            val modelOutput = model.outputBuffers
            return wrapper.postprocess(modelOutput.map {
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
        wrapper.deinit()
    }
}