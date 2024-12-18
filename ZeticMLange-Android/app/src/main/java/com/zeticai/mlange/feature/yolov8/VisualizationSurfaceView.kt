package com.zeticai.mlange.feature.yolov8

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.util.AttributeSet
import com.zeticai.mlange.common.vision.PreviewSurfaceView
import java.io.BufferedReader
import java.io.InputStreamReader

class VisualizationSurfaceView(
    context: Context,
    attrSet: AttributeSet,
) : PreviewSurfaceView(context, attrSet) {
    private val yoloClasses: List<String> by lazy {
        context.assets.open("coco.yaml").use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readLines().drop(17).map {
                    if (it.contains(':')) it.split(':')[1].trim() else it
                }
            }
        }
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_OVER)
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
        yoloResult: YoloResult?
    ) {
        if (!holder.surface.isValid) return

        if (yoloResult == null) return

        if (yoloResult.value.isEmpty()) return

        if (yoloClasses.isEmpty()) return

        val canvas = holder.lockCanvas()
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        yoloResult.value.forEach {
            val rect = Rect(
                ((it.box.xMin / 480) * layoutParams.width).toInt(),
                ((it.box.yMin / 640) * layoutParams.height).toInt(),
                ((it.box.xMax / 480) * layoutParams.width).toInt(),
                ((it.box.yMax / 640) * layoutParams.height).toInt(),
            )
            paint.style = Paint.Style.STROKE
            paint.color = getColorByClassId(it.classId)
            canvas.drawRect(rect, paint)

            val text = "${yoloClasses[it.classId]} ${"%.2f".format(it.confidence)}"
            val textRect = Rect(
                rect.left,
                (rect.top - paint.textSize).toInt(),
                (rect.left + paint.measureText(text)).toInt(),
                rect.top,
            )
            paint.style = Paint.Style.FILL_AND_STROKE
            canvas.drawRect(textRect, paint)

            paint.color = Color.BLACK
            canvas.drawText(
                text,
                rect.left.toFloat(),
                rect.top - 10f,
                paint
            )
        }
        holder.unlockCanvasAndPost(canvas)
    }

    private fun getColorByClassId(classId: Int): Int {
        val r = ((classId + 72) * 1717 % 256)
        val g = ((classId + 7) * 33 % 126 + 70)
        val b = ((classId + 47) * 107 % 256)
        return Color.argb(255, r, g, b)
    }
}
