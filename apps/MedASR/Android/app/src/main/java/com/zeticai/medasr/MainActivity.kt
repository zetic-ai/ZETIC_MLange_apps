package com.zeticai.medasr

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.zeticai.mlange.core.model.ZeticMLangeModel
import com.zeticai.mlange.core.tensor.Tensor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.GlobalScope

class MainActivity : AppCompatActivity() {

    private lateinit var btnRecord: Button
    private lateinit var btnPickFile: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvResult: TextView

    private var model: ZeticMLangeModel? = null
    private var isRecording = false
    private var audioRecord: AudioRecord? = null
    private val sampleRate = 16000 // Standard for ASR
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            startRecording()
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val filePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            processAudioFile(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnRecord = findViewById(R.id.btnRecord)
        btnPickFile = findViewById(R.id.btnPickFile)
        tvStatus = findViewById(R.id.tvStatus)
        tvResult = findViewById(R.id.tvResult)

        btnRecord.setOnClickListener {
            if (isRecording) {
                stopRecording()
            } else {
                checkPermissionAndRecord()
            }
        }

        btnPickFile.setOnClickListener {
            filePickerLauncher.launch("audio/*")
        }

        // Setup Global Crash Logging
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("MedASR", "CRASH: Uncaught Exception in thread ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        initModel()
    }


    private lateinit var ctcDecoder: CTCDecoder

    private fun initModel() {
        tvStatus.text = "Status: Initializing..."
        CoroutineScope(Dispatchers.IO).launch {
            // 1. Init Model (Network Call)
            try {
                withContext(Dispatchers.Main) { tvStatus.text = "Status: Loading Model..." }
                model = ZeticMLangeModel(this@MainActivity, Constants.MLANGE_PERSONAL_ACCESS_TOKEN, Constants.MODEL_NAME)
                withContext(Dispatchers.Main) { tvStatus.text = "Status: Model Loaded" }
            } catch (e: Exception) {
                Log.e("MedASR", "Error loading Model", e)
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Status: Model Error: ${e.message}"
                }
                return@launch // Stop if model fails
            }

            // 2. Init Decoder (Local Asset)
            try {
                withContext(Dispatchers.Main) { tvStatus.text = "Status: Loading Decoder..." }
                val vocabPath = copyAssetToInternalStorage(this@MainActivity, "tokenizer.json")
                val vocabString = File(vocabPath).readText()
                ctcDecoder = CTCDecoder(vocabString)
                Log.d("MedASR", "Tokenizer Vocab Size: ${ctcDecoder.getVocabSize()}")
                
                withContext(Dispatchers.Main) { tvStatus.text = "Status: Ready (Model+Decoder)" }
            } catch (e: Exception) {
                Log.e("MedASR", "Error loading Decoder", e)
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Status: Decoder Error: ${e.message}"
                }
            }
        }
    }

    private fun copyAssetToInternalStorage(context: Context, filename: String): String {
        val file = File(context.filesDir, filename)
        if (!file.exists()) {
            context.assets.open(filename).use { inputStream ->
                java.io.FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return file.absolutePath
    }

    private fun checkPermissionAndRecord() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Toast.makeText(this, "Audio Record Init Failed", Toast.LENGTH_SHORT).show()
                return
            }

            audioRecord?.startRecording()
            isRecording = true
            btnRecord.text = "Stop Recording"
            tvStatus.text = "Status: Recording..."
            
            CoroutineScope(Dispatchers.IO).launch {
                val data = ArrayList<Short>()
                val buffer = ShortArray(1024)
                while (isRecording) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        for (i in 0 until read) {
                            data.add(buffer[i])
                        }
                    }
                }
                processAudioData(data.toShortArray())
            }

        } catch (e: Exception) {
            Log.e("MedASR", "Error starting recording", e)
            Toast.makeText(this, "Error starting recording", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopRecording() {
        isRecording = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        btnRecord.text = "Record Audio"
        tvStatus.text = "Status: Processing..."
    }

    private fun processAudioFile(uri: android.net.Uri) {
        tvStatus.text = "Status: Processing File..."
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                
                if (bytes != null) {
                    val info = AudioUtils.readWav(bytes)
                    if (info != null) {
                        // Resample to 16kHz & Convert to Mono Float [-1, 1]
                        val rawFloats = AudioUtils.resample(info, 16000)
                        
                        // Apply Standard Normalization (Zero Mean, Unit Var)
                        val normalizedFloats = AudioUtils.normalize(rawFloats)
                        
                        // Extract Mel Spectrogram using User's Extractor
                        val features = MedasrFeatureExtractor.extract(normalizedFloats, 16000)
                        
                        // Flatten 2D array [Time, 128] -> 1D FloatArray
                        val flattenedFeatures = features.inputFeatures.flatMap { it.asIterable() }.toFloatArray()
                        
                        runModel(flattenedFeatures, rawFloats.size, features.attentionMask)
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Invalid WAV File", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "File too short", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Status: File Error"
                    Toast.makeText(this@MainActivity, "Error reading file", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun processAudioData(audioData: ShortArray) {
        if (model == null) {
            withContext(Dispatchers.Main) {
                tvStatus.text = "Status: Model not ready"
            }
            return
        }

        try {
            // Convert ShortArray to correct format using AudioUtils
            // Mic input is already 16kHz Mono (configured in startRecording)
            val info = AudioUtils.AudioInfo(16000, 1, audioData)
            
            // Convert to Float [-1, 1]
            val rawFloats = AudioUtils.resample(info, 16000)
            
            // Apply Standard Normalization (Zero Mean, Unit Var) - Core HF Step
            val normalizedFloats = AudioUtils.normalize(rawFloats)
            
            // Extract Mel Spectrogram using User's Extractor
            val features = MedasrFeatureExtractor.extract(normalizedFloats, 16000)
            
            // Flatten 2D array [Time, 128] -> 1D FloatArray
            val flattenedFeatures = features.inputFeatures.flatMap { it.asIterable() }.toFloatArray()
            
            runModel(flattenedFeatures, rawFloats.size, features.attentionMask)
            
        } catch (e: Exception) {
            Log.e("MedASR", "Processing Error", e)
             withContext(Dispatchers.Main) {
                tvStatus.text = "Status: Processing Failed"
            }
        }
    }

    private fun runModel(floatArray: FloatArray, rawSampleCount: Int, attentionMask: IntArray? = null) {
        CoroutineScope(Dispatchers.Default).launch {
             try {
                // Calculate Time Steps based on flattened features (Time * 128)
                val timeStepsInput = floatArray.size / 128
                
                // Reshape to [1, Time, 128]
                val shape = intArrayOf(1, timeStepsInput, 128)
                
                // DEBUG: Log Input Stats and First 50 Values
                var sum = 0.0
                var sumSqInput = 0.0
                for (f in floatArray) {
                    sum += f
                    sumSqInput += f * f
                }
                val mean = sum / floatArray.size
                val std = Math.sqrt(sumSqInput / floatArray.size - mean * mean)
                
                Log.d("MedASR", "Audio Samples Count: $rawSampleCount")
                Log.d("MedASR", "Audio Duration: ${rawSampleCount / 16000.0} seconds")
                Log.d("MedASR", "Input Features Shape: [1, $timeStepsInput, 128]")
                
                Log.d("MedASR", "Input Features (Mel) Stats: Mean=$mean, Std=$std, Range=[${floatArray.minOrNull()}, ${floatArray.maxOrNull()}]")
                Log.d("MedASR", "Input Features First 50: ${floatArray.take(50).joinToString()}")
                Log.d("MedASR", "Input Features Last 50: ${floatArray.takeLast(50).joinToString()}")
                
                // Mask: Use provided mask or default to all 1s
                val maskArray = if (attentionMask != null) {
                    attentionMask.map { it.toLong() }.toLongArray()
                } else {
                    LongArray(timeStepsInput) { 1L }
                }
                // Calculate Sum of Mask
                 var maskSum = 0L
                for (m in maskArray) maskSum += m
                Log.d("MedASR", "Attention Mask Sum: $maskSum (Total Length: ${maskArray.size})")
                
                val maskTensor = Tensor.of(maskArray, shape = intArrayOf(1, timeStepsInput))
                
                val inputTensor = Tensor.of(floatArray, shape = shape)
                val outputs = model?.run(arrayOf(inputTensor, maskTensor))
                
                if (outputs != null && outputs.isNotEmpty()) {
                    val outputTensor = outputs[0]
                    val dataSize = outputTensor.data.remaining()
                    
                    // Conformer CTC Logits (Float32) [1, Time, VocabSize]
                    val floatCount = dataSize / 4
                    Log.d("MedASR", "Output Float Count: $floatCount")
                    
                    // ROBUST VOCAB DETECTION:
                    var vocabSize = 512 // Default
                    // processor_config.json says 613. Add common ones.
                    val candidates = intArrayOf(512, 513, 613, 614, 1024, 1025)
                    
                    for (cand in candidates) {
                        val rem = floatCount % cand
                        if (rem == 0) {
                            vocabSize = cand
                            Log.d("MedASR", "Vocab Candidate Match: $cand")
                            break
                        } else {
                            Log.d("MedASR", "Vocab Candidate Mismatch: $cand (Rem: $rem)")
                        }
                    }
                    
                    val timeSteps = floatCount / vocabSize
                    Log.d("MedASR", "Detected Vocab: $vocabSize (TimeSteps: $timeSteps)")
                    
                    val logits = FloatArray(floatCount)
                    outputTensor.data.asFloatBuffer().get(logits)
                    
                    // DEBUG: Log Logits Stats
                    var minLogit = Float.MAX_VALUE
                    var maxLogit = Float.MIN_VALUE
                    for (l in logits) {
                        if (l < minLogit) minLogit = l
                        if (l > maxLogit) maxLogit = l
                    }
                    Log.d("MedASR", "Logits Range: [$minLogit, $maxLogit]")
                    Log.d("MedASR", "Total Logits Count: ${logits.size}")
                    Log.d("MedASR", "First 100 Logits: ${logits.take(100).joinToString()}")
                    Log.d("MedASR", "Last 200 Logits: ${logits.takeLast(200).joinToString()}")
                    
                    // Perform Argmax to get Token IDs
                    val tokenIds = IntArray(timeSteps)
                    for (t in 0 until timeSteps) {
                        var maxVal = Float.NEGATIVE_INFINITY
                        var maxIdx = 0
                        val offset = t * vocabSize
                        
                        // Safety check for boundary
                        if (offset + vocabSize <= logits.size) {
                            for (v in 0 until vocabSize) {
                                val value = logits[offset + v]
                                if (value > maxVal) {
                                    maxVal = value
                                    maxIdx = v
                                }
                            }
                            tokenIds[t] = maxIdx
                            if (t < 10) {
                                Log.d("MedASR", "Time $t: MaxIdx=$maxIdx, MaxVal=$maxVal")
                            }
                            if (t >= timeSteps - 10) {
                                Log.d("MedASR", "Time $t: MaxIdx=$maxIdx, MaxVal=$maxVal")
                            }
                        }
                    }
                    
                    val distinctIds = tokenIds.toSet()
                    Log.d("MedASR", "Distinct Token IDs Found: ${distinctIds.joinToString()}")
                    
                    Log.d("MedASR", "Logits Range: [$minLogit, $maxLogit]")
                    Log.d("MedASR", "First 100 Logits: ${logits.take(100).joinToString()}")
                    
                    val decodedText = ctcDecoder.decode(tokenIds)
                    
                    // DEBUG: Debug Truncation
                    Log.d("MedASR", "Full Token ID Sequence: ${tokenIds.joinToString(limit = -1)}")
                    // Locate "protocol" or end of current text?
                    // Just print the last 20 tokens (including blanks) to see what happened at the end
                    Log.d("MedASR", "Last 50 Token IDs: ${tokenIds.takeLast(50).joinToString()}")
                    
                    // Cleanup known artifacts
                    val cleanText = decodedText.replace(".end}", "")
                    
                    val resultText = "Decoded: $cleanText"
    
                    withContext(Dispatchers.Main) {
                        tvStatus.text = "Status: Done"
                        tvResult.text = resultText
                    }
                } else {
                     withContext(Dispatchers.Main) {
                        tvStatus.text = "Status: No Output"
                    }
                }
            } catch (e: Exception) {
                Log.e("MedASR", "Inference Error", e)
                 withContext(Dispatchers.Main) {
                    tvStatus.text = "Status: Inference Failed"
                    tvResult.text = e.message
                }
            }
        }
    }
}
