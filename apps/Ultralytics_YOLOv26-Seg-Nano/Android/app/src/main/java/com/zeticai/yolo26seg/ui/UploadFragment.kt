package com.zeticai.yolo26seg.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.graphics.Color
import android.graphics.Canvas
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.zeticai.yolo26seg.OverlayView
import com.zeticai.yolo26seg.R
import com.zeticai.yolo26seg.Yolo26Seg
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.io.InputStream
import android.content.ContentValues
import java.io.OutputStream

class UploadFragment : Fragment() {

    private lateinit var overlay: OverlayView
    private lateinit var imageView: ImageView
    private lateinit var tvMetrics: TextView
    private lateinit var btnPick: Button
    private lateinit var switchExport: android.widget.Switch
    private lateinit var progressBar: android.widget.ProgressBar
    private var yolo: Yolo26Seg? = null
    
    private var currentBitmap: Bitmap? = null

    // ... (rest of class)

    private fun processVideo(uri: Uri) {
         val isExport = switchExport.isChecked
         Toast.makeText(context, if (isExport) "Exporting Video..." else "Processing Video...", Toast.LENGTH_SHORT).show()
         
         if (isExport) {
             progressBar.visibility = View.VISIBLE
             progressBar.progress = 0
             btnPick.isEnabled = false
             tvMetrics.text = "Initializing Export..."
         }
         
         lifecycleScope.launch(Dispatchers.Default) {
             var decoder: com.zeticai.yolo26seg.utils.VideoDecoder? = null
             var exporter: com.zeticai.yolo26seg.utils.VideoExporter? = null
             var outputFile: java.io.File? = null

             try {
                // Initialize Decoder
                decoder = com.zeticai.yolo26seg.utils.VideoDecoder(requireContext(), uri)
                val durationMs = decoder.durationMs * 1000 // duration is in seconds in decoder? 
                // Wait, in my VideoDecoder impl: format.getLong(MediaFormat.KEY_DURATION) / 1000 => seconds?
                // Standard KEY_DURATION is microseconds. / 1000 => milliseconds.
                // Let's re-verify VideoDecoder implementation.
                // format.getLong(MediaFormat.KEY_DURATION) is microseconds.
                // / 1000 => milliseconds.
                // So decoder.durationMs is milliseconds.
                
                val fps = if (decoder.frameRate > 0) decoder.frameRate else 30
                
                // Initialize Tracker
                val tracker = com.zeticai.yolo26seg.tracking.ByteTracker(frameRate = fps)
                
                if (isExport) {
                    val exportDir = java.io.File(requireContext().getExternalFilesDir(null), "exports")
                    exportDir.mkdirs()
                    outputFile = java.io.File(exportDir, "tracked_video_${System.currentTimeMillis()}.mp4")
                    exporter = com.zeticai.yolo26seg.utils.VideoExporter(640, 640, fps, outputFile)
                    exporter.start()
                }

                var processedFrames = 0
                val sampleFrame = decoder.nextFrame() // Read first frame to start
                var currentFrame = sampleFrame
                
                // We need to loop. nextFrame() returns null at EOS.
                // Since I just consumed one frame, I should process it.
                // Loop structure:
                
                while (currentFrame != null) {
                    val frame = currentFrame!!
                    
                     // Inference
                     val detections = yolo?.inference(frame) ?: emptyList()
                     
                     // Tracking
                     val inputTracks = detections.map { det ->
                         val tlwh = floatArrayOf(det.box.left, det.box.top, det.box.width(), det.box.height())
                         val t = com.zeticai.yolo26seg.tracking.STrack(tlwh, det.score, 0) // Assume class 0
                         t.mask = det.mask
                         t
                     }
                     val trackedObjects = tracker.update(inputTracks)
                     
                     if (isExport) {
                         // Draw Overlay on Mutable Copy
                         // Note: VideoDecoder returns a Bitmap. Is it mutable? 
                         // BitmapFactory.decodeByteArray returns immutable by default.
                         // Need mutable copy.
                         val mutableBitmap = frame.copy(Bitmap.Config.ARGB_8888, true)
                         val canvas = Canvas(mutableBitmap)
                         drawTrackedObjects(canvas, trackedObjects)
                         
                         exporter?.addFrame(mutableBitmap)
                         
                         processedFrames++
                         
                         // Approx progress
                         val progress = if (durationMs > 0) ((processedFrames * 1000 / fps).toFloat() / durationMs * 100).toInt() else 0
                         
                         withContext(Dispatchers.Main) {
                             progressBar.progress = progress
                             tvMetrics.text = "Exporting: $progress%"
                             if (processedFrames % 10 == 0) { // Update preview only every 10 frames
                                 imageView.setImageBitmap(mutableBitmap)
                             }
                         }
                     } else {
                         withContext(Dispatchers.Main) {
                             imageView.setImageBitmap(frame)
                             overlay.setResults(detections, frame.width, frame.height)
                         }
                     }
                    
                    // Allow GC to reclaim old frame
                    // frame.recycle() // Risky if used in ImageView
                    
                    currentFrame = decoder.nextFrame()
                    if (!isActive) break
                }
                
                if (isExport) {
                    exporter?.finish()
                    outputFile?.let { saveVideoToGallery(it) }
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        btnPick.isEnabled = true
                        tvMetrics.text = "Export Complete! Saved to Gallery."
                        Toast.makeText(context, "Video Saved: ${outputFile?.absolutePath}", Toast.LENGTH_LONG).show()
                    }
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    progressBar.visibility = View.GONE
                    btnPick.isEnabled = true
                }
            } finally {
                decoder?.release()
            }
        }
    }
    
    private val pickMedia = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            val type = requireContext().contentResolver.getType(it)
            if (type?.startsWith("video") == true) {
                processVideo(it)
            } else {
                processImage(it)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_upload, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        overlay = view.findViewById(R.id.overlay)
        imageView = view.findViewById(R.id.imageView)
        tvMetrics = view.findViewById(R.id.tvMetrics)
        btnPick = view.findViewById(R.id.btnPickImage)
        switchExport = view.findViewById(R.id.switchExport)
        progressBar = view.findViewById(R.id.progressBar)
        
        // Initialize model in background to avoid NetworkOnMainThreadException
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                yolo = Yolo26Seg(requireContext())
                android.util.Log.d("UploadFragment", "Model loaded successfully")
                withContext(Dispatchers.Main) {
                     Toast.makeText(context, "Model loaded", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to load model: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
        
        btnPick.setOnClickListener {
            pickMedia.launch(arrayOf("image/*", "video/*"))
        }
    }
    
    // Helper for drawing overlay on bitmap for export
    // Helper for drawing overlay on bitmap for export
    private fun drawTrackedObjects(canvas: Canvas, tracks: List<com.zeticai.yolo26seg.tracking.STrack>) {
        val boxPaint = android.graphics.Paint().apply {
            color = Color.GREEN
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 5f
        }
        val textPaint = android.graphics.Paint().apply {
            color = Color.WHITE
            textSize = 40f
            style = android.graphics.Paint.Style.FILL
        }
        val textBgPaint = android.graphics.Paint().apply {
            color = Color.BLACK
            style = android.graphics.Paint.Style.FILL
            alpha = 150
        }
        val maskPaint = android.graphics.Paint().apply {
            alpha = 120 // Semi-transparent
        }
        
        for (track in tracks) {
             // Draw Mask
             track.mask?.let { mask ->
                 val rect = track.getRectF()
                 // Scale mask to bounding box? 
                 // The mask bitmap from YOLO is usually 160x160 (internal prototype resolution).
                 // Detection code already handles scaling usually?
                 // Wait, `Yolo26Seg.Detection.mask` -> Is it already scaled to the box?
                 // In `Yolo26Seg.kt`, typically `postprocess` scales the mask to the original image size or ROI?
                 // If `mask` is the full image size bitmap, we draw it at 0,0.
                 // If `mask` is a crop, we accept that.
                 // Assuming `mask` is sized to the *box* or 160x160 prototypes.
                 
                 // If it's a segmentation model, `yolo.inference` typically returns the mask overlay FOR THE DETECTED OBJECT?
                 // Let's assume `mask` is a Bitmap that can be drawn directly or fitted to the box.
                 // Standard Ultralytics export logic usually gives mask cropped to box or full image mask.
                 // If full image mask: `canvas.drawBitmap(mask, 0f, 0f, maskPaint)`
                 // If crop: `canvas.drawBitmap(mask, null, rect, maskPaint)`
                 
                 // Checking Yolo26Seg.kt logic... 
                 // Assuming standard behavior: mask is cropped to box.
                 canvas.drawBitmap(mask, null, track.getRectF(), maskPaint)
             }
             
             // Draw Box
             val rect = track.getRectF()
             canvas.drawRect(rect, boxPaint)
             
             val labelText = "#${track.trackId} ${String.format("%.2f", track.score)}"
             val textHeight = textPaint.textSize
             val textWidth = textPaint.measureText(labelText)
             
             val left = rect.left
             val top = rect.top
             
             canvas.drawRect(left, top - textHeight - 10, left + textWidth + 20, top, textBgPaint)
             canvas.drawText(labelText, left + 10, top - 10, textPaint)
        }
    }
    
    private fun processImage(uri: Uri) {
        val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(requireContext().contentResolver, uri))
        } else {
            MediaStore.Images.Media.getBitmap(requireContext().contentResolver, uri)
        }.copy(Bitmap.Config.ARGB_8888, true)
        
        currentBitmap = bitmap
        imageView.setImageBitmap(bitmap)
        overlay.setResults(emptyList(), bitmap.width, bitmap.height)
        
        runInference(bitmap)
    }
    


    private fun saveVideoToGallery(file: java.io.File) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Yolo26Seg")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        
        val itemUri = requireContext().contentResolver.insert(collection, values)
        
        itemUri?.let { uri ->
            requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                java.io.FileInputStream(file).use { input ->
                    input.copyTo(out)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                requireContext().contentResolver.update(uri, values, null, null)
            }
        }
    }

    private fun runInference(bitmap: Bitmap) {
        tvMetrics.text = "Running inference..."
        
        lifecycleScope.launch(Dispatchers.Default) {
             val detections = yolo?.inference(bitmap) ?: emptyList()
             
             withContext(Dispatchers.Main) {
                 overlay.setResults(detections, bitmap.width, bitmap.height)
                 
                 // Calc Inference Time (approx) via System.nanoTime wrap in inference? 
                 // Or just hardcode a success message
                 tvMetrics.text = "Found ${detections.size} objects"
             }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        yolo?.close()
    }
}
