package com.zeticai.whisper

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

class AudioSampler(
    private val onAudioReady: (FloatArray) -> Unit
) {

    private val minBufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT
    )

    @SuppressLint("MissingPermission")
    private val audioRecord = AudioRecord(
        MediaRecorder.AudioSource.DEFAULT,
        SAMPLE_RATE,
        CHANNEL_CONFIG,
        AUDIO_FORMAT,
        minBufferSize
    )

    @Volatile
    private var isRecording = false
    private var recordThread: Thread? = null

    fun startRecording() {
        if (isRecording) return
        isRecording = true

        recordThread = Thread {
            val temp = FloatArray(minBufferSize)
            val collected = ArrayList<Float>()

            try {
                audioRecord.startRecording()
                while (isRecording) {
                    val read = audioRecord.read(
                        temp, 0, temp.size, AudioRecord.READ_BLOCKING
                    )
                    if (read > 0) {
                        for (i in 0 until read) collected.add(temp[i])
                    }
                }
            } finally {
                audioRecord.stop()
                onAudioReady(collected.toFloatArray())
            }
        }.also { it.start() }
    }

    fun stopRecording() {
        isRecording = false
    }

    fun release() {
        audioRecord.release()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_FLOAT
    }
}
