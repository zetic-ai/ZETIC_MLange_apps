package com.zeticai.zeticmlangeyolov8androidjava

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.zeticai.mlange.feature.vision.OpenCVImageUtilsWrapper
import com.zeticai.zeticmlangeyolov8androidjava.feature.YoloV8

class MainActivity : AppCompatActivity() {
    private val requestPermissionLauncher =
        registerForActivityResult(RequestPermission()) { isGranted: Boolean ->
            if (isGranted) cameraController.startPreview()
            else Toast.makeText(this, "Camera Permission Not Granted!", Toast.LENGTH_SHORT).show()
        }

    private val visualizationSurfaceView: VisualizationSurfaceView by lazy { findViewById(R.id.visualizationSurfaceView) }
    private val openCVImageUtilsWrapper: OpenCVImageUtilsWrapper =
        OpenCVImageUtilsWrapper()
    private val yolov8 by lazy { YoloV8(this, "yolo-v11n-test") }

    private val cameraController by lazy {
        CameraController(this,
            findViewById(R.id.surfaceView),
            findViewById(R.id.visualizationSurfaceView),
            { image, width, height ->
                processImage(image)
            },
            {
                openCVImageUtilsWrapper.setSurface(it)
            })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }

    override fun onResume() {
        super.onResume()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            cameraController.startPreview()
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.CAMERA
            )
        ) {
            Toast.makeText(this, "Camera Permission Not Granted!", Toast.LENGTH_SHORT).show()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraController.close()
        yolov8.close()
    }

    private fun processImage(image: ByteArray) {
        val imagePtr = openCVImageUtilsWrapper.frame(image, 90)

        val faceDetectionResult = yolov8.run(imagePtr)

        visualizationSurfaceView.visualize(
            faceDetectionResult
        )
    }

    companion object {
        const val TAG = "Main Activity"
    }
}
