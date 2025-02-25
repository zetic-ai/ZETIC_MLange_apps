package com.zeticai.benchmark

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.zeticai.mlange.core.benchmark.BenchmarkResult

class BenchmarkResultAdapter(
    private val results: MutableList<BenchmarkResult> = mutableListOf()
) : RecyclerView.Adapter<BenchmarkResultAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val backendName: TextView = view.findViewById(R.id.backendName)
        val latencyValue: TextView = view.findViewById(R.id.latencyValue)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_benchmark_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = results[position]
        holder.backendName.text = "AP Type : ${result.targetModelBenchmarkResult.apType.name}"
        holder.latencyValue.text = String.format("%.8f sec", result.targetModelBenchmarkResult.latency)
    }

    override fun getItemCount() = results.size

    fun addResult(result: BenchmarkResult) {
        val resultNotExists = results.none { it.targetModelBenchmarkResult.apType == result.targetModelBenchmarkResult.apType }
        val resultSlowerThanNew = results.removeIf {
            it.targetModelBenchmarkResult.apType == result.targetModelBenchmarkResult.apType &&
                    it.targetModelBenchmarkResult.latency > result.targetModelBenchmarkResult.latency
        }
        if (resultNotExists || resultSlowerThanNew) {
            results.add(result)
            notifyDataSetChanged()
        }
    }
}