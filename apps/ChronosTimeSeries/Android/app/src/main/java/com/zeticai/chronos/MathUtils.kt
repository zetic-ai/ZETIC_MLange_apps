package com.zeticai.chronos

import kotlin.math.ln
import kotlin.math.sqrt
import kotlin.math.exp
import kotlin.math.pow

object MathUtils {

    fun asinh(x: Float): Float {
        return ln(x + sqrt(x * x + 1.0f))
    }

    fun sinh(x: Float): Float {
        return (exp(x) - exp(-x)) / 2.0f
    }

    fun from(values: List<Float>, mode: String): NormalizationScale {
        val normalizedMode = mode.lowercase()
        val useArcsinh = normalizedMode == "asinh_zscore" || normalizedMode == "asinh"

        val raw = values.filter { !it.isNaN() }
        if (raw.isEmpty()) {
            return NormalizationScale(0f, 1f, useArcsinh)
        }

        if (normalizedMode == "mean_scale" || normalizedMode == "mean") {
            // Mean Absolute Scaling: sum(|x|) / N
            val meanAbs = raw.map { kotlin.math.abs(it) }.sum() / raw.size
            val scale = if (meanAbs < 1e-5f) 1.0f else meanAbs
            return NormalizationScale(0f, scale, false)
        }

        val mean = raw.sum() / raw.size
        val variance = raw.map { (it - mean).pow(2) }.sum() / raw.size
        val std = java.lang.Float.max(sqrt(variance), 1e-5f)
        
        return NormalizationScale(mean, std, useArcsinh)
    }
}

fun NormalizationScale.normalize(value: Float): Float {
    if (value.isNaN()) return Float.NaN
    val scaled = (value - mean) / std
    return if (useArcsinh) MathUtils.asinh(scaled) else scaled
}

fun NormalizationScale.denormalize(value: Float): Float {
    val deTransformed = if (useArcsinh) MathUtils.sinh(value) else value
    return deTransformed * std + mean
}
