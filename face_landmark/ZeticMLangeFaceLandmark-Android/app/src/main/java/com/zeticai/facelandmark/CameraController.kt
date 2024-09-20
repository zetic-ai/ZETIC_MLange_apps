package com.zeticai.facelandmark

import android.Manifest
import android.app.Activity
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

class CameraController @JvmOverloads constructor(
    context: Context,
    private val previewSurfaceView: PreviewSurfaceView,
    private val visualizationSurfaceView: VisualizationSurfaceView,
    private val onCameraFrame: (ByteArray, Int, Int) -> Unit,
    private val onSurface: (Surface) -> Unit,
    manager: CameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager,
    private val cameraId: String = manager.cameraIdList[1],
    characteristics: CameraCharacteristics = manager.getCameraCharacteristics(cameraId),
    previewSize: Size = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        ?.getOutputSizes(ImageFormat.JPEG)?.get(0) ?: Size(0, 0),
    val rotation: Int = (context as? Activity)?.windowManager?.defaultDisplay?.rotation ?: 0
) {
    private val handler = Handler(
        HandlerThread("camera2").apply {
            start()
        }.looper
    )

    private val imageReader = ImageReader.newInstance(
        128,
        128,
        ImageFormat.JPEG,
        2
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
                ),
                cameraCaptureSessionStateCallback,
                handler
            )
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraDevice?.close()
        }

        override fun onError(camera: CameraDevice, error: Int) {

        }
    }

    private val previewBuilder by lazy {
        cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(imageReader.surface)
            addTarget(previewSurfaceView.holder.surface)
        }
    }

    private val cameraCaptureSessionStateCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            captureSession = session
            session.setRepeatingRequest(previewBuilder.build(), null, handler)
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {

        }
    }
    private val previewSurfaceHolderCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            onSurface(holder.surface)
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            )
                throw IllegalArgumentException()
            visualizationSurfaceView.updateSizeKeepRatio(previewSize)
            previewSurfaceView.updateSizeKeepRatio(previewSize)
            manager.openCamera(cameraId, cameraDeviceStateCallback, handler)
        }

        override fun surfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            width: Int,
            height: Int
        ) {
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            cameraDevice?.close()
        }
    }

    fun startPreview() {
        imageReader.setOnImageAvailableListener(
            {
                val image = it.acquireLatestImage() ?: return@setOnImageAvailableListener
                processCameraImage(image)
                image.close()
            },
            handler
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
        cameraDevice?.close()
        captureSession?.close()
        imageReader.close()
    }
}
