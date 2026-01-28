package com.zeticai.chronos

import java.util.UUID

data class ExampleDataset(
    val id: String,
    val name: String,
    val csvContent: String,
    val idColumn: String,
    val timestampColumn: String,
    val targetColumn: String,
    val defaultPredictionLength: String = "24",
    val defaultQuantiles: String = "0.1, 0.5, 0.9"
)

data class SeriesPoint(
    val timestamp: String,
    val value: Double
)

data class ForecastPoint(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: String,
    val prediction: Double,
    val q10: Double,
    val q50: Double,
    val q90: Double
)

data class TimeSeriesDataset(
    val series: Map<String, List<SeriesPoint>>
)

data class NormalizationScale(
    val mean: Float,
    val std: Float,
    val useArcsinh: Boolean = true
)

data class ForecastBundle(
    val byQuantile: Map<String, List<Float>>
)
