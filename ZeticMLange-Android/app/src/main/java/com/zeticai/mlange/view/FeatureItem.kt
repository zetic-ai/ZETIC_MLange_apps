package com.zeticai.mlange.view

import android.app.Activity
import com.zeticai.mlange.core.model.ZeticMLangeTarget

data class FeatureItem(
    val name: String,
    val activity: Class<out Activity>,
    val modelKeys: List<String>,
    var modelStatus: ModelStatus = ModelStatus.NOT_READY,
    val target: ZeticMLangeTarget? = null
)
