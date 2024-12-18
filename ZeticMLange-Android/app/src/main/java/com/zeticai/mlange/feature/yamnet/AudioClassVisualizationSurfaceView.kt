package com.zeticai.mlange.feature.yamnet

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.SurfaceView

class AudioClassVisualizationSurfaceView(context: Context, attrSet: AttributeSet) :
    SurfaceView(context, attrSet) {
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

    fun visualize(classes: List<AudioClass>) {
        if (!holder.surface.isValid)
            return

        val canvas = holder.lockCanvas()
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        classes.forEachIndexed { index, audioClass ->
            canvas.drawText(
                "${audioClass.name} : ${audioClass.score}",
                100f, 100f + (index * 50f),
                paint
            )
        }

        holder.unlockCanvasAndPost(canvas)
    }
}