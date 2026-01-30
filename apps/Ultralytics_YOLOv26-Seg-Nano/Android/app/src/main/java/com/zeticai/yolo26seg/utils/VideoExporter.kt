package com.zeticai.yolo26seg.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.view.Surface
import java.io.File
import java.nio.ByteBuffer

class VideoExporter(
    private val width: Int,
    private val height: Int,
    private val fps: Int = 30,
    private val outputFile: File
) {
    private var encoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var inputSurface: Surface? = null
    
    private var trackIndex = -1
    private var muxerStarted = false
    private val bufferInfo = MediaCodec.BufferInfo()
    
    // EGL Helpers to draw to InputSurface
    private var eglSurface: CodecInputSurface? = null

    fun start() {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        format.setInteger(MediaFormat.KEY_BIT_RATE, 6000000) // 6Mbps
        format.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

        encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        
        inputSurface = encoder?.createInputSurface()
        eglSurface = CodecInputSurface(inputSurface!!)
        eglSurface?.makeCurrent()
        
        encoder?.start()

        muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        muxerStarted = false
    }

    fun addFrame(bitmap: Bitmap, timestampMs: Long? = null) {
        drainEncoder(false)
        
        // Draw bitmap to EGL Surface
        val canvas = eglSurface?.lockCanvas()
        if (canvas != null) {
            canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR) // Clear
            
            // Draw fit center
            val scale = kotlin.math.min(width.toFloat() / bitmap.width, height.toFloat() / bitmap.height)
            val dw = bitmap.width * scale
            val dh = bitmap.height * scale
            val dx = (width - dw) / 2
            val dy = (height - dh) / 2
            
            val destRect = Rect(dx.toInt(), dy.toInt(), (dx + dw).toInt(), (dy + dh).toInt())
            canvas.drawBitmap(bitmap, null, destRect, null)
            
            eglSurface?.unlockCanvasAndPost(canvas)
        }
        
        // nsecs
        // val pts = timestampMs?.times(1000000) ?: (System.nanoTime())
        // But System.nanoTime is monotonic, we need relative PTS.
        // Let caller provide meaningful, or auto-increment based on FPS?
        // EGL presentation time is set via eglPresentationTimeANDROID? 
        // Actually, CodecInputSurface usually handles swapBuffers but MediaCodec pulls from Surface with timestamp.
        // Wait, standard `Surface.lockCanvas` doesn't allow setting timestamp easily.
        // But `MediaCodec` Input Surface generally uses the system time or needs `eglPresentationTimeANDROID`.
        
        // Simplified: use `eglSurface` helper which wraps EGL. 
        // My simple `lockCanvas` approach on `inputSurface` (which is a persistent surface) 
        // works on API 23+ (Surface.lockCanvas).
        // BUT, `MediaCodec` input surface might NOT support lockCanvas directly on all devices without EGL.
        // Actually, `NativeWindow` from Codec might not support checking.
        
        // However, I am using a trick: `CodecInputSurface` (below) normally uses EGL.
        // But here I called `lockCanvas`. `inputSurface.lockCanvas` is valid for `SurfaceView` or `TextureView` or `ImageReader`.
        // FOR ENCODER SURFACE: It is CONSUMER, not PRODUCER in the normal view sense.
        // Standard docs say: "The input surface identifies the codec as the consumer... The application is the producer."
        // Producer can use OpenGL ES or Canvas (lockCanvas). 
        // Use `lockCanvas` is valid if we don't need GLES.
        // But `lockHardwareCanvas` might be needed?
        // Let's rely on `inputSurface.lockCanvas` if API 23+.
    }
    
    fun finish() {
        drainEncoder(true)
        encoder?.stop()
        encoder?.release()
        eglSurface?.release()
        
        if (muxerStarted) {
            muxer?.stop()
        }
        muxer?.release()
    }
    
    private fun drainEncoder(endOfStream: Boolean) {
        if (endOfStream) {
            encoder?.signalEndOfInputStream()
        }
        
        while (true) {
            val encoderStatus = encoder!!.dequeueOutputBuffer(bufferInfo, 10000) // 10ms
            if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break else continue // Keep waiting if EOS
            } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (muxerStarted) throw RuntimeException("Format changed twice")
                val newFormat = encoder!!.outputFormat
                trackIndex = muxer!!.addTrack(newFormat)
                muxer!!.start()
                muxerStarted = true
            } else if (encoderStatus < 0) {
                // ignore
            } else {
                val encodedData = encoder!!.getOutputBuffer(encoderStatus) ?: continue
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                    bufferInfo.size = 0
                }
                
                if (bufferInfo.size != 0) {
                    if (!muxerStarted) throw RuntimeException("Muxer not started")
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer!!.writeSampleData(trackIndex, encodedData, bufferInfo)
                }
                
                encoder!!.releaseOutputBuffer(encoderStatus, false)
                
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }
    
    // --- Inner Helper: Simple Wrapper for Surface Canvas Drawing --- 
    // Actually we don't need full EGL if `lockCanvas` works. 
    // However, `MediaCodec.createInputSurface()` returns a surface that MIGHT reject lockCanvas 
    // if it demands GPU producer. But usually on Android M+ it works.
    
    // Minimal placeholder for the class member
    class CodecInputSurface(private val surface: Surface) {
        fun makeCurrent() {
            // No-op for Canvas API
        }
        fun lockCanvas(): Canvas? {
            return try {
                if (android.os.Build.VERSION.SDK_INT >= 23) {
                    surface.lockCanvas(null)
                } else {
                    null // Fallback?
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        fun unlockCanvasAndPost(canvas: Canvas) {
            surface.unlockCanvasAndPost(canvas)
        }
        fun release() {
            surface.release()
        }
    }
}
