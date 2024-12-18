package com.zeticai.faceemotionrecognition

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.util.AttributeSet
import com.zetic.ZeticMLangeFeature.type.FaceDetectionResult
import com.zetic.ZeticMLangeFeature.type.FaceEmotionRecognitionResult

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
        faceDetectionResult: FaceDetectionResult?,
        faceEmotionRecognitionResult: FaceEmotionRecognitionResult?
    ) {
        if (!holder.surface.isValid)
            return

        val canvas = holder.lockCanvas()
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        faceDetectionResult?.faceDetections?.forEach {
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

        if ((faceDetectionResult?.faceDetections?.size ?: 0) <= 0) {
            holder.unlockCanvasAndPost(canvas)
            return
        }

        val detectionBox = faceDetectionResult?.faceDetections?.get(0)?.bbox
        if (detectionBox == null) {
            holder.unlockCanvasAndPost(canvas)
            return
        }

        if (faceEmotionRecognitionResult == null) {
            holder.unlockCanvasAndPost(canvas)
            return
        }

        canvas.drawText(
            "Emotion : ${faceEmotionRecognitionResult.emotion}    Conf. : ${faceEmotionRecognitionResult.confidence}",
            (width - (detectionBox.xMax * width)),
            (detectionBox.yMin * height) - 70f,
            paint
        )
        holder.unlockCanvasAndPost(canvas)
    }
}
