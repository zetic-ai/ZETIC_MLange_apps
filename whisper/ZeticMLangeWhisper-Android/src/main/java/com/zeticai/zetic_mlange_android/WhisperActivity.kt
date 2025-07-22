package com.zeticai.zetic_mlange_android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.zeticai.zetic_mlange_android.R
import com.zeticai.mlange.feature.automaticspeechrecognition.whisper.WhisperWrapper
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.time.measureTime
import android.util.Log

fun loadFloatArrayFromFile(path: String): FloatArray {
    val fileBytes = File(path).readBytes()
    val buffer = ByteBuffer.wrap(fileBytes).order(ByteOrder.LITTLE_ENDIAN)
    val floatArray = FloatArray(fileBytes.size / 4)
    for (i in floatArray.indices) {
        floatArray[i] = buffer.getFloat()
    }
    return floatArray
}


class WhisperActivity : AppCompatActivity() {


    fun dumpFloatBuffer(tag: String, buf: ByteBuffer, count: Int = 10) {
        val fb = buf.duplicate()
            .order(ByteOrder.LITTLE_ENDIAN)
            .asFloatBuffer()

        val arr = FloatArray(fb.remaining())
        fb.get(arr)

        Log.d(tag, "float count = ${arr.size}, preview = ${arr.take(count)}")
    }

    private val audioText: TextView by lazy {
        findViewById(R.id.audio_text)
    }

    private val encoder by lazy {
        WhisperEncoder(this, "313d31795c9b4d0494deb4d3c6666bb0")
    }

    private val decoder by lazy {
        Log.d("TEMP", this.applicationInfo.nativeLibraryDir)
        WhisperDecoder(
            50258,
            50264,//50259-en,//50264-ko
            50359,
            50257,
            this,
//            "dc5f58d792f848e9a3af146d6d5b5d7e",
            "24e8af927886472cbc3244bfaecde84f"
        )
    }

    private val whisper by lazy {
        WhisperWrapper(copyAssetToInternalStorage(this, "vocab.json"))
    }

    private val audioSampler: AudioSampler by lazy {
        AudioSampler {
            runOnUiThread {
                audioText.text = "processing..."
            }
            measureTime {
                val features = whisper.process(it)

                val outputs = encoder.process(features)

                val generatedIds = decoder.generateTokens(outputs)
                println(generatedIds)
                val text = whisper.decodeToken(generatedIds.toIntArray(), true)
                println(text)
                runOnUiThread {
                    audioText.text = text
                }
            }.also {
                println("total process : $it")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_whisper)


        if (!checkPermission())
            requestPermission()

        findViewById<Button>(R.id.audio_button).setOnClickListener {
            audioText.text = "listening..."
            Thread {
                audioSampler.startRecording()
            }.start()
        }
//        findViewById<Button>(R.id.audio_button).setOnClickListener {
//            audioText.text = "loading wav…"
//            Thread {
//                val wavPath = copyAssetToInternalStorage(this, "output_whisper.wav")
//
//                val bufferFloat = WavUtils.loadMono16kPcm(wavPath)
//
//                runWhisper(bufferFloat)
//            }.start()
//        }

    }


    private fun runWhisper(bufferFloat: FloatArray) {
        runOnUiThread { audioText.text = "processing…" }

        val inputStream = assets.open("float_data.bin")
        val bytes = inputStream.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val floatBuffer = buffer.asFloatBuffer()
        val size = floatBuffer.limit()  // 전체 float 개수
        val floatArray = FloatArray(size)

        // 현재 position부터 읽으므로 꼭 rewind() 또는 position(0) 해줘야 함
        floatBuffer.rewind()  // 또는 floatBuffer.position(0)
        floatBuffer.get(floatArray)

        val sampleCnt = bufferFloat.size
        val (_, mask1500Buf) = MaskUtils.buildMasks(sampleCnt)

        val features = whisper.process(bufferFloat)          // Mel 80×3000
        val encOut = encoder.process(features)             // Encoder 출력


        val path = copyAssetToInternalStorage(this, "encoded_features.bin")


        val bytes2 = File(path).readBytes()
        val dest = ByteBuffer.wrap(bytes2)
        dest.order(ByteOrder.LITTLE_ENDIAN)
        dest.put(bytes2)
        dumpFloatBuffer("EncOut", dest, 20)  // 앞 20개 찍기

        val ids = decoder.generateTokens(dest)
        val text = whisper.decodeToken(ids.toIntArray(), true)

        runOnUiThread { audioText.text = text }
    }


    override fun onDestroy() {
        super.onDestroy()
        audioSampler.stopRecording()
        encoder.close()
        decoder.close()
        whisper.deinit()
    }

    private fun copyAssetToInternalStorage(context: Context, assetFileName: String): String {
        val inputStream = context.assets.open(assetFileName)
        val outFile = File(context.filesDir, assetFileName)
        val outputStream = FileOutputStream(outFile)
        val newFilePath = outFile.absolutePath

        val buffer = ByteArray(1024)
        var read: Int
        while ((inputStream.read(buffer).also { read = it }) != -1) {
            outputStream.write(buffer, 0, read)
        }

        outputStream.flush()
        inputStream.close()
        outputStream.close()

        return newFilePath
    }

    private fun checkPermission(): Boolean {
        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }


    companion object {
        private const val PERMISSION_REQUEST_CODE = 200
    }
}