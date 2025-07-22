package com.zeticai.zetic_mlange_android

import android.content.Context
import android.util.Log
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.zeticai.mlange.core.benchmark.BenchmarkModel
import com.zeticai.mlange.core.benchmark.BenchmarkResults
import com.zeticai.mlange.core.benchmark.llm.LLMBenchmarkModel
import com.zeticai.mlange.core.benchmark.llm.LLMBenchmarkResults
import com.zeticai.mlange.core.common.DataUtils
import com.zeticai.mlange.core.model.Target
import com.zeticai.mlange.core.model.llm.LLMModelFetcher
import com.zeticai.mlange.core.model.llm.LLMQuantType
import com.zeticai.mlange.core.model.llm.LLMTarget
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    /*
    ##### Input Json #####
    {
        "name": "modelName",
        "targetModels": [
            {
                "target": "ZETIC_MLANGE_TARGET_COREML",
                "path": "/tmp/asdfadf.onnx",
            }
        ],
        "inputPaths": [
            "input_0.bin",
            "input_1.bin"
        ]
        "originalOutputPaths": [
            "output_0.bin",
            "output_1.bin"
        ]
    }

    ##### Output Json #####
    {
    "hardware": "QTI",
    "device": "SM-S928N",
    "results": [
        {
            "path": "/data/user/0/com.zeticai.zetic_mlange_android/files/yolov8n.onnx",
            "target": "ZETIC_MLANGE_TARGET_ORT",
            "apType": "AP_TYPE_NA",
            "latency": 0.06602191925048828,
            "snr": [
                17.341108
            ]
        },
        {
            "path": "/data/user/0/com.zeticai.zetic_mlange_android/files/yolov8n.onnx",
            "target": "ZETIC_MLANGE_TARGET_ORT_NNAPI",
            "apType": "AP_TYPE_NA",
            "latency": 0.31228166818618774,
            "snr": [
                20.671358
            ]
        },
        {
            "path": "/data/user/0/com.zeticai.zetic_mlange_android/files/libyolov8n_onnx.so",
            "target": "ZETIC_MLANGE_TARGET_QNN",
            "apType": "AP_TYPE_CPU",
            "latency": 0.14331704378128052,
            "snr": [
                19.473839
            ]
        },
        {
            "path": "/data/user/0/com.zeticai.zetic_mlange_android/files/libyolov8n_onnx.so",
            "target": "ZETIC_MLANGE_TARGET_QNN",
            "apType": "AP_TYPE_GPU",
            "latency": 0.038278643041849136,
            "snr": [
                36.02972
            ]
        },
        {
            "path": "/data/user/0/com.zeticai.zetic_mlange_android/files/libyolov8n_onnx.so",
            "target": "ZETIC_MLANGE_TARGET_QNN",
            "apType": "AP_TYPE_NPU",
            "latency": 0.02692718803882599,
            "snr": [
                -1
            ]
        }
    ]
}

     */

    @Test
    fun benchmark_model() {
        val context = getInstrumentation().targetContext

        runCatching {
            val modelInfoText = openTxtFileFromAssets(context, "model_info.txt")
            val modelJson = JSONObject(modelInfoText)
            val model = BenchmarkModel()
            val targetModels = modelJson.getJSONArray("targetModels")

            val inputPaths = modelJson.getJSONArray("inputPaths")
            val inputs = (0 until inputPaths.length()).map {
                readAssetFile(context, inputPaths.get(it) as String)
            }.toTypedArray()

            val originalOutputPaths = modelJson.getJSONArray("originalOutputPaths")
            val originalOutputs = (0 until originalOutputPaths.length()).map {
                readAssetFile(context, originalOutputPaths.get(it) as String)
            }
            val results = BenchmarkResults((0 until targetModels.length()).flatMap {
                runCatching {
                    val targetModel = targetModels.getJSONObject(it)
                    val target = Target.fromName(targetModel.getString("target"))
                    val path = copyAssetToInternalStorage(context, targetModel.getString("path"))
                    model.benchmark(
                        modelJson.getString("name"),
                        target,
                        path,
                        originalOutputs,
                        inputs
                    )
                }.onFailure {
                    printError(it)
                }.getOrDefault(emptyList())
            })

            Log.d(BenchmarkModel.RESULT_TAG, results.toJSONObject().toString())
        }.onFailure {
            printError(it)
        }
    }

    @Test
    fun benchmark_llm_model() {
        val context = getInstrumentation().targetContext

        runCatching {
            val modelInfoText = openTxtFileFromAssets(context, "model_info.txt")
            val modelJson = JSONObject(modelInfoText)
            val model = LLMBenchmarkModel()
            val targetModels = modelJson.getJSONArray("targetModels")

            val results = LLMBenchmarkResults((0 until targetModels.length()).flatMap {
                runCatching {
                    val targetModel = targetModels.getJSONObject(it)
                    val target = LLMTarget.valueOf(targetModel.getString("target"))
                    val quantType = LLMQuantType.valueOf(targetModel.getString("quant_type"))
                    val path = copyAssetToInternalStorage(context, targetModel.getString("path"))
                    model.benchmark(
                        context,
                        modelJson.getString("name"),
                        target,
                        quantType,
                        path,
                    )
                }.onFailure {
                    printError(it)
                }.getOrDefault(emptyList())
            })

            Log.d(BenchmarkModel.RESULT_TAG, results.toJSONObject().toString())
        }.onFailure {
            printError(it)
        }
    }

    @Test
    fun benchmark_llm_model_with_download() {
        val context = getInstrumentation().targetContext

        runCatching {
            val modelInfoText = openTxtFileFromAssets(context, "model_info.txt")
            val modelJson = JSONObject(modelInfoText)
            val model = LLMBenchmarkModel()
            val targetModels = modelJson.getJSONArray("targetModels")

            val results = LLMBenchmarkResults((0 until targetModels.length()).flatMap {
                runCatching {
                    val targetModel = targetModels.getJSONObject(it)
                    val target = LLMTarget.valueOf(targetModel.getString("target"))
                    val quantType = LLMQuantType.valueOf(targetModel.getString("quant_type"))
                    val remotePaths =
                        Array(targetModel.getJSONArray("remote_paths").length()) { i ->
                            targetModel.getJSONArray("remote_paths").getString(i)
                        }
                    val path = LLMModelFetcher.downloadModelWithRemotePaths(
                        context,
                        remotePaths,
                        target,
                        quantType
                    )
                    model.benchmark(
                        context,
                        modelJson.getString("name"),
                        target,
                        quantType,
                        path,
                    )
                }.onFailure {
                    printError(it)
                }.getOrDefault(emptyList())
            })

            Log.d(BenchmarkModel.RESULT_TAG, results.toJSONObject().toString())
        }.onFailure {
            printError(it)
        }
    }

    private fun printError(it: Throwable) {
        Log.e(BenchmarkModel.ERROR_TAG, it.message ?: "")
        Log.e(BenchmarkModel.ERROR_TAG, it.stackTrace.contentToString())
    }

    private fun readAssetFile(context: Context, assetFileName: String): ByteArray {
        context.assets.open(assetFileName).use { input ->
            val size = input.available()
            val buffer = ByteArray(size)
            input.read(buffer)
            return buffer
        }
    }

    private fun openTxtFileFromAssets(context: Context, fileName: String): String {
        val text = StringBuilder()
        context.assets.open(fileName).bufferedReader().use { reader ->
            var temp: String?
            while (reader.readLine().also { temp = it } != null) {
                text.append(temp)
            }
        }
        return text.toString()
    }

    private fun copyAssetToInternalStorage(context: Context, assetFileName: String): String {
        val inputStream = context.assets.open(assetFileName)
        val outFile = File(context.filesDir, assetFileName)
        val outputStream = FileOutputStream(outFile)
        val newFilePath = outFile.absolutePath

        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }

        return newFilePath
    }
}