package com.zeticai.mlange.feature.yamnet

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AudioSampler(
    private val processAudioData: (ByteBuffer) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val ringBuffer = FloatArray(TOTAL_SAMPLES)
    private var writeIndex = 0
    private var readIndex = 0

    @SuppressLint("MissingPermission")
    fun startRecording() {
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        recordingJob = CoroutineScope(Dispatchers.Default).launch {
            audioRecord?.startRecording()
            val shortBuffer = ShortArray(bufferSize / 2)

            while (isActive) {
                val samplesRead = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: -1
                if (samplesRead > 0) {
                    for (i in 0 until samplesRead) {
                        ringBuffer[writeIndex] = shortBuffer[i] / 32768f
                        writeIndex = (writeIndex + 1) % TOTAL_SAMPLES
                    }

                    if ((writeIndex - readIndex + TOTAL_SAMPLES) % TOTAL_SAMPLES >= OVERLAP_SAMPLES) {
                        val processBuffer = ByteBuffer.allocateDirect(TOTAL_SAMPLES * 4).order(ByteOrder.LITTLE_ENDIAN)
                        for (i in 0 until TOTAL_SAMPLES) {
                            processBuffer.putFloat(ringBuffer[(readIndex + i) % TOTAL_SAMPLES])
                        }
                        processBuffer.rewind()

                        processAudioData(processBuffer)

                        readIndex = (readIndex + OVERLAP_SAMPLES) % TOTAL_SAMPLES
                    }
                }
            }
        }
    }

    fun stopRecording() {
        recordingJob?.cancel()
        audioRecord?.release()
        audioRecord = null
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_SECONDS = 3
        private const val OVERLAP_SECONDS = 1
        private const val TOTAL_SAMPLES = SAMPLE_RATE * BUFFER_SIZE_SECONDS
        private const val OVERLAP_SAMPLES = SAMPLE_RATE * OVERLAP_SECONDS
    }
}