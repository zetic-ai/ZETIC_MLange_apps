package com.zeticai.mlange.feature.facedetection

import android.content.Context
import com.zeticai.mlange.core.model.ZeticMLangeModel

class FaceDetection @JvmOverloads constructor(
    context: Context,
    modelKey: String,
    private val model: ZeticMLangeModel = ZeticMLangeModel(context, modelKey),
    private val wrapper: FaceDetectionWrapper = FaceDetectionWrapper()
) {
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
}