package com.zeticai.zeticmlangeyamnet_android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.zetic.ZeticMLange.ZeticMLangeModel
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
        findViewById(
            R.id.audio_class_visualizer
        )
    }
    private val yamnetModel: ZeticMLangeModel by lazy { ZeticMLangeModel(this, "YAMNet") }
    private val audioRecord: AudioSampler by lazy {
        AudioSampler {
            yamnetModel.run(arrayOf(it))
            val output = yamnetModel.outputBuffers[1]
            val outputs = postprocess(output)
            audioClassVisualizationSurfaceView.visualize(
                outputs.first.map {
                    AudioClass(audioClasses[it], outputs.second[it])
                }
            )
        }
    }

    private fun postprocess(
        outputBuffer: ByteBuffer
    ): Pair<IntArray, FloatArray> {
        val row = 6
        val column = 521
        val topN = 5

        outputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.rewind()
        val floatBuffer = outputBuffer.asFloatBuffer()
        val floatArray = FloatArray(outputBuffer.remaining() / Float.SIZE_BYTES)
        floatBuffer.get(floatArray)

        val scores = Array(row) { FloatArray(column) }
        for (i in 0 until row) {
            System.arraycopy(floatArray, i * column, scores[i], 0, column)
        }

        val meanScores = FloatArray(column) { index ->
            scores.map { it[index] }.average().toFloat()
        }

        val topClassIndices = meanScores.withIndex()
            .sortedByDescending { it.value }
            .take(topN)
            .map { it.index }
            .toIntArray()

        return Pair(topClassIndices, meanScores)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if (checkPermission()) {
            audioRecord.startRecording()
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
            audioRecord.startRecording()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        audioRecord.stopRecording()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 200
    }
}
