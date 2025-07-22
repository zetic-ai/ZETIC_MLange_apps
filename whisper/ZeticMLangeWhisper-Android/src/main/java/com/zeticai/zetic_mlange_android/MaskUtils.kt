package com.zeticai.zetic_mlange_android

import java.nio.ByteBuffer
import java.nio.ByteOrder

object MaskUtils {
    private const val HOP = 160     // 16 kHz × 10 ms
    private const val MAX_FRAME = 3000
    private const val ENC_FRAME = 1500

    /* IntArray → Direct ByteBuffer (int32, little-endian) */
    private fun IntArray.toBB(): ByteBuffer {
        val bb = ByteBuffer.allocateDirect(size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)

        bb.asIntBuffer().put(this)  // 값 써넣기
        bb.rewind()                 // 포인터 0 으로
        return bb                   // ★ ByteBuffer 그대로 반환
    }

    /** PCM 샘플 수 ➜ (decoderMask3000, encoderMask1500)  **both int32** */
    fun buildMasks(sampleCount: Int): Pair<ByteBuffer, ByteBuffer> {
        val frames = ((sampleCount + HOP - 1) / HOP).coerceAtMost(MAX_FRAME)

        val mask3000 = IntArray(MAX_FRAME) { idx -> if (idx < frames) 1 else 0 }
        val mask1500 = IntArray(ENC_FRAME) { idx -> mask3000[idx * 2] }

        return Pair(mask3000.toBB(), mask1500.toBB())
    }
}
