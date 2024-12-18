package com.zeticai.mlange.common.vision

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import androidx.core.app.ActivityCompat

class CameraProcessor(
    context: Context,
    private val previewSurfaceView: PreviewSurfaceView,
    private val visualizationSurfaceView: PreviewSurfaceView,
    private val onCameraFrame: (ByteArray, Int, Int) -> Unit,
    private val onSurface: (Surface) -> Unit,
    private val onInitialized: () -> Unit,
    cameraDirection: CameraDirection
) {
    private val manager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val cameraId: String = manager.cameraIdList[cameraDirection.id]
    private val previewSize: Size = manager.getCameraCharacteristics(cameraId)
        .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        ?.getOutputSizes(ImageFormat.JPEG)?.get(0) ?: Size(0, 0)

    private val handler = Handler(
        HandlerThread("camera2").apply {
            start()
        }.looper
    )

    private val imageReader = ImageReader.newInstance(
        640, 640, ImageFormat.JPEG, 2
    )

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private val cameraDeviceStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraDevice = camera
            cameraDevice?.createCaptureSession(
                listOf(
                    previewSurfaceView.holder.surface,
                    imageReader.surface,
                ), cameraCaptureSessionStateCallback, handler
            )
            previewSurfaceView.invalidate()
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice?.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            println("CameraDevice.StateCallback onError $error")
        }
    }

    private val previewBuilder by lazy {
        cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(imageReader.surface)
            addTarget(previewSurfaceView.holder.surface)
            onInitialized()
        }
    }

    private val cameraCaptureSessionStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            captureSession = session
            session.setRepeatingRequest(previewBuilder.build(), null, handler)
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            println("onConfigureFailed")
        }
    }
    private val previewSurfaceHolderCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
        }

        @SuppressLint("MissingPermission")
        override fun surfaceChanged(
            holder: SurfaceHolder, format: Int, width: Int, height: Int
        ) {
            onSurface(holder.surface)
            if (ActivityCompat.checkSelfPermission(
                    context, Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) throw IllegalArgumentException()
            manager.openCamera(cameraId, cameraDeviceStateCallback, handler)
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            cameraDevice?.close()
        }
    }

    fun startPreview() {
        visualizationSurfaceView.updateSizeKeepRatio(previewSize)
        previewSurfaceView.updateSizeKeepRatio(previewSize)
        imageReader.setOnImageAvailableListener(
            {
                val image = it.acquireLatestImage() ?: return@setOnImageAvailableListener
                processCameraImage(image)
                image.close()
            }, handler
        )

        previewSurfaceView.holder.addCallback(previewSurfaceHolderCallback)
    }

    private fun processCameraImage(image: Image) {
        val buffer = image.planes[0].buffer
        val array = ByteArray(buffer.remaining())
        buffer.get(array)
        onCameraFrame(array, image.width, image.height)
    }

    fun close() {
        handler.looper.quit()
        cameraDevice?.close()
        captureSession?.close()
        imageReader.close()
    }
}
