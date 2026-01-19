package com.zeticai.yolov26

import android.graphics.Bitmap
import android.graphics.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ImageUtils {
    fun resize(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    /**
     * Prepares input for YOLO model:
     * 1. Resizes to target size (640x640)
     * 2. Normalizes pixel values (0-255 -> 0.0-1.0)
     * 3. Converts to CHW format Float Buffer
     */
    fun prepareInput(bitmap: Bitmap, targetWidth: Int = 640, targetHeight: Int = 640): FloatArray {
        val resized = resize(bitmap, targetWidth, targetHeight)
        val pixels = IntArray(targetWidth * targetHeight)
        resized.getPixels(pixels, 0, targetWidth, 0, 0, targetWidth, targetHeight)

        val chwArray = FloatArray(3 * targetWidth * targetHeight)
        val area = targetWidth * targetHeight

        for (i in pixels.indices) {
            val pixel = pixels[i]
            
            // Extract RGB (Ignore Alpha)
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            // CHW Layout:
            // Plane 0: R
            // Plane 1: G
            // Plane 2: B
            chwArray[i] = r
            chwArray[area + i] = g
            chwArray[area * 2 + i] = b
        }
        
        return chwArray
    }
}
