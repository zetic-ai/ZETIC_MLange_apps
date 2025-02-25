package com.zeticai.benchmark

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.zeticai.benchmark.databinding.ActivityMainBinding
import com.zeticai.mlange.core.benchmark.BenchmarkModel
import com.zeticai.mlange.core.benchmark.BenchmarkResult
import com.zeticai.mlange.core.benchmark.TargetModelBenchmarkResult
import com.zeticai.mlange.core.model.APType
import com.zeticai.mlange.core.model.ZeticMLangeModelInfo
import com.zeticai.mlange.core.model.ZeticMLangeTarget

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var benchmarkAdapter: BenchmarkResultAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupBenchmarkButton()
    }

    private fun setupRecyclerView() {
        benchmarkAdapter = BenchmarkResultAdapter()
        binding.resultsRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = benchmarkAdapter
            addItemDecoration(
                DividerItemDecoration(
                    this@MainActivity,
                    DividerItemDecoration.VERTICAL
                )
            )
        }
    }

    private fun setupBenchmarkButton() {
        binding.runBenchmarkButton.setOnClickListener {
            val modelKey = binding.modelKeyInput.editText?.text.toString()
            if (modelKey.isNotEmpty()) {
                runBenchmark(modelKey)
            } else {
                showError("Please enter a model key")
            }
        }
    }

    private fun runBenchmark(modelKey: String) {
        runCatching {
            val benchmarkModel = BenchmarkModel()
            binding.runBenchmarkButton.isEnabled = false

            Thread {
                benchmarkModel.benchmarkAll(this@MainActivity, modelKey) {
                    val aptype =
                        convertAPType(Build.MODEL, it.target, it.targetModelBenchmarkResult.apType)
                    val result = BenchmarkResult(it.path, it.target, TargetModelBenchmarkResult(it.targetModelBenchmarkResult.latency, emptyList(), aptype))
                    runOnUiThread {
                        benchmarkAdapter.addResult(result)
                    }
                }
            }.start()
        }.onFailure {
            showError("Error running benchmark: ${it.message}")
            binding.runBenchmarkButton.isEnabled = true
        }
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun convertAPType(apVendor: String, target: ZeticMLangeTarget, apType: APType): APType {
        return when (target) {
            ZeticMLangeTarget.ZETIC_MLANGE_TARGET_TORCH,
            ZeticMLangeTarget.ZETIC_MLANGE_TARGET_ORT -> APType.CPU

            ZeticMLangeTarget.ZETIC_MLANGE_TARGET_ORT_NNAPI -> {
                if (apVendor.lowercase().contains("mediatek")) APType.NPU
                else APType.CPU
            }

            ZeticMLangeTarget.ZETIC_MLANGE_TARGET_TFLITE_FP16,
            ZeticMLangeTarget.ZETIC_MLANGE_TARGET_TFLITE_FP32,
            ZeticMLangeTarget.ZETIC_MLANGE_TARGET_QNN -> apType

            ZeticMLangeTarget.ZETIC_MLANGE_TARGET_QNN_QUANT -> APType.NPU
            else -> APType.NA
        }
    }
}
