package com.zeticai.zeticmlangeyamnet_android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : AppCompatActivity() {
    private val audioClasses: List<String> by lazy {
        assets.open("yamnet_class_map.csv").use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                return@lazy reader.readLines().drop(1).map {
                    it.split(",")[2]
                }
            }
        }
    }

    private val audioClassVisualizationSurfaceView: AudioClassVisualizationSurfaceView by lazy {
        findViewById(R.id.audio_class_visualizer)
    }

    private val yamnet: YAMNet by lazy { YAMNet( this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (checkPermission()) {
            Thread {
                yamnet.startRecording(::visualize)
            }.start()
        } else {
            requestPermission()
        }
    }

    private fun checkPermission(): Boolean {
        val result = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        return result == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            yamnet.startRecording(::visualize)
        }
    }

    private fun visualize(outputs: Pair<IntArray, FloatArray>) {
        runOnUiThread {
            audioClassVisualizationSurfaceView.visualize(
                outputs.first.map {
                    AudioClass(audioClasses[it], outputs.second[it])
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        yamnet.deinit()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 200
    }
}
