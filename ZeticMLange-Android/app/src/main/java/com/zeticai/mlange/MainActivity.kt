package com.zeticai.mlange

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.zeticai.mlange.common.NetworkManager
import com.zeticai.mlange.core.model.ZeticMLangeModel
import com.zeticai.mlange.feature.facedetection.FaceDetectionActivity
import com.zeticai.mlange.feature.faceemotionrecognition.FaceEmotionRecognitionActivity
import com.zeticai.mlange.feature.facelandmark.FaceLandmarkActivity
import com.zeticai.mlange.feature.whisper.WhisperActivity
import com.zeticai.mlange.feature.yamnet.YAMNetActivity
import com.zeticai.mlange.feature.yolov8.YOLOv8Activity
import com.zeticai.mlange.view.FeatureItem
import com.zeticai.mlange.view.FeaturesAdapter
import com.zeticai.mlange.view.ModelStatus
import java.util.concurrent.CompletableFuture

class MainActivity : AppCompatActivity() {
    private val sharedPref: SharedPreferences by lazy { getPreferences(Context.MODE_PRIVATE) }

    private val featureItems: MutableList<FeatureItem> = mutableListOf(
        FeatureItem(
            "Object Detection",
            YOLOv8Activity::class.java,
            listOf("yolo-v8n-test")
        ),
        FeatureItem(
            "Face Detection",
            FaceDetectionActivity::class.java,
            listOf("face_detection_short_range")
        ),
        FeatureItem(
            "Face Emotion Recognition",
            FaceEmotionRecognitionActivity::class.java,
            listOf("face_detection_short_range", "face_landmark", "face_emotion_recognition")
        ),
        FeatureItem(
            "Face Landmark Detection",
            FaceLandmarkActivity::class.java,
            listOf("face_detection_short_range", "face_landmark")
        ),
        FeatureItem(
            "Automatic Speech Recognition",
            WhisperActivity::class.java,
            listOf("whisper-tiny-encoder", "whisper-tiny-decoder")
        ),
        FeatureItem(
            "Sound Classification",
            YAMNetActivity::class.java,
            listOf("YAMNet")
        ),
    )

    private val featuresAdapter: FeaturesAdapter by lazy {
        FeaturesAdapter(featureItems) {
            onClickFeatureItem(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initModelStatus()
        initAdapter()
    }

    private fun initAdapter() {
        findViewById<RecyclerView>(R.id.main_features_recyclerview).adapter = featuresAdapter
    }

    private fun initModelStatus() {
        featureItems.forEach {
            it.modelStatus = determineModelStatus(it)
        }
    }

    private fun determineModelStatus(featureItem: FeatureItem): ModelStatus {
        if (sharedPref.getInt(featureItem.name, 0) == 1)
            return ModelStatus.READY
        return ModelStatus.NOT_READY
    }

    private fun onClickFeatureItem(featureItem: FeatureItem) {
        if (featureItem.modelStatus == ModelStatus.READY)
            startActivity(Intent(this, featureItem.activity))
        else if (featureItem.modelStatus == ModelStatus.NOT_READY) {
            if (!NetworkManager.checkNetworkState(this)) {
                Toast.makeText(this, "Network connection is required!", Toast.LENGTH_SHORT).show()
                return
            }
            loadModel(featureItem)
        }
    }

    private fun loadModel(featureItem: FeatureItem) {
        CompletableFuture.runAsync {
            runOnUiThread {
                updateModelStatus(featureItem, ModelStatus.FETCHING)
            }
            featureItem.modelKeys.forEach {
                ZeticMLangeModel(this, it)
            }
            runOnUiThread {
                updateModelStatus(featureItem, ModelStatus.READY)

                with(sharedPref.edit()) {
                    putInt(featureItem.name, 1)
                    apply()
                }
            }
        }.exceptionally {
            runOnUiThread {
                updateModelStatus(featureItem, ModelStatus.NOT_READY)
                Toast.makeText(this, it.message ?: "Network error occurred!", Toast.LENGTH_SHORT).show()
            }
            Log.e("ZeticMLange", it.message ?: "")
            Log.e("ZeticMLange", it.stackTraceToString())
            null
        }
    }

    private fun updateModelStatus(featureItem: FeatureItem, modelStatus: ModelStatus) {
        featureItem.modelStatus = modelStatus
        featuresAdapter.notifyDataSetChanged()
    }
}