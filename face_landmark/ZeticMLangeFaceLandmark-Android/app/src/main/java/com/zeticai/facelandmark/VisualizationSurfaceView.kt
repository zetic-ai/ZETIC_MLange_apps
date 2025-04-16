package com.zeticai.facelandmark

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
import com.zeticai.mlange.feature.facelandmark.FaceLandmarkResult

class VisualizationSurfaceView(context: Context, attrSet: AttributeSet) :
    PreviewSurfaceView(context, attrSet) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.GREEN
        textSize = 40f
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSPARENT)
    }

    fun visualize(
        faceDetectionResult: FaceDetectionResults?,
        faceLandmarkResult: FaceLandmarkResult?,
        inputSize: Size,
        isRotated: Boolean
    ) {
        if (!holder.surface.isValid)
            return

        if (faceDetectionResult == null)
            return
        if (faceDetectionResult.faceDetectionResults.isEmpty())
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

        val detectionRect = faceDetectionResult.faceDetectionResults.map {
            val convertedMin = transformCoordToTargetCoord(
                Pair(
                    (1 - it.bbox.xMin) * inputRect.width(),
                    it.bbox.yMin * inputRect.height()
                ), inputRect, targetRect
            )
            val convertedMax = transformCoordToTargetCoord(
                Pair(
                    (1 - it.bbox.xMax) * inputRect.width(),
                    it.bbox.yMax * inputRect.height()
                ), inputRect, targetRect
            )

            RectF(
                convertedMin.first,
                convertedMin.second,
                convertedMax.first,
                convertedMax.second,
            )
        }.also {
            it.zip(faceDetectionResult.faceDetectionResults).forEach { result ->
                canvas.drawRect(result.first, paint)
                canvas.drawText(
                    "Conf. : ${result.second.score}",
                    result.first.right,
                    result.first.top - 10f,
                    paint
                )
            }
        }

        val scaleFactor = 1.2f
        val detectionBox = detectionRect[0]
        faceLandmarkResult?.landmarks?.forEach {
            canvas.drawCircle(
                detectionBox.left + (it.x * detectionBox.width() * scaleFactor),
                (detectionBox.top / scaleFactor) + (it.y * detectionBox.height() * scaleFactor),
                1f,
                paint
            )
        }

        holder.unlockCanvasAndPost(canvas)
    }
}
