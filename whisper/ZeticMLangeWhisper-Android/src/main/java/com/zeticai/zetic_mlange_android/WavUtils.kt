/* WavUtils.kt -----------------------------------------------------------*/
package com.zeticai.zetic_mlange_android

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavUtils {
    /**
     * 16-bit PCM, 16 kHz, 1-ch WAV 파일을 FloatArray(-1.0…1.0) 로 반환
     */
    fun loadMono16kPcm(path: String): FloatArray {
        val raf = RandomAccessFile(File(path), "r")
        raf.seek(44)                                 // WAV 헤더 44바이트 스킵
        val bytes = ByteArray((raf.length() - 44).toInt())
        raf.readFully(bytes)
        raf.close()

        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val shorts = ShortArray(bytes.size / 2)
        bb.asShortBuffer().get(shorts)

        return FloatArray(shorts.size) { i -> shorts[i] / 32768f }
    }
}
