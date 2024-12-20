package com.zeticai.mlange.feature.yolov8

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.zeticai.mlange.R
import com.zeticai.mlange.common.vision.CameraDirection
import com.zeticai.mlange.common.vision.CameraProcessor
import com.zeticai.mlange.feature.vision.OpenCVImageUtilsWrapper

class YOLOv8Activity : AppCompatActivity() {
    private val requestPermissionLauncher =
        registerForActivityResult(RequestPermission()) { isGranted: Boolean ->
            if (isGranted)
                cameraProcessor.startPreview()
            else
                Toast.makeText(this, "Camera Permission Not Granted!", Toast.LENGTH_SHORT).show()
        }

    private val visualizationSurfaceView: VisualizationSurfaceView by lazy { findViewById(R.id.visualizationSurfaceView) }
    private val openCVImageUtilsWrapper: OpenCVImageUtilsWrapper =
        OpenCVImageUtilsWrapper()
    private val yolov8 by lazy { YOLOv8(this, "yolo-v8n-test") }

    private val cameraProcessor by lazy {
        CameraProcessor(this,
            findViewById(R.id.surfaceView),
            findViewById(R.id.visualizationSurfaceView),
            { image, _, _ ->
                processImage(image)
            },
            {
                openCVImageUtilsWrapper.setSurface(it)
            },
            {
                isCameraInitialized = true
                turnOffSplashScreen()
            }, CameraDirection.BACK
        )
    }

    private var isYoloInitialized: Boolean = false
    private var isCameraInitialized: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_yolov8)

        findViewById<ImageView>(R.id.splashView).post {
            yolov8
            isYoloInitialized = true
            turnOffSplashScreen()
        }
    }

    override fun onResume() {
        super.onResume()

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            cameraProcessor.startPreview()
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
        cameraProcessor.close()
    }

    private fun processImage(image: ByteArray) {
        val imagePtr = openCVImageUtilsWrapper.frame(image, CameraProcessor.ROTATE_CLOCKWISE)

        val faceDetectionResult = yolov8.run(imagePtr)

        visualizationSurfaceView.visualize(
            faceDetectionResult
        )
    }

    private fun turnOffSplashScreen() {
        if (isYoloInitialized && isCameraInitialized) {
            runOnUiThread {
                findViewById<ImageView>(R.id.splashView).visibility = View.GONE
                findViewById<View>(R.id.splashBackgroundView).visibility = View.GONE
            }
        }
    }

    companion object {
        const val TAG = "Main Activity"
    }
}
