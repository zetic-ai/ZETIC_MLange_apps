package com.zeticai.mlange.feature.yolov8

import android.content.Context
import com.zeticai.mlange.core.model.ZeticMLangeModel
import java.io.File
import java.io.FileOutputStream

class Yolov8 @JvmOverloads constructor(
    context: Context,
    modelKey: String,
    private val model: ZeticMLangeModel = ZeticMLangeModel(context, modelKey),
) {
    private val wrapper: Yolov8Wrapper

    init {
        val cocoYamlSamplePath = "coco.yaml"
        copyFileFromAssetsToData(context, cocoYamlSamplePath)
        val cocoYamlFile = File(context.filesDir, cocoYamlSamplePath)
        val cocoYamlPath = cocoYamlFile.absolutePath
        wrapper = Yolov8Wrapper(cocoYamlPath)
    }

    fun run(imagePtr: Long): YoloResult {
        val preprocess = wrapper.preprocess(imagePtr)
        model.run(arrayOf(preprocess))
        return wrapper.postprocess(model.outputBuffers[0].array())
    }

    fun close() {
        model.deinit()
        wrapper.deinit()
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