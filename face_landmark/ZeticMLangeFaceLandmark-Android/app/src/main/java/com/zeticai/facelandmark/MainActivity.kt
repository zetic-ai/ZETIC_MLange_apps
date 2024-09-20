package com.zeticai.facelandmark

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.zetic.ZeticMLangeFeature.ZeticMLangeFeatureCameraController
import com.zetic.ZeticMLangeFeature.type.Box
import com.zeticai.facelandmark.feature.FaceDetection
import com.zeticai.facelandmark.feature.FaceLandmark
import kotlin.math.min
import kotlin.math.max

class MainActivity : AppCompatActivity() {
    private val requestPermissionLauncher =
        registerForActivityResult(RequestPermission()) { isGranted: Boolean ->
            if (isGranted)
                cameraController.startPreview()
            else
                Toast.makeText(this, "Camera Permission Not Granted!", Toast.LENGTH_SHORT).show()
        }

    private val visualizationSurfaceView: VisualizationSurfaceView by lazy { findViewById(R.id.visualizationSurfaceView) }
    private val zeticMLangeFeatureCameraController: ZeticMLangeFeatureCameraController = ZeticMLangeFeatureCameraController()
    private val faceDetection by lazy { FaceDetection(this, "face_detection_short_range") }
    private val faceLandmark by lazy { FaceLandmark(this, "face_landmark") }

    private val cameraController by lazy {
        CameraController(this,
            findViewById(R.id.surfaceView),
            findViewById(R.id.visualizationSurfaceView),
            { image, width, height ->
                processImage(image, width, height)
            },
            {
                zeticMLangeFeatureCameraController.setSurface(it)
            })
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

        if (ContextCompat.checkSelfPermission( this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraController.startPreview()
        } else if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
            Toast.makeText(this, "Camera Permission Not Granted!", Toast.LENGTH_SHORT).show()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraController.close()
        faceDetection.close()
        faceLandmark.close()
    }

    private fun processImage(image: ByteArray, width: Int, height: Int) {
        val imagePtr = zeticMLangeFeatureCameraController.frame(image)

        val faceDetectionResult = faceDetection.run(imagePtr)

        val faceLandmarkResult = if (faceDetectionResult.faceDetections.isEmpty()) null
        else {
            val res = faceDetectionResult.faceDetections[0]
            val resizeFactor = 0.2f
            val roi = Box(
                clamp(res.bbox.xMin * (1 - resizeFactor), 1f),
                clamp(res.bbox.yMin * (1 - resizeFactor), 1f),
                clamp(res.bbox.xMax * (1 + resizeFactor), 1f),
                clamp(res.bbox.yMax * (1 + resizeFactor), 1f),
            )
            faceLandmark.run(imagePtr, roi)
        }

        visualizationSurfaceView.visualize(
            faceDetectionResult,
            faceLandmarkResult)
    }

    companion object {
        const val TAG = "Main Activity"
    }
}
