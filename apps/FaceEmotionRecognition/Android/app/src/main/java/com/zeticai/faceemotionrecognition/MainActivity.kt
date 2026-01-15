package com.zeticai.faceemotionrecognition

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Size
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.zeticai.faceemotionrecognition.feature.FaceDetection
import com.zeticai.faceemotionrecognition.feature.FaceEmotionRecognition
import com.zeticai.mlange.feature.entity.Box
import com.zeticai.mlange.feature.vision.OpenCVImageUtilsWrapper
import kotlin.math.max
import kotlin.math.min

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
    private val faceEmotionRecognition by lazy { FaceEmotionRecognition(this) }

    private val cameraController by lazy {
        CameraController(this,
            findViewById(R.id.surfaceView),
            findViewById(R.id.visualizationSurfaceView),
            { image, inputSize ->
                processImage(image, inputSize)
            },
            {
                openCVImageUtilsWrapper.setSurface(it)
            }, CameraDirection.FRONT)
    }

    private fun clamp(a: Float, b: Float): Float {
        return max(0f, min(a, b))
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
        val imagePtr = openCVImageUtilsWrapper.frame(image, CameraController.ROTATE_COUNTER_CLOCKWISE)

        val faceDetectionResult = faceDetection.run(imagePtr)

        val faceEmotionRecognitionResult =
            if (faceDetectionResult.faceDetectionResults.isEmpty()) null
            else {
                val res = faceDetectionResult.faceDetectionResults[0]
                val resizeFactor = 0f
                val roi = Box(
                    clamp(res.bbox.xMin * (1 - resizeFactor), 1f),
                    clamp(res.bbox.yMin * (1 - resizeFactor), 1f),
                    clamp(res.bbox.xMax * (1 + resizeFactor), 1f),
                    clamp(res.bbox.yMax * (1 + resizeFactor), 1f),
                )
                faceEmotionRecognition.run(imagePtr, roi)
            }

        visualizationSurfaceView.visualize(
            faceDetectionResult,
            faceEmotionRecognitionResult,
            inputSize,
            true
        )
    }
}
