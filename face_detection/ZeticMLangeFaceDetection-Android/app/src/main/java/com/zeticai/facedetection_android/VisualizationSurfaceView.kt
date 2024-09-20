package com.zeticai.facedetection_android

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.util.AttributeSet
import com.zetic.ZeticMLangeFeature.type.Box
import com.zetic.ZeticMLangeFeature.type.FaceDetectionResult
import com.zetic.ZeticMLangeFeature.type.FaceLandmarkResult

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
        faceDetectionResult: FaceDetectionResult?
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
        holder.unlockCanvasAndPost(canvas)
    }
}
