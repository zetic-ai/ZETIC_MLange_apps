package com.zeticai.benchmark

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.zeticai.mlange.core.benchmark.BenchmarkResult

class BenchmarkResultAdapter(
    private val results: List<BenchmarkResult>
) : RecyclerView.Adapter<BenchmarkResultAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val backendName: TextView = view.findViewById(R.id.backendName)
        val latencyValue: TextView = view.findViewById(R.id.latencyValue)
        val apTypeText: TextView = view.findViewById(R.id.apTypeText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_benchmark_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val result = results[position]
        holder.backendName.text = result.target.name
        holder.latencyValue.text = String.format("%.8f sec", result.targetModelBenchmarkResult.latency)
        holder.apTypeText.text = "AP Type : ${result.targetModelBenchmarkResult.apType.name}"
    }

    override fun getItemCount() = results.size
}