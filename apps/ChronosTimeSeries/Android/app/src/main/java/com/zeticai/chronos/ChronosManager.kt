package com.zeticai.chronos

import android.content.Context
import com.zeticai.mlange.core.model.ZeticMLangeModel
import com.zeticai.mlange.core.tensor.DataType
import com.zeticai.mlange.core.tensor.Tensor
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ForecastResult(
    val forecast: ForecastBundle
)

class ChronosManager(private val context: Context) {
    private var model: ZeticMLangeModel? = null
    private val contextLength = 512
    private val modelQuantilesCount = 9

    suspend fun loadModel(token: String, name: String, version: Int, progressListener: ((Float) -> Unit)? = null) {
        model = ZeticMLangeModel(context, token, name, version, onProgress = progressListener)
    }

    fun runInference(values: List<Float>, horizon: Int, quantiles: List<Float>): ForecastResult {
        val model = model ?: throw IllegalStateException("Model not loaded")

        // 1. Prepare Inputs
        val contextInput = prepareInputs(values, contextLength)
        
        // [1, 512] Tensor
        val byteBuffer = ByteBuffer.allocateDirect(1 * contextLength * 4)
        byteBuffer.order(ByteOrder.nativeOrder())
        val floatBuffer = byteBuffer.asFloatBuffer()
        floatBuffer.put(contextInput)
        byteBuffer.rewind()
        
        val inputTensor = Tensor(byteBuffer, DataType.Float32, intArrayOf(1, contextLength))

        // 2. Run Model
        val outputs = model.run(arrayOf(inputTensor)) ?: throw IllegalStateException("Model execution failed")
        
        // 3. Parse Output
        val forecastBundle = parseForecastBolt(outputs, horizon, quantiles)
        
        return ForecastResult(forecastBundle)
    }

    // MARK: - Internal Logic

    private fun prepareInputs(values: List<Float>, length: Int): FloatArray {
        val buffer = FloatArray(length) { Float.NaN }
        
        if (values.size >= length) {
            // Take last 'length'
            val suffix = values.takeLast(length)
            for (i in suffix.indices) buffer[i] = suffix[i]
        } else {
            // Place at end (Right-aligned)
            val offset = length - values.size
            for (i in values.indices) buffer[offset + i] = values[i]
        }
        return buffer
    }

    private fun parseForecastBolt(outputs: Array<Tensor>, horizon: Int, reqQuantiles: List<Float>): ForecastBundle {
        val first = outputs.firstOrNull() ?: throw IllegalStateException("No Output Tensor")
        val dataBuffer = first.data
        dataBuffer.rewind()
        val floatCount = dataBuffer.remaining() / 4
        val values = FloatArray(floatCount)
        dataBuffer.asFloatBuffer().get(values)
        
        val modelHorizon = values.size / modelQuantilesCount
        val byQuantile = mutableMapOf<String, List<Float>>()
        
        if (values.size >= modelQuantilesCount * modelHorizon) {
             val stride = modelHorizon
             val readLen = if (horizon < modelHorizon) horizon else modelHorizon
             
             // Median Index is 4
             val medianIdx = 4
             val startM = medianIdx * stride
             val medianSeries = values.slice(startM until (startM + readLen))
             byQuantile["mean"] = medianSeries
             
             for (q in reqQuantiles) {
                 // 0.1 -> 0, 0.9 -> 8
                 var idx = (q * 10).toInt() - 1
                 if (idx < 0) idx = 0
                 if (idx > 8) idx = 8
                 
                 val start = idx * stride
                 val end = start + readLen
                 byQuantile[formatQuantileLabel(q)] = values.slice(start until end)
             }
        } else {
             // Fallback
             byQuantile["mean"] = values.take(horizon)
        }
        
        return ForecastBundle(byQuantile)
    }

    private fun formatQuantileLabel(q: Float): String {
        return "q" + String.format("%.2f", q).replace(".", "_")
    }
}
