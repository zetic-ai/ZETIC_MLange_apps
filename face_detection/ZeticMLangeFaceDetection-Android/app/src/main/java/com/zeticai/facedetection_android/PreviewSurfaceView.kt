package com.zeticai.facedetection_android

import android.content.Context
import android.util.AttributeSet
import android.util.Size
import android.view.Gravity
import android.view.SurfaceView
import android.widget.FrameLayout

open class PreviewSurfaceView(context: Context, attrSet: AttributeSet) :
    SurfaceView(context, attrSet) {

    fun updateSizeKeepRatio(size: Size) {
        val metrics = resources.displayMetrics
        val screenSize = Size(metrics.heightPixels, metrics.widthPixels)
        updateSize(
            Size(
                screenSize.width * if (size.width > size.height) (size.width / size.height) else (size.height / size.width),
                screenSize.width
            )
        )
    }

    private fun updateSize(size: Size) {
        layoutParams = FrameLayout.LayoutParams(size.width, size.height, Gravity.CENTER)
    }
}