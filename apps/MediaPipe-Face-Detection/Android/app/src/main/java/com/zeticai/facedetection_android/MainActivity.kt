package com.zeticai.facedetection_android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.zeticai.facedetection_android.feature.FaceDetection
import com.zeticai.mlange.feature.vision.OpenCVImageUtilsWrapper

class MainActivity : AppCompatActivity() {
    private val requestPermissionLauncher =
        registerForActivityResult(RequestPermission()) { isGranted: Boolean ->
            if (isGranted)
                cameraController.startPreview()
            else
                Toast.makeText(this, "Camera Permission Not Granted!", Toast.LENGTH_SHORT).show()
        }

    private val visualizationSurfaceView: VisualizationSurfaceView by lazy { findViewById(R.id.visualizationSurfaceView) }
    private val openCVImageUtilsWrapper: OpenCVImageUtilsWrapper = OpenCVImageUtilsWrapper()
    private val faceDetection by lazy { FaceDetection(this) }

    private val cameraController by lazy {
        CameraController(this,
            findViewById(R.id.surfaceView),
            findViewById(R.id.visualizationSurfaceView),
            { image, inputSize ->
                processImage(image, inputSize)
            },
            {
                openCVImageUtilsWrapper.setSurface(it)
            }, CameraDirection.FRONT
        )
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
    }

    private fun processImage(image: ByteArray, inputSize: Size) {
        val imagePtr =
            openCVImageUtilsWrapper.frame(image, CameraController.ROTATE_COUNTER_CLOCKWISE)

        val faceDetectionResult = faceDetection.run(imagePtr)

        visualizationSurfaceView.visualize(
            faceDetectionResult, inputSize, true
        )
    }
}
