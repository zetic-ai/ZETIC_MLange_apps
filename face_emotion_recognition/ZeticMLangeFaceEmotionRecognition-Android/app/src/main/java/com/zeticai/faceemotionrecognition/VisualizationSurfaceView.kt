package com.zeticai.faceemotionrecognition

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
import android.util.Size
import com.zeticai.mlange.feature.facedetection.FaceDetectionResults
import com.zeticai.mlange.feature.faceemotionrecognition.FaceEmotionRecognitionResult

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
        textSize = 40f
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
        faceEmotionRecognitionResult: FaceEmotionRecognitionResult?,
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

        val text =
            "Emotion : ${faceEmotionRecognitionResult.emotion}\nConf. : ${faceEmotionRecognitionResult.confidence}"

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
        val left = targetRect.width() / 2 - rectWidth / 2
        val right = targetRect.width() / 2 + rectWidth / 2
        val bottom = targetRect.height() - outerPadding
        val top = bottom - totalHeight - (padding * 2)

        val backgroundRect = transformRectToTargetRect(RectF(left, top, right, bottom), inputRect, targetRect)
        canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, backgroundPaint)

        var y = bottom - totalHeight - (padding * 2) - (textPaint.ascent() * 1.5f)
        textLines.forEach { line ->
            canvas.drawText(line, left + padding, y, textPaint)
            y += lineHeight
        }

        holder.unlockCanvasAndPost(canvas)
    }
}
