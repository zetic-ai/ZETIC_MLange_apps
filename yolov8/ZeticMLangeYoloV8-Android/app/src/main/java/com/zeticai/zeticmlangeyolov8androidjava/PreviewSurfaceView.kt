package com.zeticai.zeticmlangeyolov8androidjava

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
        val screenSize = Size(metrics.widthPixels, metrics.heightPixels)

        val rotatedSize = Size(size.height, size.width)
        holder.setFixedSize(rotatedSize.width, rotatedSize.height)

        val imageRatio = rotatedSize.width.toFloat() / rotatedSize.height.toFloat()
        val screenRatio = screenSize.width.toFloat() / screenSize.height.toFloat()

        var newWidth: Int
        var newHeight: Int

        if (imageRatio > screenRatio) {
            newWidth = screenSize.width
            newHeight = (newWidth / imageRatio).toInt()
        } else {
            newHeight = screenSize.height
            newWidth = (newHeight * imageRatio).toInt()
        }

        updateSize(Size(newWidth, newHeight))
    }

    private fun updateSize(size: Size) {
        layoutParams = FrameLayout.LayoutParams(size.width, size.height, Gravity.CENTER)
    }
}
