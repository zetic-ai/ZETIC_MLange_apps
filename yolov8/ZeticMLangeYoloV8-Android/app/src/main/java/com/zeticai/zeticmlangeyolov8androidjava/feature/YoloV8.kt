package com.zeticai.zeticmlangeyolov8androidjava.feature

import android.content.Context
import com.zetic.ZeticMLange.ZeticMLangeModel
import com.zetic.ZeticMLangeFeature.ZeticMLangeFeatureYolov8
import com.zetic.ZeticMLangeFeature.type.YoloResult
import java.io.File
import java.io.FileOutputStream

class YoloV8 @JvmOverloads constructor(
    context: Context,
    modelKey: String,
    private val model: ZeticMLangeModel = ZeticMLangeModel(context, modelKey),
) {
    private val featureModel: ZeticMLangeFeatureYolov8

    init {
        val cocoYamlSamplePath = "coco.yaml"
        copyFileFromAssetsToData(context, cocoYamlSamplePath)
        val cocoYamlFile = File(context.filesDir, cocoYamlSamplePath)
        val cocoYamlPath = cocoYamlFile.absolutePath
        featureModel = ZeticMLangeFeatureYolov8(cocoYamlPath)
    }

    fun run(imagePtr: Long): YoloResult {
        val preprocess = featureModel.preprocess(imagePtr)
        model.run(arrayOf(preprocess))
        return featureModel.postprocess(model.outputBuffers[0].array())
    }

    fun close() {
        model.deinit()
        featureModel.deinit()
    }

    private fun copyFileFromAssetsToData(context: Context, fileName: String) {
        val assetManager = context.assets
        val file = assetManager.open(fileName)
        val outFile = File(context.filesDir, fileName)
        val out = FileOutputStream(outFile)

        val buffer = ByteArray(1024)
        var read: Int
        while ((file.read(buffer).also { read = it }) != -1) {
            out.write(buffer, 0, read)
        }

        file.close()
        out.flush()
        out.close()
    }
}