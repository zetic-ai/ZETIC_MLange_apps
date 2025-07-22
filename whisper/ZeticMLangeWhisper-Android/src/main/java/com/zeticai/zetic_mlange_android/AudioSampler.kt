package com.zeticai.zetic_mlange_android

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log

class AudioSampler(
    private val processAudioData: (FloatArray) -> Unit
) {
    private val minBufferSize: Int = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT
    )

    @SuppressLint("MissingPermission")
    private val audioRecord: AudioRecord = AudioRecord(
        MediaRecorder.AudioSource.DEFAULT,
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT,
        minBufferSize
    )

    private val readBuffer: FloatArray = FloatArray(NUM_SAMPLES)

    private var isRecording = false

    @SuppressLint("MissingPermission")
    fun startRecording() {
        Log.i("AudioSamplerDebug", "▶ startRecording() called")
        if (isRecording) {
            Log.w("AudioSamplerDebug", "Already recording, return")
            return
        }
        isRecording = true
        Log.d("AudioSamplerDebug", "re samples, preview: ${readBuffer.take(5)}")

        readBuffer.fill(0.0f)
        try {
            audioRecord.startRecording()
            Log.d("AudioSamplerDebug", "re samples, preview: ${readBuffer.take(5)}")

            var samplesRead = 0
            while (samplesRead < NUM_SAMPLES) {
                val remaining: Int = NUM_SAMPLES - samplesRead
                val readSize = remaining.coerceAtMost(minBufferSize)
                val result: Int = audioRecord.read(
                    readBuffer,
                    samplesRead,
                    readSize,
                    AudioRecord.READ_BLOCKING
                )
                if (result > 0) {
                    samplesRead += result
                }
            }
            Log.d("AudioSamplerDebug", "re samples, preview: ${readBuffer.take(5)}")


            processAudioData(readBuffer)
        } finally {
            audioRecord.stop()
            isRecording = false
        }
    }

    fun stopRecording() {
        if (audioRecord.state == AudioRecord.STATE_INITIALIZED) {
            audioRecord.stop()
        }
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT
        const val NUM_SAMPLES = SAMPLE_RATE * 5
    }
}
