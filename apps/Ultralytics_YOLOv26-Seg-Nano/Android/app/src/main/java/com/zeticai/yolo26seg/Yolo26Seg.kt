package com.zeticai.yolo26seg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import com.zeticai.mlange.core.model.ZeticMLangeModel
import com.zeticai.mlange.core.tensor.Tensor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

class Yolo26Seg(private val context: Context) {
    private var model: ZeticMLangeModel? = null
    private val tracker = com.zeticai.yolo26seg.tracking.ByteTracker()
    
    init {
        model = ZeticMLangeModel(
            context,
            Constants.ZETIC_ACCESS_TOKEN,
            Constants.MODEL_ID
        )
        android.util.Log.d("Yolo26Seg", ">>> LOADED MODEL ID: ${Constants.MODEL_ID} <<<")
    }

    data class Detection(
        val label: String,
        val score: Float,
        val box: RectF, // In original image coordinates
        val mask: Bitmap?, // Binary mask (alpha channel or black/white)
        val id: Int? = null
    )

    fun close() {
        // model?.close()
    }

    fun inference(bitmap: Bitmap): List<Detection> {
        val model = this.model ?: return emptyList()

        // 1. Preprocessing (Letterbox + Normalize)
        val (inputTensor, scaleInfo) = preprocess(bitmap)
        
        // 2. Run Inference
        val resultTensors = model.run(arrayOf(inputTensor))
        
        if (resultTensors.isNullOrEmpty()) {
            return emptyList()
        }
        
        // 3. Extract ByteBuffers from Output Tensors
        val outputBuffers = resultTensors.map { it.data }.toTypedArray()
        
        // 4. Postprocessing
        return postprocess(outputBuffers, scaleInfo, bitmap.width, bitmap.height)
    }

    private data class ScaleInfo(val scale: Float, val padX: Float, val padY: Float)

    private fun preprocess(bitmap: Bitmap): Pair<Tensor, ScaleInfo> {
        val w = bitmap.width
        val h = bitmap.height
        val size = Constants.INPUT_SIZE
        
        // Letterbox
        val scale = min(size.toFloat() / w, size.toFloat() / h)
        val newW = (w * scale).toInt()
        val newH = (h * scale).toInt()
        
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        val inputBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(inputBitmap)
        // Gray padding 127/114
        canvas.drawColor(Color.rgb(114, 114, 114)) 
        
        // Center Padding (Ultralytics Standard)
        val padX = (size - newW) / 2f
        val padY = (size - newH) / 2f
        
        canvas.drawBitmap(scaledBitmap, padX, padY, null)
        
        val buffer = ByteBuffer.allocateDirect(1 * 3 * size * size * 4)
        buffer.order(ByteOrder.nativeOrder())
        
        val pixels = IntArray(size * size)
        inputBitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        
        for (c in 0 until 3) { // CHW
             for (i in pixels.indices) {
                val pixel = pixels[i]
                val value = when (c) {
                    0 -> (pixel shr 16) and 0xFF
                    1 -> (pixel shr 8) and 0xFF
                    else -> pixel and 0xFF
                }
                buffer.putFloat(value / 255.0f)
            }
        }
        buffer.flip()
        
        val tensor = Tensor.of(buffer.asFloatBuffer(), shape = intArrayOf(1, 3, size, size))
        return Pair(tensor, ScaleInfo(scale, padX, padY))
    }

    private fun postprocess(outputs: Array<ByteBuffer>, scaleInfo: ScaleInfo, origW: Int, origH: Int): List<Detection> {
        if (outputs.isEmpty()) return emptyList()
        
        val detBuffer = outputs[0]
        detBuffer.rewind()
        val detFloats = FloatArray(detBuffer.remaining() / 4).also { detBuffer.asFloatBuffer().get(it) }
        
        val numFloats = detFloats.size
        
        // Case 1: E2E Optimized Output (1, 300, 38) => 11400 floats
        // Format: [x1, y1, x2, y2, score, class, mask0...mask31]
        // 4+1+1+32 = 38
        if (numFloats == 300 * 38) {
            val stride = 38
            val numDetections = 300
            
            val allDetections = ArrayList<RawDetection>()
            
            for (i in 0 until numDetections) {
                val offset = i * stride
                val score = detFloats[offset + 4]
                
                if (score > Constants.CONF_THRESHOLD) {
                    val x1 = detFloats[offset + 0]
                    val y1 = detFloats[offset + 1]
                    val x2 = detFloats[offset + 2]
                    val y2 = detFloats[offset + 3]
                    val clsId = detFloats[offset + 5].toInt()
                    
                    val maskCoeffs = FloatArray(32)
                    for (k in 0 until 32) {
                        maskCoeffs[k] = detFloats[offset + 6 + k]
                    }
                    
                    allDetections.add(RawDetection(RectF(x1, y1, x2, y2), score, clsId, maskCoeffs))
                }
            }
            
            val afterNms = nms(allDetections)
            
            // Convert to STrack for tracking
            val inputStracks = ArrayList<com.zeticai.yolo26seg.tracking.STrack>()
            
            for (det in afterNms) {
                 val w = det.box.width()
                 val h = det.box.height()
                 val tlwh = floatArrayOf(det.box.left, det.box.top, w, h)
                 val strack = com.zeticai.yolo26seg.tracking.STrack(tlwh, det.score, det.classId)
                 strack.maskCoeffs = det.maskCoeffs // Pass coefficients for later mask gen
                 inputStracks.add(strack)
            }
            
            val trackedStracks = tracker.update(inputStracks) // Use class-level tracker
            
            // Proto Masks (Output[1])
            val proto: FloatArray?
            val protoH = 160
            val protoW = 160
            if (outputs.size > 1) {
                val protoBuf = outputs[1]
                protoBuf.rewind()
                proto = FloatArray(protoBuf.remaining() / 4).also { protoBuf.asFloatBuffer().get(it) }
            } else {
                proto = null
            }
            
            val finalDetections = ArrayList<Detection>()
            
            for (t in trackedStracks) {
                // Only output activated tracks
                if (t.isActivated) {
                    // Get Box from Track State (Kalman smoothed)
                    val rect = t.getRectF()
                    
                    // Scale Box to Screen
                    val finalBox = scaleBox(rect, scaleInfo, origW, origH)
                    
                    // Generate Mask (using latest coefficients and proto)
                    var maskBitmap: Bitmap? = null
                    if (proto != null && t.maskCoeffs != null) {
                        // Note: generateMask needs the box in Model Coordinates (rect), not Scaled Coordinates
                        maskBitmap = generateMask(t.maskCoeffs!!, proto, protoH, protoW, rect)
                    }
                
                    finalDetections.add(Detection(
                        Constants.LABELS.getOrElse(t.classId) { "unknown" },
                        t.score,
                        finalBox,
                        maskBitmap,
                        t.trackId
                    ))
                }
            }
            
            return finalDetections
        }
        
        // Case 2: Raw Output (1, 116, 8400) => 974400 floats
        // ... (Keep existing Raw Logic as fallback if needed, or remove to simplify if we are sure it's E2E)
        // For robustness, I'll log mismatches.
        
        android.util.Log.e("Yolo26Seg", "Unknown output shape. Size: $numFloats. Expected 11400 (E2E) or 974400 (Raw).")
        return emptyList()
    }

    private data class RawDetection(val box: RectF, val score: Float, val classId: Int, val maskCoeffs: FloatArray)

    private fun sigmoid(x: Float): Float {
        return (1.0f / (1.0f + exp(-x)))
    }

    private fun nms(dets: List<RawDetection>): List<RawDetection> {
        val keep = ArrayList<RawDetection>()
        // Group by class
        val byClass = dets.groupBy { it.classId }
        
        for ((_, classDets) in byClass) {
            val sorted = classDets.sortedByDescending { it.score }
            val active = ArrayList(sorted)
            
            while (active.isNotEmpty()) {
                val current = active.removeAt(0)
                keep.add(current)
                
                val toRemove = ArrayList<RawDetection>()
                for (other in active) {
                    if (iou(current.box, other.box) > Constants.IOU_THRESHOLD) {
                        toRemove.add(other)
                    }
                }
                active.removeAll(toRemove)
            }
        }
        return keep
    }

    private fun iou(a: RectF, b: RectF): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        
        val w = max(0f, right - left)
        val h = max(0f, bottom - top)
        val inter = w * h
        
        val areaA = (a.right - a.left) * (a.bottom - a.top)
        val areaB = (b.right - b.left) * (b.bottom - b.top)
        
        return inter / (areaA + areaB - inter + 1e-6f)
    }

    private fun generateMask(coeffs: FloatArray, proto: FloatArray, protoH: Int, protoW: Int, box640: RectF): Bitmap? {
        val sx = protoW / Constants.INPUT_SIZE.toFloat()
        val sy = protoH / Constants.INPUT_SIZE.toFloat()
        
        val bx1 = (max(0f, box640.left) * sx).toInt()
        val by1 = (max(0f, box640.top) * sy).toInt()
        val bx2 = (min(Constants.INPUT_SIZE.toFloat(), box640.right) * sx).toInt().coerceAtMost(protoW - 1)
        val by2 = (min(Constants.INPUT_SIZE.toFloat(), box640.bottom) * sy).toInt().coerceAtMost(protoH - 1)
        
        if (bx2 <= bx1 || by2 <= by1) return null
        
        val cropW = bx2 - bx1
        val cropH = by2 - by1
        
        val maskPixels = IntArray(cropW * cropH)
        
        for (y in 0 until cropH) {
            val py = by1 + y
            for (x in 0 until cropW) {
                val px = bx1 + x
                
                var sum = 0f
                for (k in 0 until 32) {
                    // proto index = k * (160*160) + py * 160 + px
                    val valProto = proto[k * (protoH * protoW) + py * protoW + px]
                    // WARNING: Proto is usually 32 channels. k should index channels.
                    // Typical Proto layout: [32, 160, 160] (Chan, H, W)
                    // k * (160*160) + py*160 + px is Correct for CHW
                    sum += coeffs[k] * valProto
                }
                
                val maskVal = sigmoid(sum)
                if (maskVal > Constants.MASK_THRESHOLD) {
                    maskPixels[y * cropW + x] = Color.argb(128, 255, 0, 0) // Semi-transparent Red
                } else {
                    maskPixels[y * cropW + x] = Color.TRANSPARENT
                }
            }
        }
        
        return Bitmap.createBitmap(maskPixels, cropW, cropH, Bitmap.Config.ARGB_8888)
    }

    private fun scaleBox(box: RectF, scaleInfo: ScaleInfo, origW: Int, origH: Int): RectF {
        // Inverse Letterbox: (x - pad) / scale
        val x1 = (box.left - scaleInfo.padX) / scaleInfo.scale
        val y1 = (box.top - scaleInfo.padY) / scaleInfo.scale
        val x2 = (box.right - scaleInfo.padX) / scaleInfo.scale
        val y2 = (box.bottom - scaleInfo.padY) / scaleInfo.scale
        
        return RectF(
            max(0f, min(origW.toFloat(), x1)),
            max(0f, min(origH.toFloat(), y1)),
            max(0f, min(origW.toFloat(), x2)),
            max(0f, min(origH.toFloat(), y2))
        )
    }
}
