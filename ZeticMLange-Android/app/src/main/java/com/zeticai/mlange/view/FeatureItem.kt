package com.zeticai.mlange.view

import android.app.Activity

data class FeatureItem(
    val name: String,
    val activity: Class<out Activity>,
    val modelKeys: List<String>,
    var modelStatus: ModelStatus = ModelStatus.NOT_READY,
)
