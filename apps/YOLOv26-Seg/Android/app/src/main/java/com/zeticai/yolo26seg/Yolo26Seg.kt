package com.zeticai.yolo26seg

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.zeticai.mlange.core.model.ZeticMLangeModel
import com.zeticai.mlange.core.tensor.Tensor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.io.File
import java.io.FileOutputStream
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import java.util.PriorityQueue

class Yolo26Seg(private val context: Context) {
    private var model: ZeticMLangeModel? = null
    
    init {
        model = ZeticMLangeModel(
            context,
            Constants.ZETIC_ACCESS_TOKEN,
            Constants.MODEL_ID
        )
    }

    data class Detection(
        val label: String,
        val score: Float,
        val box: RectF, // In original image coordinates
        val mask: Bitmap? // Binary mask (alpha channel or black/white)
    )


    fun close() {
        // model?.deinit() // Error: Unresolved reference. Assuming auto-managed or no explicit deinit in this version.
    }

    fun inference(bitmap: Bitmap): List<Detection> {
        val model = this.model ?: return emptyList()

        // 1. Preprocessing (Letterbox + Normalize)
        val (inputBuffer, scaleInfo) = preprocess(bitmap)
        
        // 2. Prepare Tensor
        // inputBuffer is Direct ByteBuffer (NCHW float32)
        // Use asFloatBuffer() for zero-copy if possible, or direct buffer
        inputBuffer.rewind()
        val floatBuffer = inputBuffer.asFloatBuffer()
        
        // Use named argument 'shape' to avoid positional mismatch given the signature of Tensor.of
        val inputTensor = Tensor.of(floatBuffer, shape = intArrayOf(1, 3, Constants.INPUT_SIZE, Constants.INPUT_SIZE))
        
        // 3. Run Inference
        val resultTensors = model.run(arrayOf(inputTensor))
        
        if (resultTensors.isNullOrEmpty()) {
            return emptyList()
        }
        
        // 4. Extract ByteBuffers from Output Tensors
        val outputBuffers = resultTensors.map { it.data }.toTypedArray()
        
        // 5. Postprocessing
        return postprocess(outputBuffers, scaleInfo, bitmap.width, bitmap.height)
    }

    private data class ScaleInfo(val scale: Float, val padX: Int, val padY: Int)

    private fun preprocess(bitmap: Bitmap): Pair<ByteBuffer, ScaleInfo> {
        val w = bitmap.width
        val h = bitmap.height
        
        // Letterbox
        val scale = min(Constants.INPUT_SIZE.toFloat() / w, Constants.INPUT_SIZE.toFloat() / h)
        val newW = (w * scale).toInt()
        val newH = (h * scale).toInt()
        
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newW, newH, true)
        val inputBitmap = Bitmap.createBitmap(Constants.INPUT_SIZE, Constants.INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(inputBitmap)
        canvas.drawColor(Color.rgb(127, 127, 127)) // Gray padding 127 (matching HF/Python)
        
        // Top-left padding
        val padX = 0
        val padY = 0
        canvas.drawBitmap(scaledBitmap, padX.toFloat(), padY.toFloat(), null)
        
        // Convert to Float Buffer (NCHW, 0-1)
        val buffer = ByteBuffer.allocateDirect(1 * 3 * Constants.INPUT_SIZE * Constants.INPUT_SIZE * 4)
        buffer.order(ByteOrder.nativeOrder())
        
        val pixels = IntArray(Constants.INPUT_SIZE * Constants.INPUT_SIZE)
        inputBitmap.getPixels(pixels, 0, Constants.INPUT_SIZE, 0, 0, Constants.INPUT_SIZE, Constants.INPUT_SIZE)
        
        // Planar layout: RRR...GGG...BBB...
        for (c in 0 until 3) { // 0=R, 1=G, 2=B
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
        
        return Pair(buffer, ScaleInfo(scale, padX, padY))
    }

    private fun postprocess(outputs: Array<ByteBuffer>, scaleInfo: ScaleInfo, origW: Int, origH: Int): List<Detection> {
        val allDetections = ArrayList<RawDetection>()
        
        // Process 3 heads
        val strides = listOf(8, 16, 32)
        val grids = listOf(80, 40, 20)
        
        for (i in 0 until 3) {
            val idx = i * 3
            val grid = grids[i]
            
            val boxesBuf = outputs[idx]
            val clsBuf = outputs[idx + 1]
            val maskBuf = outputs[idx + 2]
            
            boxesBuf.rewind()
            clsBuf.rewind()
            maskBuf.rewind()
            
            // Read buffers into FloatArrays for easier access
            val boxes = FloatArray(boxesBuf.remaining() / 4).also { boxesBuf.asFloatBuffer().get(it) }
            val cls = FloatArray(clsBuf.remaining() / 4).also { clsBuf.asFloatBuffer().get(it) }
            val maskCoeffs = FloatArray(maskBuf.remaining() / 4).also { maskBuf.asFloatBuffer().get(it) }
            
            // Iterate over grid (H, W)
            for (h in 0 until grid) {
                for (w in 0 until grid) {
                    val anchorIdx = h * grid + w
                    
                    // Scores: find max class score
                    var maxScore = 0f
                    var maxClassId = -1
                    
                    val clsOffset = anchorIdx * 80
                    for (c in 0 until 80) {
                        // Apply Sigmoid to logits
                        val logit = cls[clsOffset + c]
                        val score = sigmoid(logit)
                        if (score > maxScore) {
                            maxScore = score
                            maxClassId = c
                        }
                    }
                    
                    if (maxScore > Constants.CONF_THRESHOLD) {
                        // Decode Box (Anchor-Free LTRB)
                        val boxOffset = anchorIdx * 4
                        val l = boxes[boxOffset + 0]
                        val t = boxes[boxOffset + 1]
                        val r = boxes[boxOffset + 2]
                        val b = boxes[boxOffset + 3]
                        
                        val anchorX = w + 0.5f
                        val anchorY = h + 0.5f
                        
                        val stride = Constants.INPUT_SIZE / grid.toFloat()
                        
                        val xMinRaw = (anchorX - l) * stride
                        val yMinRaw = (anchorY - t) * stride
                        val xMaxRaw = (anchorX + r) * stride
                        val yMaxRaw = (anchorY + b) * stride
                        
                        // Clamp and assign to expected variable names
                        val xMin = max(0f, min(Constants.INPUT_SIZE.toFloat(), xMinRaw))
                        val yMin = max(0f, min(Constants.INPUT_SIZE.toFloat(), yMinRaw))
                        val xMax = max(0f, min(Constants.INPUT_SIZE.toFloat(), xMaxRaw))
                        val yMax = max(0f, min(Constants.INPUT_SIZE.toFloat(), yMaxRaw))
                        
                        // Extract Mask Coeffs
                        val maskOffset = anchorIdx * 32
                        val coeffs = FloatArray(32)
                        for (k in 0 until 32) coeffs[k] = maskCoeffs[maskOffset + k]
                        
                        allDetections.add(RawDetection(
                            RectF(xMin, yMin, xMax, yMax),
                            maxScore,
                            maxClassId,
                            coeffs
                        ))
                    }
                }
            }
        }
        
        // NMS (Class-wise)
        val afterNms = nms(allDetections)
        
        // Process Masks (Proto)
        val protoBuf = outputs[9]
        protoBuf.rewind()
        val proto = FloatArray(protoBuf.remaining() / 4).also { protoBuf.asFloatBuffer().get(it) }
        // Proto shape: 32 x 160 x 160
        val protoH = 160
        val protoW = 160
        
        val finalDetections = ArrayList<Detection>()
        
        for (det in afterNms) {
            // Generate Mask
            val maskBitmap = generateMask(det.maskCoeffs, proto, protoH, protoW, det.box)
            
            // Scale Box back to original image
            val finalBox = scaleBox(det.box, scaleInfo, origW, origH)
            
            finalDetections.add(Detection(
                Constants.LABELS.getOrElse(det.classId) { "unknown" },
                det.score,
                finalBox,
                maskBitmap 
            ))
        }
        
        return finalDetections
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
        
        // Create 160x160 crop bitmap
        return Bitmap.createBitmap(maskPixels, cropW, cropH, Bitmap.Config.ARGB_8888)
    }

    private fun scaleBox(box: RectF, scaleInfo: ScaleInfo, origW: Int, origH: Int): RectF {
        val x1 = (box.left - scaleInfo.padX) / scaleInfo.scale
        val y1 = (box.top - scaleInfo.padY) / scaleInfo.scale
        val x2 = (box.right - scaleInfo.padX) / scaleInfo.scale
        val y2 = (box.bottom - scaleInfo.padY) / scaleInfo.scale
        return RectF(
            max(0f, x1), max(0f, y1), 
            min(origW.toFloat(), x2), min(origH.toFloat(), y2)
        )
    }
}
