package com.zeticai.yolov26.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.NativeCanvas
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import com.zeticai.yolov26.BoundingBox

@Composable
fun BoundingBoxOverlay(
    boxes: List<BoundingBox>,
    scaleX: Float = 1f,
    scaleY: Float = 1f
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        for (box in boxes) {
            drawBox(box, scaleX, scaleY)
        }
    }
}

fun DrawScope.drawBox(box: BoundingBox, scaleX: Float, scaleY: Float) {
    val colors = listOf(
        Color.Red, Color.Green, Color.Blue, Color.Cyan, Color.Magenta, Color.Yellow
    )
    val color = colors[box.classIndex % colors.size]

    val left = box.x1 * scaleX
    val top = box.y1 * scaleY
    val width = box.w * scaleX
    val height = box.h * scaleY
    
    // Draw Rect
    drawRect(
        color = color,
        topLeft = Offset(left, top),
        size = Size(width, height),
        style = Stroke(width = 5f)
    )
    
    // Draw Text Background
    val paint = Paint().apply {
        this.color = android.graphics.Color.WHITE
        textSize = 40f
        typeface = Typeface.DEFAULT_BOLD
    }
    
    val text = "${box.label} ${String.format("%.2f", box.score)}"
    val textWidth = paint.measureText(text)
    val textHeight = 50f 
    
    drawRect(
        color = color,
        topLeft = Offset(left, top - textHeight),
        size = Size(textWidth + 20f, textHeight)
    )
    
    // Draw Text
    drawContext.canvas.nativeCanvas.drawText(
        text,
        left + 10f,
        top - 10f,
        paint
    )
}
