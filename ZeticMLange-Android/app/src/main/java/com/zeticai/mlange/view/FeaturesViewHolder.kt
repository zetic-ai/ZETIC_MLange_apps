package com.zeticai.mlange.view

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.zeticai.mlange.R

class FeaturesViewHolder(
    root: View,
    private val onClick: (FeatureItem) -> Unit
) : RecyclerView.ViewHolder(root) {
    private val name: TextView = root.findViewById(R.id.features_name)
    private val modelStatus: TextView = root.findViewById(R.id.features_model_path)

    fun bind(featureItem: FeatureItem) {
        name.text = featureItem.name
        modelStatus.text =  when(featureItem.modelStatus) {
            ModelStatus.NOT_READY -> "Model is not ready to use"
            ModelStatus.FETCHING -> "Fetching model files from the server..."
            ModelStatus.READY -> "Model is ready to use"
        }
        itemView.setOnClickListener {
            onClick(featureItem)
        }
    }
}
