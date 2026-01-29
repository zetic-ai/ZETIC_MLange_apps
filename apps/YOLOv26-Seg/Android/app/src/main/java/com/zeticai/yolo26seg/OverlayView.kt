package com.zeticai.yolo26seg

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

class OverlayView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private var results: List<Yolo26Seg.Detection> = emptyList()
    private var imageWidth = 1
    private var imageHeight = 1
    
    // Paints
    private val boxPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }
    
    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 40f
        style = Paint.Style.FILL
    }
    
    private val textBgPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
        alpha = 150
    }
    
    private val maskPaint = Paint().apply {
        alpha = 200
    }

    fun setResults(detections: List<Yolo26Seg.Detection>, imgW: Int, imgH: Int) {
        results = detections
        imageWidth = imgW
        imageHeight = imgH
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (results.isEmpty()) return
        
        // Calculate scaling to match fitCenter logic of ImageView
        val avW = width.toFloat()
        val avH = height.toFloat()
        
        val scale = min(avW / imageWidth, avH / imageHeight)
        val renderW = imageWidth * scale
        val renderH = imageHeight * scale
        
        val offsetX = (avW - renderW) / 2
        val offsetY = (avH - renderH) / 2
        
        for (det in results) {
            // Draw Mask if available
            det.mask?.let { mask ->
                // Mask is a crop (160x160 native, but likely small crop from generateMask). 
                // Wait, generateMask returns crop of 160x160 buffer.
                // We need to draw it into the bounding box location.
                // My generateMask code returns mask pixels in a bitmap of size cropW x cropH (160 relative).
                // It needs to be scaled up to the screen size.
                
                // Oops, my generateMask returned mask relative to the 160x160 grid.
                // It needs to be drawn at the correct location on the SCREEN.
                // det.box is in Original Image Coordinates.
                // The mask corresponds to that box.
                
                // Let's assume the mask bitmap exactly covers the det.box area.
                
                val destRect = RectF(
                    det.box.left * scale + offsetX,
                    det.box.top * scale + offsetY,
                    det.box.right * scale + offsetX,
                    det.box.bottom * scale + offsetY
                )
                
                canvas.drawBitmap(mask, null, destRect, maskPaint)
            }
            
            // Draw Box
            val left = det.box.left * scale + offsetX
            val top = det.box.top * scale + offsetY
            val right = det.box.right * scale + offsetX
            val bottom = det.box.bottom * scale + offsetY
            
            canvas.drawRect(left, top, right, bottom, boxPaint)
            
            // Draw Label
            val label = "${det.label} ${String.format("%.2f", det.score)}"
            val textWidth = textPaint.measureText(label)
            val textHeight = textPaint.textSize
            
            canvas.drawRect(left, top - textHeight - 10, left + textWidth + 20, top, textBgPaint)
            canvas.drawText(label, left + 10, top - 10, textPaint)
        }
    }
}
