package com.zeticai.medasr

import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.util.Log

object AudioUtils {

    data class AudioInfo(val sampleRate: Int, val channels: Int, val pcmData: ShortArray)

    // Parse WAV Header
    fun readWav(bytes: ByteArray): AudioInfo? {
        try {
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            
            // Check "RIFF" and "WAVE"
            if (bytes.size < 44) return null
            
            // Format (2 bytes at 20): 1=PCM
            // val format = buffer.getShort(20) // Unused
            val channels = buffer.getShort(22).toInt()
            val sampleRate = buffer.getInt(24)
            val bitsPerSample = buffer.getShort(34).toInt()
            
            // Data starts after header. Header is usually 44 bytes, but can be larger (FMT, DATA chunks).
            // Robust parsing searches for "start of data"
            var pos = 12
            while (pos < bytes.size) {
                val chunkId = String(bytes, pos, 4)
                val chunkSize = buffer.getInt(pos + 4)
                if (chunkId == "data") {
                    pos += 8
                    break
                }
                pos += 8 + chunkSize
            }
            if (pos >= bytes.size) pos = 44 // Fallback if data chunk not found
            
            val pcmBytes = bytes.copyOfRange(pos, bytes.size)
            val shorts = ShortArray(pcmBytes.size / 2)
            ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts)
            
            Log.d("AudioUtils", "Parsed WAV: SR=$sampleRate, Ch=$channels, Bits=$bitsPerSample, Length=${shorts.size}")
            return AudioInfo(sampleRate, channels, shorts)
            
        } catch (e: Exception) {
            Log.e("AudioUtils", "Error parsing WAV", e)
            return null
        }
    }

    // Resample if needed (Linear Interpolation) and convert to Mono Float [-1, 1]
    fun resample(info: AudioInfo, targetRate: Int): FloatArray {
        val src = info.pcmData
        val srcRate = info.sampleRate
        val channels = info.channels
        
        // 1. Convert to Float Mono [-1.0, 1.0]
        val mono = FloatArray(src.size / channels)
        for (i in mono.indices) {
            var sum = 0f
            for (c in 0 until channels) {
                // Convert Short to Float [-1.0, 1.0]
                val sample = src[i * channels + c] / 32768.0f
                sum += sample
            }
            mono[i] = sum / channels // Average channels
        }
        
        if (srcRate == targetRate) {
             return mono
        }
        
        // 2. Resample
        val ratio = srcRate.toDouble() / targetRate
        val outLen = (mono.size / ratio).toInt()
        val out = FloatArray(outLen)
        
        for (i in 0 until outLen) {
            val srcIdx = i * ratio
            val idx0 = srcIdx.toInt()
            val idx1 = if (idx0 + 1 < mono.size) idx0 + 1 else idx0
            val frac = (srcIdx - idx0).toFloat()
            
            // Linear Interpolation
            out[i] = mono[idx0] * (1 - frac) + mono[idx1] * frac
        }
        
        Log.d("AudioUtils", "Resampled from $srcRate to $targetRate. Len: ${mono.size} -> ${out.size}")
        return out
    }

    // Apply Zero-Mean Unit-Variance Normalization (Standard Score)
    fun normalize(input: FloatArray): FloatArray {
        // First convert raw short values (if not already scaled) to range roughly [-1,1] or keep raw?
        // User's python: librosa.load gives [-1,1].
        // Our input here is raw Short values (e.g. 15000.0). 
        // Wav2Vec normalization usually happens ON RAW AUDIO.
        // But (x - mean) / std works regardless of scale (it cancels out).
        
        // Calculate Stats
        var sum = 0.0
        for (f in input) sum += f
        val mean = sum / input.size
        
        var varianceSum = 0.0
        for (f in input) {
            val diff = f - mean
            varianceSum += diff * diff
        }
        val std = Math.sqrt(varianceSum / input.size)
        val stdSafe = if (std < 1e-7) 1e-7 else std
        
        val out = FloatArray(input.size)
        for (i in input.indices) {
            out[i] = ((input[i] - mean) / stdSafe).toFloat()
        }
        
        return out
    }
}
