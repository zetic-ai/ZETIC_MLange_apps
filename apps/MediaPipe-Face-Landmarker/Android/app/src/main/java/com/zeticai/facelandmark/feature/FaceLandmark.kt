package com.zeticai.facelandmark.feature

import android.content.Context
import com.zeticai.mlange.core.model.ZeticMLangeModel
import com.zeticai.mlange.feature.entity.Box
import com.zeticai.mlange.feature.facelandmark.FaceLandmarkResult
import com.zeticai.mlange.feature.facelandmark.FaceLandmarkWrapper

class FaceLandmark(
    context: Context,
) {
    private val model: ZeticMLangeModel = ZeticMLangeModel(context, "YOUR_MLANGE_KEY", modelKey)
    private val wrapper: FaceLandmarkWrapper = FaceLandmarkWrapper()

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

    companion object {
        val modelKey: String = "google/MediaPipe-Face-Landmark"
    }
}