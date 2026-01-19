package com.zeticai.textanonymizer

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.zeticai.mlange.core.model.Target
import com.zeticai.mlange.core.model.ZeticMLangeModel
import com.zeticai.mlange.core.tensor.DataType
import com.zeticai.mlange.core.tensor.Tensor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.util.regex.Pattern


class AnonymizerViewModel(private val context: Context) : ViewModel() {
    companion object {
        private const val TAG = "TextAnonymizer"
    }
    
    private val _anonymizedText = MutableLiveData<String>()
    val anonymizedText: LiveData<String> = _anonymizedText
    
    private val _isProcessing = MutableLiveData<Boolean>()
    val isProcessing: LiveData<Boolean> = _isProcessing
    
    private val _showingError = MutableLiveData<Boolean>()
    val showingError: LiveData<Boolean> = _showingError
    
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    fun clearError() {
        _showingError.value = false
        _errorMessage.value = ""
    }
    
    private val _isModelLoaded = MutableLiveData<Boolean>()
    val isModelLoaded: LiveData<Boolean> = _isModelLoaded
    
    private var model: ZeticMLangeModel? = null
    private val modelMaxLength = 128
    private var lastInputBytes: ByteArray = ByteArray(0)
    private var lastInputByteCount: Int = 0
    
    private val classLabels = listOf(
        "O",
        "EMAIL",
        "PHONE_NUMBER",
        "CREDIT_CARD_NUMBER",
        "SSN",
        "NRP",
        "PERSON",
        "ADDRESS",
        "LOCATION",
        "DATE",
        "OTHER"
    )
    
    private val placeholderByLabel = mapOf(
        "EMAIL" to "[Email]",
        "PHONE_NUMBER" to "[Phone number]",
        "CREDIT_CARD_NUMBER" to "[Credit card]",
        "SSN" to "[SSN]",
        "NRP" to "[NRP]"
    )
    
    init {
        loadModelAsync()
    }
    
    private fun loadModelAsync() {
        _isModelLoaded.value = false
        
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "Starting model loading...")
                Log.i(TAG, "Attempting to load Zetic MLange model...")
                println("🔄 Starting model loading...")
                println("📦 Attempting to load Zetic MLange model...")
                
                if (Constants.MLANGE_PERSONAL_ACCESS_TOKEN.isEmpty() || 
                    Constants.MLANGE_PERSONAL_ACCESS_TOKEN == "YOUR_PERSONAL_ACCESS_TOKEN") {
                    throw Exception("Missing access token. Set MLANGE_PERSONAL_ACCESS_TOKEN in Constants.kt")
                }
                
                Log.i(TAG, "Model: ${Constants.MODEL_NAME} (v${Constants.MODEL_VERSION})")
                println("   Model: ${Constants.MODEL_NAME}")
                
                Log.d("TEMP", "filesDir = ${context.applicationContext.filesDir}")
                Log.d("TEMP", "filesDir = ${context.filesDir}")
                
                val loadedModel = ZeticMLangeModel(
                    context.applicationContext,
                    Constants.MLANGE_PERSONAL_ACCESS_TOKEN,
                    Constants.MODEL_NAME
                )
                
                Log.i(TAG, "Model instance created successfully")
                Log.i(TAG, "Target model: ${loadedModel.targetModel}")
                println("✅ Model instance created successfully")
                println("   Target model: ${loadedModel.targetModel}")

                // Smoke test: run one inference using input buffers from the SDK
                try {
                    val inputs = loadedModel.getInputBuffers()
                    Log.i(TAG, "Running smoke test...")
                    val outputs = loadedModel.run(inputs)
                    Log.i(TAG, "Model smoke test passed. Outputs: ${outputs.size}")
                    println("✅ Model smoke test passed. Outputs: ${outputs.size}")
                } catch (smokeError: Exception) {
                    val smokeMessage = smokeError.message ?: smokeError.toString()
                    Log.e(TAG, "Model smoke test failed: $smokeMessage")
                    println("❌ Model smoke test failed: $smokeMessage")
                    throw smokeError
                }
                
                withContext(Dispatchers.Main) {
                    model = loadedModel
                    _isModelLoaded.value = true
                    println("✅ Model loaded and ready to use")
                }
            } catch (e: Exception) {
                val errorDescription = e.message ?: e.toString()
                Log.e(TAG, "Model loading failed: $errorDescription")
                println("❌ Model loading failed!")
                println("   Error: $errorDescription")
                
                withContext(Dispatchers.Main) {
                    _isModelLoaded.value = false
                    _errorMessage.value = "Failed to load model: $errorDescription\n\nPossible causes:\n• Invalid token or authentication failed\n• Network connection issue\n• Model download timeout\n• Model not found\n\nCheck logcat for details."
                    _showingError.value = true
                }
            }
        }
    }
    
    fun anonymizeText(text: String) {
        if (text.isEmpty()) return
        
        val currentModel = model
        if (currentModel == null) {
            _errorMessage.value = "Model not loaded. Please restart the app."
            _showingError.value = true
            return
        }
        
        _isProcessing.value = true
        _anonymizedText.value = ""
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                println("📝 Converting text to tensor...")
                println("   Input text: $text")
                
                // Try different data types
                val candidateDataTypes = listOf("uint8", "int32")
                var outputs: Array<Tensor>? = null
                var lastError: Exception? = null
                
                for (dataType in candidateDataTypes) {
                    try {
                        val inputs = createInputTensorsFromText(text, dataType)
                        println("✅ Created input tensors. Count: ${inputs.size}")
                        outputs = runModel(currentModel, inputs)
                        lastError = null
                        break
                    } catch (e: Exception) {
                        lastError = e
                        println("⚠️ Inference failed with dataType $dataType: ${e.message}")
                    }
                }
                
                if (lastError != null && outputs == null) {
                    throw lastError
                }
                
                println("✅ Model inference completed. Outputs count: ${outputs?.size ?: 0}")
                
                withContext(Dispatchers.Main) {
                    _isProcessing.value = false
                    
                    if (outputs != null && outputs.isNotEmpty()) {
                        val result = extractStringFromTensor(outputs[0])
                        if (result != null) {
                            _anonymizedText.value = result
                        } else {
                            // Fallback: regex-based masking
                            _anonymizedText.value = maskSensitiveText(text)
                        }
                    } else {
                        _errorMessage.value = "Model returned no output. Please check model configuration."
                        _showingError.value = true
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _isProcessing.value = false
                    _errorMessage.value = "Anonymization failed: ${e.message}\n\nPlease ensure:\n1. The model is correctly loaded\n2. Input format matches model requirements\n3. You have internet connection for initial model download"
                    _showingError.value = true
                }
            }
        }
    }
    
    private fun createInputTensorsFromText(text: String, dataType: String): Array<Tensor> {
        println("🔤 Tokenizing text...")
        
        val maxLength = modelMaxLength
        val shape = intArrayOf(1, maxLength)
        
        val rawBytes = text.toByteArray(Charsets.UTF_8)
        lastInputBytes = ByteArray(maxLength) { index ->
            if (index < rawBytes.size) rawBytes[index] else 0
        }
        lastInputByteCount = minOf(rawBytes.size, maxLength)
        
        return when (dataType) {
            "uint8", "int8" -> {
                val (bytes, attentionMask, _) = bytesWithMask(text, maxLength)
                println("   Byte count: ${bytes.size} (raw: $lastInputByteCount)")
                println("   First 10 bytes: ${bytes.take(10)}")
                arrayOf(
                    createByteTensor(bytes, shape, "input_ids", dataType),
                    createByteTensor(attentionMask, shape, "attention_mask", dataType)
                )
            }
            else -> {
                val (tokenIds, attentionMask) = tokenizeTextWithMask(text, maxLength)
                println("   Token count: ${tokenIds.size}")
                println("   First 10 tokens: ${tokenIds.take(10)}")
                arrayOf(
                    createIntegerTensor(tokenIds, shape, "input_ids", dataType),
                    createIntegerTensor(attentionMask, shape, "attention_mask", dataType)
                )
            }
        }
    }
    
    private fun createIntegerTensor(
        values: IntArray,
        shape: IntArray,
        label: String,
        dataType: String
    ): Tensor {
        val resolvedType = DataType.Companion.from(dataType)
        println("✅ Created Tensor for $label with dataType: $dataType")
        return Tensor.Companion.of(values, resolvedType, shape, false)
    }
    
    private fun createByteTensor(
        values: ByteArray,
        shape: IntArray,
        label: String,
        dataType: String
    ): Tensor {
        val resolvedType = DataType.Companion.from(dataType)
        println("✅ Created Tensor for $label with dataType: $dataType")
        return Tensor.Companion.of(values, resolvedType, shape, false)
    }
    
    private fun bytesWithMask(text: String, maxLength: Int): Triple<ByteArray, ByteArray, Int> {
        val rawBytes = text.toByteArray(Charsets.UTF_8)
        val bytes = rawBytes.take(maxLength).toByteArray()
        val values = ByteArray(maxLength) { index ->
            if (index < bytes.size) bytes[index] else 0
        }
        val attentionMask = ByteArray(maxLength) { index ->
            if (index < bytes.size) 1 else 0
        }
        return Triple(values, attentionMask, bytes.size)
    }
    
    private fun tokenizeTextWithMask(text: String, maxLength: Int): Pair<IntArray, IntArray> {
        val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val tokenIds = mutableListOf<Int>()
        
        // Add [CLS] token
        tokenIds.add(0)
        
        // Convert words to token IDs (simplified hash-based approach)
        for (word in words.take(maxLength - 2)) {
            val tokenId = (word.hashCode() and 0x7FFFFFFF) % 50000 + 1
            tokenIds.add(tokenId)
        }
        
        // Add [SEP] token
        tokenIds.add(2)
        
        val realTokenCount = tokenIds.size
        val attentionMask = IntArray(realTokenCount) { 1 }
        
        // Pad or truncate to maxLength
        val paddedTokenIds = if (tokenIds.size < maxLength) {
            val base = tokenIds.toIntArray()
            base + IntArray(maxLength - tokenIds.size) { 1 } // Pad with [PAD] = 1
        } else {
            tokenIds.take(maxLength).toIntArray()
        }
        
        val paddedAttentionMask = if (attentionMask.size < maxLength) {
            attentionMask + IntArray(maxLength - attentionMask.size) { 0 }
        } else {
            attentionMask.take(maxLength).toIntArray()
        }
        
        return Pair(paddedTokenIds, paddedAttentionMask)
    }
    
    private fun runModel(model: ZeticMLangeModel, inputs: Array<Tensor>): Array<Tensor> {
        println("🚀 Running model inference...")
        return try {
            model.run(inputs)
        } catch (e: Exception) {
            val errorText = e.message ?: ""
            // Some models only expect a single input_ids tensor
            if ((errorText.contains("input_ids") || errorText.contains("expected: 1")) && inputs.size > 1) {
                println("⚠️ Model reported missing input_ids. Retrying with input_ids only.")
                model.run(arrayOf(inputs[0]))
            } else if (errorText.contains("attention_mask") && inputs.size == 1) {
                println("⚠️ Model reported missing attention_mask. Retrying with input_ids + attention_mask.")
                model.run(inputs)
            } else {
                throw e
            }
        }
    }
    
    private fun extractStringFromTensor(tensor: Tensor): String? {
        println("📤 Extracting string from output tensor...")
        
        val buffer = try {
            tensor.data
        } catch (e: Exception) {
            println("⚠️ Could not access tensor.data: ${e.message}")
            return null
        }

        val bytes = ByteArray(buffer.remaining())
        buffer.duplicate().get(bytes)

        val stringValue = String(bytes, Charsets.UTF_8).trim()
        if (stringValue.isNotEmpty()) {
            return stringValue
        }

        println("⚠️ Tensor output was empty or not UTF-8. Falling back to regex masking.")
        return null
    }
    
    private fun applyPlaceholders(text: String, spans: List<Span>): String {
        if (spans.isEmpty()) return text
        
        val utf8 = text.toByteArray(Charsets.UTF_8)
        val replacements = mutableListOf<Replacement>()
        
        for (span in spans) {
            val placeholder = placeholderByLabel[span.label] ?: continue
            val start = minOf(span.start, utf8.size)
            val end = minOf(span.end, utf8.size)
            if (start >= end) continue
            
            // Convert byte indices to string indices (approximate)
            val startIndex = text.length * start / utf8.size.coerceAtLeast(1)
            val endIndex = text.length * end / utf8.size.coerceAtLeast(1)
            replacements.add(Replacement(startIndex, endIndex, placeholder))
        }
        
        if (replacements.isEmpty()) return text
        
        // Apply from end to avoid offset shifts
        val sorted = replacements.sortedByDescending { it.start }
        var result = text
        for (repl in sorted) {
            if (repl.start < result.length && repl.end <= result.length) {
                result = result.substring(0, repl.start) + repl.placeholder + result.substring(repl.end)
            }
        }
        return result
    }
    
    private fun logTopPredictions(
        floats: FloatArray,
        seqLen: Int,
        classCount: Int,
        labels: List<String>,
        limit: Int
    ) {
        val maxPos = minOf(seqLen, limit)
        println("🧪 Raw model output preview (top-3 per position):")
        for (i in 0 until maxPos) {
            val start = i.toInt() * classCount
            if (start + classCount > floats.size) break
            
            val scored = (0 until classCount).map { c ->
                Pair(c, floats[start + c])
            }.sortedByDescending { it.second }.take(3)
            
            val entries = scored.map { (idx, score) ->
                val label = if (idx < labels.size) labels[idx] else "C$idx"
                "$label:${String.format("%.3f", score)}"
            }
            println("  [$i] ${entries.joinToString(", ")}")
        }
    }
    
    private fun maskSensitiveText(text: String): String {
        var result = text
        
        val patterns = listOf(
            Pattern.compile("(?:[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,})", Pattern.CASE_INSENSITIVE) to "[Email]",
            Pattern.compile("(?:\\+?\\d{1,2}[\\s.-]?)?(?:\\(?\\d{3}\\)?[\\s.-]?)\\d{3}[\\s.-]?\\d{4}", Pattern.CASE_INSENSITIVE) to "[Phone number]",
            Pattern.compile("\\b(?:\\d[ -]*?){13,16}\\b", Pattern.CASE_INSENSITIVE) to "[Credit card]",
            Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b", Pattern.CASE_INSENSITIVE) to "[SSN]",
            Pattern.compile("\\b(?:NRP|nrp)\\b", Pattern.CASE_INSENSITIVE) to "[NRP]"
        )
        
        for ((pattern, placeholder) in patterns) {
            result = pattern.matcher(result).replaceAll(placeholder)
        }
        
        return result
    }
    
    private data class Span(val start: Int, val end: Int, val label: String, val score: Float)
    private data class Replacement(val start: Int, val end: Int, val placeholder: String)
}

