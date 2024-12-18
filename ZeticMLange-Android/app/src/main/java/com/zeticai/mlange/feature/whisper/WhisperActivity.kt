package com.zeticai.mlange.feature.whisper

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.zeticai.mlange.R
import java.io.File
import java.io.FileOutputStream
import kotlin.time.measureTime
import kotlin.time.measureTimedValue

class WhisperActivity : AppCompatActivity() {
    private val audioText: TextView by lazy {
        findViewById(R.id.audio_text)
    }

    private val encoder by lazy {
        WhisperEncoder(this, "whisper-tiny-encoder")
    }

    private val decoder by lazy {
        WhisperDecoder(
            50258,
            50264,//50259-en,//50264-ko
            50359,
            50257,
            this,
            "whisper-tiny-decoder"
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
                val outputs =
                    measureTimedValue {
                        encoder.process(features)
                    }.also {
                        println("encode inference : ${it.duration}")
                    }.value
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
