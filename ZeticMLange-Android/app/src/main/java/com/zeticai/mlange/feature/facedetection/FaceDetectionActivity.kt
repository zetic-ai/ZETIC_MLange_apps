package com.zeticai.mlange.feature.facedetection

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.zeticai.mlange.R
import com.zeticai.mlange.common.vision.CameraDirection
import com.zeticai.mlange.common.vision.CameraProcessor
import com.zeticai.mlange.feature.vision.OpenCVImageUtilsWrapper

class FaceDetectionActivity : AppCompatActivity() {
    private val requestPermissionLauncher =
        registerForActivityResult(RequestPermission()) { isGranted: Boolean ->
            if (isGranted)
                cameraProcessor.startPreview()
            else
                Toast.makeText(this, "Camera Permission Not Granted!", Toast.LENGTH_SHORT).show()
        }

    private val visualizationSurfaceView: VisualizationSurfaceView by lazy { findViewById(R.id.visualizationSurfaceView) }
    private val openCVImageUtilsWrapper: OpenCVImageUtilsWrapper = OpenCVImageUtilsWrapper()
    private val faceDetection by lazy { FaceDetection(this, "face_detection_short_range") }

    private val cameraProcessor by lazy {
        CameraProcessor(this,
            findViewById(R.id.surfaceView),
            findViewById(R.id.visualizationSurfaceView),
            { image, _, _ ->
                processImage(image)
            },
            {
                openCVImageUtilsWrapper.setSurface(it)
            }, {}, CameraDirection.FRONT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_detection)
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
        val imagePtr = openCVImageUtilsWrapper.frame(image)

        val faceDetectionResult = faceDetection.run(imagePtr)

        visualizationSurfaceView.visualize(
            faceDetectionResult
        )
    }
}
