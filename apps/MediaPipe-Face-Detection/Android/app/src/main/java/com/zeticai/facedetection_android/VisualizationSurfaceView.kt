package com.zeticai.facedetection_android

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Size
import com.zeticai.mlange.feature.facedetection.FaceDetectionResults

class VisualizationSurfaceView(context: Context, attrSet: AttributeSet) :
    PreviewSurfaceView(context, attrSet) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.GREEN
        textSize = 30f
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSPARENT)
    }

    fun visualize(
        faceDetectionResult: FaceDetectionResults?,
        inputSize: Size,
        isRotated: Boolean
    ) {
        if (!holder.surface.isValid)
            return

        val inputRect = if (!isRotated) {
            Rect(0, 0, inputSize.width, inputSize.height)
        } else {
            Rect(0, 0, inputSize.height, inputSize.width)
        }
        val targetRect = Rect(
            0, 0, holder.surfaceFrame.width(), holder.surfaceFrame.height()
        )

        val canvas = holder.lockCanvas()
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        faceDetectionResult?.faceDetectionResults?.forEach {
            val convertedMin = transformCoordToTargetCoord(Pair((1 - it.bbox.xMin) * inputRect.width(), it.bbox.yMin * inputRect.height()), inputRect, targetRect)
            val convertedMax = transformCoordToTargetCoord(Pair((1 - it.bbox.xMax) * inputRect.width(), it.bbox.yMax * inputRect.height()), inputRect, targetRect)

            val rect = RectF(
                convertedMin.first,
                convertedMin.second,
                convertedMax.first,
                convertedMax.second,
            )

            canvas.drawRect(rect, paint)
            canvas.drawText(
                "Conf. : ${it.score}",
                rect.right,
                rect.top - 10f,
                paint
            )
        }
        holder.unlockCanvasAndPost(canvas)
    }
}
