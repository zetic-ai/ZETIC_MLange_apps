package com.zeticai.yolo26seg.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class VideoDecoder(context: Context, uri: Uri) {
    private val extractor = MediaExtractor()
    private var decoder: MediaCodec? = null
    private var inputBufferIndex = -1
    private var outputBufferIndex = -1
    private val bufferInfo = MediaCodec.BufferInfo()
    private var isEOS = false

    var width = 0
    var height = 0
    var durationMs = 0L
    var frameRate = 30

    init {
        extractor.setDataSource(context, uri, null)
        val trackIndex = selectVideoTrack(extractor)
        if (trackIndex >= 0) {
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: "video/avc"
            
            width = if (format.containsKey(MediaFormat.KEY_WIDTH)) format.getInteger(MediaFormat.KEY_WIDTH) else 0
            height = if (format.containsKey(MediaFormat.KEY_HEIGHT)) format.getInteger(MediaFormat.KEY_HEIGHT) else 0
            durationMs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) / 1000 else 0
            if (format.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                frameRate = format.getInteger(MediaFormat.KEY_FRAME_RATE)
            }

            // Configure codec
            decoder = MediaCodec.createDecoderByType(mime)
            // We want direct ByteBuffer access, so no surface!
            // However, most decoders produce YUV.
            decoder?.configure(format, null, null, 0)
            decoder?.start()
        } else {
            Log.e("VideoDecoder", "No video track found")
        }
    }

    private fun selectVideoTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("video/") == true) {
                return i
            }
        }
        return -1
    }

    // Synchronous nextFrame
    // Note: This is simplified. Real robust implementation needs timeouts and loop.
    fun nextFrame(): Bitmap? {
        if (decoder == null) return null

        while (!isEOS) {
            // 1. Feed Input
            // Check if we have an input buffer available
            val inputIndex = decoder!!.dequeueInputBuffer(10000)
            if (inputIndex >= 0) {
                val inputBuffer = decoder!!.getInputBuffer(inputIndex)
                if (inputBuffer != null) {
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        decoder!!.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEOS = true
                    } else {
                        val presentationTimeUs = extractor.sampleTime
                        decoder!!.queueInputBuffer(inputIndex, 0, sampleSize, presentationTimeUs, 0)
                        extractor.advance()
                    }
                }
            }

            // 2. Poll Output
            val outputIndex = decoder!!.dequeueOutputBuffer(bufferInfo, 10000)
            if (outputIndex >= 0) {
                // Determine format
                // val outputFormat = decoder!!.outputFormat
                // val width = outputFormat.getInteger(MediaFormat.KEY_WIDTH)
                
                // Get Image
                // We didn't configure a surface, so we use getInputImage/getOutputImage logic or ByteBuffer
                // Older API uses ByteBuffers. API 21+ uses Image.
                
                // NOTE: Getting RGB Bitmap from MediaCodec YUV is tricky.
                // Best robust way on Android without RenderScript/OpenGL is YuvImage -> JPEG -> Bitmap (Slow but working)
                // Or semi-planar to RGBA conversion in Kotlin (Slow).
                // Or simply: output to Surface, use PixelCopy (Fastest, but requires Surface).
                
                // Given "Optimizing", we really should use Surface + PixelCopy or OpenGL.
                // But PixelCopy requires UI thread / Looper context usually.
                // Let's try Image -> YuvImage -> Bitmap first. It might still be faster than MediaMetadataRetriever seeking.
                
                val image = decoder!!.getOutputImage(outputIndex)
                var bitmap: Bitmap? = null
                
                if (image != null) {
                    bitmap = yuvToBitmap(image)
                    image.close()
                }
                
                decoder!!.releaseOutputBuffer(outputIndex, false)
                
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    isEOS = true
                }
                
                if (bitmap != null) return bitmap
            } else if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // handled automatically next time
                val newFormat = decoder!!.outputFormat
                width = newFormat.getInteger(MediaFormat.KEY_WIDTH)
                height = newFormat.getInteger(MediaFormat.KEY_HEIGHT)
            } else if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                // Keep looping if not EOS?
                // If we waited 10ms and got nothing, break to avoid blocking forever?
                // But we need a frame!
                // If we are at EOS and try again later, loop might exit.
            }
        }
        return null
    }

    private fun yuvToBitmap(image: android.media.Image): Bitmap? {
        // NV21 conversion
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // U and V are swapped
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        // Downscale while decoding to save memory?
        // Let's decode full size usually
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    fun release() {
        try {
            decoder?.stop()
            decoder?.release()
            extractor.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
