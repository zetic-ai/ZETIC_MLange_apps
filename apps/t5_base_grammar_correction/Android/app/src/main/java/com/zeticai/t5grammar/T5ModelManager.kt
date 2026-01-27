package com.zeticai.t5grammar

import android.content.Context
import android.util.Log
import com.zeticai.mlange.core.model.ModelMode
import com.zeticai.mlange.core.model.ZeticMLangeModel
import com.zeticai.mlange.core.tensor.DataType
import com.zeticai.mlange.core.tensor.Tensor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer

class T5ModelManager(context: Context) {
    private var model: ZeticMLangeModel? = null
    private val tokenizer = Tokenizer(context)

    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded = _isModelLoaded.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress = _downloadProgress.asStateFlow()

    // Model Config
    // Must be 1024 to match the compiled model artifact. 
    // Reducing this causes "Source buffer is smaller than expected" error unless the model is re-exported.
    private val fixedEncoderLength = 1024
    private val fixedDecoderLength = 128
    // SAFETY: Use 1024 for decoder tensor too to avoid 'Buffer Smaller' errors if slots mismatch
    private val decoderTensorLength = 1024
    
    // Pre-allocated buffers (Int64 = 8 bytes)
    private val inputIdsBuffer: ByteBuffer = ByteBuffer.allocateDirect(fixedEncoderLength * 8).order(ByteOrder.nativeOrder())
    private val attentionMaskBuffer: ByteBuffer = ByteBuffer.allocateDirect(fixedEncoderLength * 8).order(ByteOrder.nativeOrder())
    private val decoderInputIdsBuffer: ByteBuffer = ByteBuffer.allocateDirect(decoderTensorLength * 8).order(ByteOrder.nativeOrder())
    
    // Arrays for logic
    // We keep logic array at 128 to save memory/time, but copy to 1024 buffer? 
    // Or just make it 1024.
    private var decoderInputIds = IntArray(decoderTensorLength)

    init {
        loadModel(context)
        // ...
    }
    

    private fun loadModel(context: Context) {
        Thread {
            try {
                // Keys from user request/iOS
                val modelName = "Team_ZETIC/t5-base-grammar-correction"
                val tokenKey = "YOUR_PERSONAL_ACCESS_TOKEN"
                val version = 3
                
                Log.d("T5ModelManager", "Loading model: $modelName")
                model = ZeticMLangeModel(
                    context = context, 
                    tokenKey = tokenKey, 
                    name = modelName, 
                    version = version,
                    onProgress = { progress ->
                        _downloadProgress.value = progress
                    },
                    modelMode = ModelMode.RUN_ACCURACY
                )
                _isModelLoaded.value = true
                Log.d("T5ModelManager", "Model loaded successfully.")
            } catch (e: Exception) {
                Log.e("T5ModelManager", "Failed to load model", e)
                _error.value = "Failed to load model: ${e.message}"
            }
        }.start()
    }

    suspend fun runInference(text: String): String = withContext(Dispatchers.Default) {
        if (!_isModelLoaded.value || model == null) {
            throw IllegalStateException("Model is not loaded")
        }

        // 1. Tokenize & Pad
        val prompt = "grammar: $text"
        val rawIds = tokenizer.encode(prompt)
        
        val inputIds = IntArray(fixedEncoderLength)
        val attentionMask = IntArray(fixedEncoderLength)
        
        for (i in inputIds.indices) {
            if (i < rawIds.size) {
                inputIds[i] = rawIds[i].toInt()
                attentionMask[i] = 1 // Not padding
            } else {
                inputIds[i] = 0 // Pad
                attentionMask[i] = 0 // Pad mask
            }
        }

        // 2. Prepare Decoder Buffer with Start Token
        val decoderStartTokenId = 0
        val eosTokenId = 1
        decoderInputIds.fill(0)
        decoderInputIds[0] = decoderStartTokenId

        Log.d("T5ModelManager", "Starting inference loop...")

        // OPTIMIZATION: Fill constant buffers ONCE outside the loop
        inputIdsBuffer.clear()
        val inputLongs = LongArray(fixedEncoderLength) { i -> inputIds[i].toLong() }
        inputIdsBuffer.asLongBuffer().put(inputLongs)
        
        attentionMaskBuffer.clear()
        val maskLongs = LongArray(fixedEncoderLength) { i -> attentionMask[i].toLong() }
        attentionMaskBuffer.asLongBuffer().put(maskLongs)
        
        // Reused array for argmax optimization
        val vocabSize = 32128
        val logitsArray = FloatArray(vocabSize)

        // 3. Create Tensors ONCE (Reuse wrappers)
        // Encoder inputs are constant for the whole decoding loop of one sentence
        val encoderInputTensor = Tensor(inputIdsBuffer, DataType.Int64, intArrayOf(1, fixedEncoderLength))
        val encoderMaskTensor = Tensor(attentionMaskBuffer, DataType.Int64, intArrayOf(1, fixedEncoderLength))
        val decoderInputTensor = Tensor(decoderInputIdsBuffer, DataType.Int64, intArrayOf(1, decoderTensorLength))

        val inputs = arrayOf(encoderInputTensor, encoderMaskTensor, decoderInputTensor)

        for (step in 0 until (fixedDecoderLength - 1)) {
            try {
                // Only update Decoder buffer inside loop
                // Since decoderInputTensor wraps this buffer, we just need to update the buffer content
                decoderInputIdsBuffer.clear()
                val decoderLongs = LongArray(decoderTensorLength) { i -> decoderInputIds[i].toLong() }
                decoderInputIdsBuffer.asLongBuffer().put(decoderLongs)

                // Run with cached Tensor objects
                val outputs = model!!.run(inputs)
                val logitsTensor = outputs.firstOrNull() ?: break
                
                // Extract Logits
                // Output shape is [1, 128, 32128]
                val floatBuffer = logitsTensor.data.asFloatBuffer()
                
                // Calculate start index in float buffer
                val startIndex = step * vocabSize
                
                if (startIndex + vocabSize > floatBuffer.remaining()) {
                    Log.e("T5ModelManager", "Logit bounds error")
                    break
                }

                // OPTIMIZATION: Bulk read into array
                // Position the buffer at start index
                floatBuffer.position(startIndex)
                floatBuffer.get(logitsArray, 0, vocabSize)

                // Argmax on array (Fastest in JVM)
                var maxVal = Float.NEGATIVE_INFINITY
                var maxIdx = 0
                
                for (i in 0 until vocabSize) {
                    val valAt = logitsArray[i]
                    if (valAt > maxVal) {
                        maxVal = valAt
                        maxIdx = i
                    }
                }
                
                val nextToken = maxIdx
                
                if (nextToken == eosTokenId) {
                    // Log.d("T5ModelManager", "EOS reached at step $step.")
                    break
                }
                
                decoderInputIds[step + 1] = nextToken
            } catch (e: Exception) {
                Log.e("T5ModelManager", "Inference Step $step Failed", e)
                throw e
            }
        }

        // 3. Decode
        // Filter start token and zeros, also stop at EOS if present in array effectively
        // The array might contain tail zeros if we broke early.
        // We need to collect valid tokens.
        val outputList = ArrayList<Long>()
        // Skip index 0 (Start Token)
        for (i in 1 until fixedDecoderLength) {
            val token = decoderInputIds[i]
            if (token == 0) continue // Skip padding? Or stop? 
            // Actually, if we broke loop, the rest are 0.
            // But if EOS marked termination, we just take up to EOS.
            if (token == eosTokenId) break
            outputList.add(token.toLong())
        }
        
        val result = tokenizer.decode(outputList.toLongArray())
        Log.d("T5ModelManager", "Result: '$result'")
        return@withContext result
    }
}
