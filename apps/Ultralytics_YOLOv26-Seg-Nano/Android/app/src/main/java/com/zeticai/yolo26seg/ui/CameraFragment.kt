package com.zeticai.yolo26seg.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.YuvImage
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.zeticai.yolo26seg.OverlayView
import com.zeticai.yolo26seg.R
import com.zeticai.yolo26seg.Yolo26Seg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class CameraFragment : Fragment() {

    private lateinit var overlay: OverlayView
    private lateinit var tvFps: TextView
    private lateinit var viewFinder: PreviewView
    private lateinit var yolo: Yolo26Seg
    
    private val isComputing = AtomicBoolean(false)
    private var cameraExecutor: ExecutorService? = null
    private var lastFpsTime = System.currentTimeMillis()
    private var frameCount = 0

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewFinder = view.findViewById(R.id.viewFinder)
        overlay = view.findViewById(R.id.overlay)
        tvFps = view.findViewById(R.id.tvFps)
        
        yolo = Yolo26Seg(requireContext())
        cameraExecutor = Executors.newSingleThreadExecutor()

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            
            val preview = Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }
                
            val imageAnalyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor!!, { image ->
                         processImage(image)
                    })
                }
                
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, 
                    CameraSelector.DEFAULT_BACK_CAMERA, 
                    preview, 
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e("CameraFragment", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }
    
    private fun processImage(image: ImageProxy) {
        if (isComputing.get()) {
            image.close()
            return
        }
        isComputing.set(true)
        
        // Convert YUV to Bitmap (slow but robust)
        val bitmap = imageProxyToBitmap(image)
        image.close()

        if (bitmap == null) {
            isComputing.set(false)
            return
        }
        
        // Update FPS
        val now = System.currentTimeMillis()
        frameCount++
        if (now - lastFpsTime >= 1000) {
            val fps = frameCount * 1000.0 / (now - lastFpsTime)
            lifecycleScope.launch(Dispatchers.Main) {
                tvFps.text = "FPS: %.1f".format(fps)
            }
            lastFpsTime = now
            frameCount = 0
        }
        
        lifecycleScope.launch(Dispatchers.Default) {
             try {
                 // Rotate bitmap if needed for portrait mode?
                 // ImageAnalysis usually gives rotated buffer if target rotation set,
                 // but we didn't set target rotation.
                 // CameraX output is typically landscape 640x480.
                 // We need to rotate 90 deg for portrait phone usage. 
                 
                 // For now, let's assume landscape or just process as is. 
                 // Real rotation depends on device orientation.
                 // Let's implement a simple rotation if width > height (landscape) but phone is portrait.
                 
                 val detections = yolo.inference(bitmap)
                 
                 withContext(Dispatchers.Main) {
                     overlay.setResults(detections, bitmap.width, bitmap.height)
                 }
             } catch (e: Exception) {
                 Log.e("CameraFragment", "Inference error", e)
             } finally {
                 isComputing.set(false)
             }
        }
    }
    
    // Helper to convert ImageProxy to Bitmap
    // Note: This is computationally expensive, ideally use YUV directly or RenderScript
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap? {
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 90, out) // faster quality
        val imageBytes = out.toByteArray()
        
        val opts = BitmapFactory.Options()
        opts.inMutable = true
        var bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, opts)
        
        // Correct Rotation
        val rotation = image.imageInfo.rotationDegrees
        if (rotation != 0) {
            val matrix = android.graphics.Matrix()
            matrix.postRotate(rotation.toFloat())
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        }
        
        return bmp
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor?.shutdown()
        yolo.close()
    }
}
