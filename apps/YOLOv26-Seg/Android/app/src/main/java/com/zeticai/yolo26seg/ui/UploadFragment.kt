package com.zeticai.yolo26seg.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Canvas
import android.net.Uri
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
import java.io.InputStream
import android.content.ContentValues
import android.provider.MediaStore
import java.io.OutputStream

class UploadFragment : Fragment() {

    private lateinit var overlay: OverlayView
    private lateinit var imageView: ImageView
    private lateinit var tvMetrics: TextView
    private lateinit var btnPick: Button
    private lateinit var yolo: Yolo26Seg
    
    private var currentBitmap: Bitmap? = null
    
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            processUri(it)
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
        
        yolo = Yolo26Seg(requireContext())
        
        btnPick.setOnClickListener {
            pickImage.launch("image/*")
        }
    }
    
    private fun processUri(uri: Uri) {
        val stream: InputStream? = requireContext().contentResolver.openInputStream(uri)
        currentBitmap = BitmapFactory.decodeStream(stream)
        
        if (currentBitmap != null) {
            imageView.setImageBitmap(currentBitmap)
            runInference(currentBitmap!!)
        }
    }
    
    private fun runInference(bitmap: Bitmap) {
        tvMetrics.text = "Running inference..."
        
        lifecycleScope.launch(Dispatchers.Default) {
             val detections = yolo.inference(bitmap)
             
             withContext(Dispatchers.Main) {
                 overlay.setResults(detections, bitmap.width, bitmap.height)
                 
                 // Show metrics
                 val counts = detections.groupingBy { it.label }.eachCount()
                 val top = detections.maxByOrNull { it.score }
                 
                 val sb = StringBuilder()
                 sb.append("Counts: ")
                 counts.forEach { (k, v) -> sb.append("$k: $v, ") }
                 if (top != null) {
                      sb.append("\nTop: ${top.label} (${"%.2f".format(top.score)})")
                 }
                 tvMetrics.text = sb.toString()
             }
        }
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        yolo.close()
    }
}
