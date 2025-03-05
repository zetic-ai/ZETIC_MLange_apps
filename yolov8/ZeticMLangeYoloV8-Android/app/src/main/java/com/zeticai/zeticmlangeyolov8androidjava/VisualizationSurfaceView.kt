package com.zeticai.zeticmlangeyolov8androidjava

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.util.AttributeSet
import com.zeticai.mlange.feature.yolov8.YoloResult
import java.io.BufferedReader
import java.io.InputStreamReader
import android.util.Size
import kotlin.math.min

class VisualizationSurfaceView(
    context: Context,
    attrSet: AttributeSet,
) : PreviewSurfaceView(context, attrSet) {
    private val yoloClasses: List<String> by lazy {
        context.assets.open("coco.yaml").use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                reader.readLines().drop(17).map {
                    it//.split(": ")[1]
                }
            }
        }
    }

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
        yoloResult: YoloResult?, yoloInputSize: Size, isRotated: Boolean
    ) {
        if (!holder.surface.isValid) return

        if (yoloResult == null) return

        if (yoloResult.value.isEmpty()) return

        if (yoloClasses.isEmpty()) return

        val canvas = holder.lockCanvas()
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val yoloInputSizePair = if (!isRotated) {
            yoloInputSize.width to yoloInputSize.height
        } else {
            yoloInputSize.height to yoloInputSize.width
        }
        val targetSizePair = (layoutParams.width to layoutParams.height)

        val (yoloWidth, yoloHeight) = if (!isRotated) {
            yoloInputSize.width to yoloInputSize.height
        } else {
            yoloInputSize.height to yoloInputSize.width
        }

        yoloResult.value.forEach {
            val convertedMin = transformCoordToTargetCoord(Pair(it.box.xMin, it.box.yMin), yoloInputSizePair, targetSizePair)
            val convertedMax = transformCoordToTargetCoord(Pair(it.box.xMax, it.box.yMax), yoloInputSizePair, targetSizePair)

            val rect = Rect(
                convertedMin.first,
                convertedMin.second,
                convertedMax.first,
                convertedMax.second,
            )
            canvas.drawRect(rect, paint)
            canvas.drawText(
                "${yoloClasses[it.classId]} : ${it.confidence}",
                rect.left.toFloat(),
                rect.top - 10f,
                paint
            )
        }
        holder.unlockCanvasAndPost(canvas)
    }

    private fun transformCoordToTargetCoord(coord: Pair<Float, Float>, originalSize: Pair<Int, Int>, targetSize: Pair<Int, Int>): Pair<Int, Int> {
        val originalWidth = originalSize.first
        val originalHeight = originalSize.second
        val originalCenterX = originalWidth / 2
        val originalCenterY = originalHeight / 2

        val targetWidth = targetSize.first
        val targetHeight = targetSize.second
        val targetCenterX = targetWidth / 2
        val targetCenterY = targetHeight / 2

        val widthRatio = targetWidth.toFloat() / originalWidth.toFloat()
        val heightRatio = targetHeight.toFloat() / originalHeight.toFloat()

        val resizeFactor = min(widthRatio, heightRatio)
        val retX = ((coord.first - originalCenterX) * resizeFactor + targetCenterX).toInt()
        val retY = ((coord.second - originalCenterY) * resizeFactor + targetCenterY).toInt()

        return Pair(retX, retY)
    }
}
