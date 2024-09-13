package com.zeticai.faceemotionrecognition

import android.content.Context
import android.util.AttributeSet
import android.util.Size
import android.view.SurfaceView
import android.widget.FrameLayout

open class PreviewSurfaceView(context: Context, attrSet: AttributeSet) :
    SurfaceView(context, attrSet) {

    fun updateSizeKeepRatio(size: Size) {
        val metrics = resources.displayMetrics
        val screenSize = Size(metrics.widthPixels, metrics.heightPixels)
        updateSize(
            Size(
                screenSize.width,
                screenSize.width * if (size.width > size.height) (size.width / size.height) else (size.height / size.width)
            )
        )
    }

    private fun updateSize(size: Size) {
        layoutParams = FrameLayout.LayoutParams(size.width, size.height)
    }
}