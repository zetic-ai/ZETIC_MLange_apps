package com.zeticai.mlange.feature.faceemotionrecognition

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
import com.zeticai.mlange.feature.facedetection.FaceDetection
import com.zeticai.mlange.feature.entity.Box
import com.zeticai.mlange.feature.vision.OpenCVImageUtilsWrapper
import kotlin.math.max
import kotlin.math.min

class FaceEmotionRecognitionActivity : AppCompatActivity() {
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
    private val faceEmotionRecognition by lazy {
        FaceEmotionRecognition(
            this,
            "face_emotion_recognition"
        )
    }

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

    private fun clamp(a: Float, b: Float): Float {
        return max(0f, min(a, b))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_emotion_recognition)
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
        val imagePtr = openCVImageUtilsWrapper.frame(image, CameraProcessor.ROTATE_COUNTER_CLOCKWISE)

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
            faceEmotionRecognitionResult
        )
    }
}
