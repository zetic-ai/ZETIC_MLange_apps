package com.zeticai.yolov26

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.zeticai.mlange.tensor.Tensor
import com.zeticai.mlange.model.ZeticMLangeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

class YOLOv26Model(context: Context) {
    private var model: ZeticMLangeModel? = null
    
    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded = _isModelLoaded.asStateFlow()
    
    // Output state
    private val _detectionResults = MutableStateFlow<List<BoundingBox>>(emptyList())
    val detectionResults = _detectionResults.asStateFlow()
    
    private val _inferenceTime = MutableStateFlow(0L)
    val inferenceTime = _inferenceTime.asStateFlow()

    init {
        // Load Model Asynchronously
        // "dev_d786c1fd7f2848acb9b0bf8060aa10b2", "Team_ZETIC/YOLOv26"
        loadModel(context)
    }

    private fun loadModel(context: Context) {
        // Run on background thread
        // Note: In real app use Coroutines Scope properly. Here init simple.
        Thread {
            try {
                // Using version 3 as per iOS config
                model = ZeticMLangeModel(context, "dev_d786c1fd7f2848acb9b0bf8060aa10b2", "Team_ZETIC/YOLOv26", 3)
                _isModelLoaded.value = true
                Log.d("YOLOv26", "Model Loaded Successfully")
            } catch (e: Exception) {
                Log.e("YOLOv26", "Failed to load model", e)
            }
        }.start()
    }

    suspend fun detect(bitmap: Bitmap) = withContext(Dispatchers.Default) {
        if (model == null) return@withContext

        val startTime = System.currentTimeMillis()
        
        // 1. Prepare Input
        val inputData = ImageUtils.prepareInput(bitmap)
        
        // 2. Wrap in Tensor
        // [1, 3, 640, 640]
        val inputTensor = Tensor(
            inputData, 
            longArrayOf(1, 3, 640, 640)
        )
        val inputs = arrayOf(inputTensor)
        
        try {
            // 3. Run Inference
            val outputs = model?.run(inputs)
            
            val outputTensor = outputs?.firstOrNull() ?: return@withContext
            
            // 4. Post Process
            val shape = outputTensor.shape
            // Expected [1, 300, 6]
            val rows = if (shape.size > 1) shape[1].toInt() else 0
            val cols = if (shape.size > 2) shape[2].toInt() else 0
            
            val results = PostProcess.process(
                outputTensor.data, 
                rows, 
                cols, 
                bitmap.width.toFloat(), 
                bitmap.height.toFloat()
            )
            
            val duration = System.currentTimeMillis() - startTime
            
            _detectionResults.emit(results)
            _inferenceTime.emit(duration)
            
        } catch (e: Exception) {
            Log.e("YOLOv26", "Inference error", e)
            _detectionResults.emit(emptyList())
        }
    }
}
