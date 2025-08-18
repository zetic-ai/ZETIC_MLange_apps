package com.zeticai.whisper

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class WhisperActivity : AppCompatActivity() {

    enum class WhisperState {
        STOPPED,
        LISTENING,
        RUNNING,
    }

    private var state: WhisperState = WhisperState.STOPPED

    private val requestPermissionLauncher =
        registerForActivityResult(RequestPermission()) { isGranted: Boolean ->
            if (isGranted)
                initAudioSampler()
            else {
                Toast.makeText(this, "Mic permission is required. Please enable it in settings.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    private val audioText: TextView by lazy {
        findViewById(R.id.audio_text)
    }
    private val audioButton: Button by lazy {
        findViewById(R.id.audio_button)
    }
    private var feature: WhisperFeature? = null
    private var audioSampler: AudioSampler? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_whisper)

        if (!hasRecordPermission()) requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        else initAudioSampler()

        audioButton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (state == WhisperState.STOPPED) {
                        state = WhisperState.LISTENING
                        audioText.text = "listening..."
                        audioSampler?.startRecording()
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (state == WhisperState.LISTENING) {
                        state = WhisperState.RUNNING
                        audioSampler?.stopRecording()
                        v.performClick()
                    }
                    true
                }

                else -> false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioSampler?.stopRecording()
        audioSampler?.release()
        feature?.close()
    }

    private fun hasRecordPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    private fun initAudioSampler() {
        feature = WhisperFeature(this)
        audioSampler = AudioSampler { pcmData ->
            runOnUiThread { audioText.text = "processing..." }
            val recognized = feature?.run(pcmData)
            state = WhisperState.STOPPED
            runOnUiThread { audioText.text = recognized }
        }
    }
}
