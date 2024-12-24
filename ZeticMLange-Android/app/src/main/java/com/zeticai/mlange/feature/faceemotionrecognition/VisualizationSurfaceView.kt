package com.zeticai.mlange.feature.faceemotionrecognition

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import com.zeticai.mlange.common.vision.PreviewSurfaceView
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

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 60f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        isAntiAlias = true
    }
    private val backgroundPaint = Paint().apply {
        color = Color.BLACK
        alpha = (255 * 0.6).toInt()
        isAntiAlias = true
    }

    private val padding = 24f
    private val cornerRadius = 24f
    private val outerPadding = 30f

    init {
        setBackgroundColor(Color.TRANSPARENT)
        setZOrderOnTop(true)
        holder.setFormat(PixelFormat.TRANSPARENT)
    }

    fun visualize(
        faceDetectionResult: FaceDetectionResults?,
        faceEmotionRecognitionResult: FaceEmotionRecognitionResult?
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

        if (faceEmotionRecognitionResult == null) {
            holder.unlockCanvasAndPost(canvas)
            return
        }

        val text = "Emotion : ${faceEmotionRecognitionResult.emotion}\nConf. : ${faceEmotionRecognitionResult.confidence}"

        val textLines = text.split("\n")
        val textBounds = Rect()
        var maxWidth = 0f
        var totalHeight = 0f
        val lineHeight = textPaint.fontSpacing

        textLines.forEach { line ->
            textPaint.getTextBounds(line, 0, line.length, textBounds)
            maxWidth = maxOf(maxWidth, textBounds.width().toFloat())
            totalHeight += lineHeight
        }

        val rectWidth = maxWidth + (padding * 2)
        val left = width / 2 - rectWidth / 2
        val right = width / 2 + rectWidth / 2
        val bottom = height - outerPadding
        val top = bottom - totalHeight - (padding * 2)

        val backgroundRect = RectF(left, top, right, bottom)
        canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, backgroundPaint)

        var y = bottom - totalHeight - (padding * 2) - (textPaint.ascent() * 1.5f)
        textLines.forEach { line ->
            canvas.drawText(line, left + padding, y, textPaint)
            y += lineHeight
        }

        holder.unlockCanvasAndPost(canvas)
    }
}
