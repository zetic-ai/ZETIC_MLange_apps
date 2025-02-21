package com.zeticai.benchmark

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.zeticai.benchmark.databinding.ActivityMainBinding
import com.zeticai.mlange.core.benchmark.BenchmarkModel
import com.zeticai.mlange.core.benchmark.BenchmarkResult

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var benchmarkAdapter: BenchmarkResultAdapter
    private val benchmarkResults = mutableListOf<BenchmarkResult>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupBenchmarkButton()
    }

    private fun setupRecyclerView() {
        benchmarkAdapter = BenchmarkResultAdapter(benchmarkResults)
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
            benchmarkResults.clear()

            Thread {
                benchmarkModel.benchmarkAll(this@MainActivity, modelKey) {
                    benchmarkResults.add(it)
                    runOnUiThread {
                        benchmarkAdapter.notifyDataSetChanged()
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
}
