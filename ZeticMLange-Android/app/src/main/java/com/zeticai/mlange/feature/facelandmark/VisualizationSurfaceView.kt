package com.zeticai.mlange.feature.facelandmark

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.util.AttributeSet
import com.zeticai.mlange.common.vision.PreviewSurfaceView
import com.zeticai.mlange.feature.entity.Box
import com.zeticai.mlange.feature.facedetection.FaceDetectionResults

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
        faceLandmarkResult: FaceLandmarkResult?
    ) {
        if (!holder.surface.isValid)
            return

        val canvas = holder.lockCanvas()
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        faceDetectionResult?.faceDetectionResults?.forEach {
            canvas.drawRect(
                Rect(
                    (width - (it.bbox.xMin * width)).toInt(),
                    (it.bbox.yMin * height).toInt(),
                    (width - (it.bbox.xMax * width)).toInt(),
                    (it.bbox.yMax * height).toInt(),
                ),
                paint
            )

            canvas.drawText(
                "Face Detection Conf. : ${it.score}",
                (width - (it.bbox.xMax * width)),
                (it.bbox.yMin * height) - 10f,
                paint
            )
        }

        if ((faceDetectionResult?.faceDetectionResults?.size ?: 0) <= 0) {
            holder.unlockCanvasAndPost(canvas)
            return
        }

        val detectionBox = faceDetectionResult?.faceDetectionResults?.get(0)?.bbox
        if (detectionBox == null) {
            holder.unlockCanvasAndPost(canvas)
            return
        }

        val resizeFactor = 0.2f
        val scaledDetectionBox = Box(
            detectionBox.xMin * width * (1 - resizeFactor),
            detectionBox.yMin * height * (1 - resizeFactor),
            detectionBox.xMax * width * (1 + resizeFactor),
            detectionBox.yMax * height * (1 + resizeFactor)
        )
        val detectionBoxWidth = (scaledDetectionBox.xMax - scaledDetectionBox.xMin)
        val detectionBoxHeight = (scaledDetectionBox.yMax - scaledDetectionBox.yMin)

        faceLandmarkResult?.landmarks?.forEach {
            canvas.drawCircle(
                width - (scaledDetectionBox.xMin + (it.x * detectionBoxWidth)),
                scaledDetectionBox.yMin + (it.y * detectionBoxHeight),
                1f,
                paint
            )
        }

        holder.unlockCanvasAndPost(canvas)
    }
}
