package com.zeticai.mlange.view

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.zeticai.mlange.R

class FeaturesAdapter(
    private val features: List<FeatureItem>,
    private val onClick: (FeatureItem) -> Unit,
) : RecyclerView.Adapter<FeaturesViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeaturesViewHolder {
        val view =
            LayoutInflater.from(parent.context).inflate(R.layout.view_features_item, parent, false)
        return FeaturesViewHolder(view, onClick)
    }

    override fun getItemCount(): Int = features.size

    override fun onBindViewHolder(holder: FeaturesViewHolder, position: Int) {
        holder.bind(features[position])
    }
}
