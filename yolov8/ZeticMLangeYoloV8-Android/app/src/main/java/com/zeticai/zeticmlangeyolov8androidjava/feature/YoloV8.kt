package com.zeticai.zeticmlangeyolov8androidjava.feature

import android.content.Context
import com.zeticai.mlange.core.model.ZeticMLangeModel
import com.zeticai.mlange.feature.yolov8.Yolov8Wrapper
import com.zeticai.mlange.feature.yolov8.YoloResult
import java.io.File
import java.io.FileOutputStream

class YoloV8 @JvmOverloads constructor(
    context: Context,
    personalKey: String,
    modelKey: String,
    private val model: ZeticMLangeModel = ZeticMLangeModel(context, personalKey, modelKey),
) {
    private val yolov8Wrapper: Yolov8Wrapper

    init {
        val cocoYamlSamplePath = "coco.yaml"
        copyFileFromAssetsToData(context, cocoYamlSamplePath)
        val cocoYamlFile = File(context.filesDir, cocoYamlSamplePath)
        val cocoYamlPath = cocoYamlFile.absolutePath
        yolov8Wrapper = Yolov8Wrapper(cocoYamlPath)
    }

    fun run(imagePtr: Long): YoloResult {
        val preprocess = yolov8Wrapper.preprocess(imagePtr)
        model.run(arrayOf(preprocess))
        return yolov8Wrapper.postprocess(model.outputBuffers[0].array())
    }

    fun close() {
        model.deinit()
        yolov8Wrapper.deinit()
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