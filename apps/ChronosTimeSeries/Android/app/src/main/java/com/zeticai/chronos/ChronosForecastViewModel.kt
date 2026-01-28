package com.zeticai.chronos

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zeticai.mlange.core.model.ZeticMLangeModel
import com.zeticai.mlange.core.tensor.DataType
import com.zeticai.mlange.core.tensor.Tensor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

// Placeholder for script replacement
const val MLANGE_PERSONAL_ACCESS_TOKEN = "YOUR_MLANGE_KEY"

data class ChronosUiState(
    val csvText: String = "",
    val outputCSV: String = "",
    val predictionLength: String = "36",
    val quantilesText: String = "0.1,0.5,0.9",
    val idColumn: String = "item_id",
    val timestampColumn: String = "Month",
    val targetColumn: String = "#Passengers",
    val availableSeriesIds: List<String> = emptyList(),
    val selectedSeriesId: String = "",
    val tokenKey: String = MLANGE_PERSONAL_ACCESS_TOKEN,
    val modelName: String = "Team_ZETIC/Chronos-balt-tiny",
    val modelVersion: String = "5",
    val normalizationMode: String = "mean_scale",
    val isRunning: Boolean = false,
    val downloadProgress: Float = 0f,
    val errorMessage: String? = null,
    val modelStatusText: String = "Model not loaded",
    val hasForecast: Boolean = false,
    val chartHistory: List<SeriesPoint> = emptyList(),
    val chartForecast: List<ForecastPoint> = emptyList(),
    val tableHeaders: List<String> = emptyList(),
    val tableRows: List<List<String>> = emptyList(),
    val selectedExample: ExampleDataset? = null
)

class ChronosForecastViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ChronosUiState())
    val uiState = _uiState.asStateFlow()

    private val manager = ChronosManager(application)
    private var dataset: TimeSeriesDataset? = null

    // Updated Model Info
    // CONTEXT_LENGTH removed (internal to Manager)

    init {
        loadExample(ExampleData.hourlyDemand)
        loadModel()
    }
    
    // Helper for State Update
    fun updateState(block: (ChronosUiState) -> ChronosUiState) {
        _uiState.update(block)
    }

    fun loadExample(example: ExampleDataset) {
        updateState { 
            it.copy(
                selectedExample = example,
                selectedSeriesId = example.id,
                // If ID column is empty in example, use default
                idColumn = if(example.idColumn.isNotEmpty()) example.idColumn else "id",
                timestampColumn = example.timestampColumn,
                targetColumn = example.targetColumn,
                predictionLength = example.defaultPredictionLength, 
                quantilesText = example.defaultQuantiles,
                csvText = example.csvContent,
                outputCSV = "",
                hasForecast = false,
                chartForecast = emptyList()
            ) 
        }
        importCSV(example.csvContent)
    }

    fun importCSV(text: String) {
        val (headers, rows) = parseCsvToTable(text)
        // If importing custom text, clear selectedExample unless it matches (simplification: just null it for custom imports)
        // Note: loadExample calls this, so it might overwrite if we are strict. 
        // Better: importCSV shouldn't null it blindly if called from loadExample.
        // But since loadExample updates state BEFORE calling importCSV, we should handle this carefully.
        // Actually, let's just update headers/rows here. The caller (loadExample) sets the example object. 
        // If called from UI "Import", the user probably wants to clear the example name.
        // We can check if the text matches the current example? No, too expensive.
        // Let's assume if this is called directly from UI, we might want to clear it.
        // But for getting it to compile let's just update table data.
        
        // Strategy: Only update table data here. The ID/Name update logic is handled by the caller or by explicit state update.
        updateState { it.copy(csvText = text, tableHeaders = headers, tableRows = rows, errorMessage = null) }
        
        // If this was a manual import (implied by checking if text matches selectedExample?), that's tricky.
        // For now, let's just fix the compilation. Correct logic: The UI expects `viewModel.selectedExample?.name`.
        // If user imports a file, we should probably set selectedExample = null.
        // BUT importCSV is called by loadExample.
        // So we will modify the call site in MainActivity for manual import, OR change this method signature.
        
        parseCSVIfReady()
    }
    
    fun onSeriesSelected(seriesId: String) {
        updateState { it.copy(selectedSeriesId = seriesId) }
        // Potentially refresh chart history
        val series = dataset?.series?.get(seriesId)
        if (series != null) {
            updateState { it.copy(chartHistory = series) }
        }
    }

    private fun parseCSVIfReady() {
        val state = _uiState.value
        if (state.csvText.isBlank()) return
        
        try {
            // Simple Parsing Logic using CSVParser util or similar
            // For now, let's implement a simple parser here or use the existing structure
            // We assume TimeSeriesDataset logic is available or we reconstruct it
            
            // Assume CSV structure: Header + Rows
            val lines = state.csvText.split("\n", "\r\n").filter { it.isNotBlank() }
            if (lines.isEmpty()) return
            
            val header = lines.first().split(",").map { it.trim() }
            // indices
            val idIdx = header.indexOf(state.idColumn.trim())
            val timeIdx = header.indexOf(state.timestampColumn.trim())
            val targetIdx = header.indexOf(state.targetColumn.trim())
            
            // If columns missing, we can't fully parse dataset for model, but we have table data
            if (timeIdx == -1 || targetIdx == -1) {
                 // updateState { it.copy(errorMessage = "Columns not found: ${state.timestampColumn}, ${state.targetColumn}") }
                 return
            }
            
            val seriesMap = mutableMapOf<String, MutableList<SeriesPoint>>()
            
            for (i in 1 until lines.size) {
                val parts = lines[i].split(",").map { it.trim() }
                if (parts.size != header.size) continue
                
                val id = if (idIdx != -1) parts[idIdx] else "default"
                val ts = parts[timeIdx]
                val value = parts[targetIdx].toDoubleOrNull() ?: 0.0
                
                seriesMap.getOrPut(id) { mutableListOf() }.add(SeriesPoint(ts, value))
            }
            
            dataset = TimeSeriesDataset(seriesMap)
            
            val ids = seriesMap.keys.toList().sorted()
            val currentId = if (ids.contains(state.selectedSeriesId)) state.selectedSeriesId else ids.firstOrNull() ?: ""
            
            updateState { 
                it.copy(
                    availableSeriesIds = ids, 
                    selectedSeriesId = currentId
                ) 
            }
            
            // Update History Chart
            val currentSeries = seriesMap[currentId]
            if (currentSeries != null) {
                updateState { it.copy(chartHistory = currentSeries) }
            }
            
        } catch (e: Exception) {
            updateState { it.copy(errorMessage = "CSV Parse Error: ${e.localizedMessage}") }
        }
    }

    private fun parseQuantiles(text: String): List<Float> {
        return text.split(",").mapNotNull { it.trim().toFloatOrNull() }
    }

    fun loadModel() {
        val state = _uiState.value
        updateState { it.copy(errorMessage = null, modelStatusText = "Loading model...") }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Use state values directly (Single Source of Truth)
                manager.loadModel(state.tokenKey, state.modelName, state.modelVersion.toIntOrNull() ?: 5) { progress ->
                    viewModelScope.launch(Dispatchers.Main) {
                        updateState {
                            it.copy(
                                modelStatusText = "Downloading... ${(progress * 100).toInt()}%",
                                downloadProgress = progress
                            )
                        }
                    }
                }
                updateState { it.copy(modelStatusText = "Model ready", errorMessage = null, downloadProgress = 1.0f) }
            } catch (e: Exception) {
                updateState {
                    it.copy(
                        modelStatusText = "Model failed to load",
                        errorMessage = "Failed to load: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    // ...

    fun runForecast() {
        val state = _uiState.value
        updateState {
            it.copy(errorMessage = null, outputCSV = "", hasForecast = false, isRunning = true)
        }

        // Check if dataset ready
        parseCSVIfReady()
        if (dataset == null) {
            updateState { it.copy(errorMessage = "Please import CSV first.", isRunning = false) }
            return
        }
        
        val series = dataset?.series?.get(state.selectedSeriesId)
        if (series.isNullOrEmpty()) {
            updateState { it.copy(errorMessage = "No history found for selected ID.", isRunning = false) }
            return
        }
        
        val horizon = state.predictionLength.trim().toIntOrNull() ?: 24
        val quantiles = parseQuantiles(state.quantilesText)

        viewModelScope.launch(Dispatchers.Default) {
             try {
                 val values = series.map { it.value.toFloat() }
                 
                 // Run Inference via Manager
                 val result = manager.runInference(values, horizon, quantiles)
                 
                 // 5. Build CSV
                 val timestamps = buildFutureTimestamps(series.last().timestamp, horizon)
                 val csv = buildOutputCSV(state.selectedSeriesId, state.targetColumn, timestamps, result.forecast, horizon, quantiles)
                 val forecastPoints = parseForecastPoints(timestamps, result.forecast)
                 
                 updateState {
                     it.copy(outputCSV = csv, hasForecast = true, isRunning = false, chartForecast = forecastPoints)
                 }
                 
             } catch (e: Exception) {
                 updateState { it.copy(errorMessage = "Error: ${e.localizedMessage}", isRunning = false) }
             }
        }
    }
    
    // Removed prepareInputs (Moved to Manager)
    


    // Removed parseForecastBolt (Moved to Manager)

    // Removed old parsing logic


    private fun formatQuantileLabel(q: Float): String {
        return "q" + String.format("%.2f", q).replace(".", "_")
    }

    private fun buildFutureTimestamps(lastTimestamp: String, horizon: Int): List<String> {
        // Try parsing
        val formats = listOf("yyyy-MM-dd", "yyyy-MM", "yyyy/MM/dd", "yyyy/M/d")
        var date: java.time.LocalDate? = null
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            for (fmt in formats) {
                try {
                    // Simple parser attempt
                    // If yyyy-MM, append -01
                    var ts = lastTimestamp.trim()
                    if (fmt == "yyyy-MM" && ts.count { it == '-' } == 1) {
                        ts += "-01"
                        date = java.time.LocalDate.parse(ts, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    } else {
                        date = java.time.LocalDate.parse(ts, java.time.format.DateTimeFormatter.ofPattern(fmt))
                    }
                    if (date != null) break
                } catch (e: Exception) { continue }
            }
            
            if (date != null) {
                val list = mutableListOf<String>()
                var current = date
                val outFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
                for (i in 0 until horizon) {
                    current = current!!.plusMonths(1)
                    list.add(current.format(outFmt))
                }
                return list
            }
        }
        
        return (1..horizon).map { "step-$it" }
    }
    
    private fun buildOutputCSV(
        seriesId: String,
        targetName: String,
        timestamps: List<String>,
        forecast: ForecastBundle,
        horizon: Int,
        quantiles: List<Float>
    ): String {
        val predictionKey = formatQuantileLabel(0.5f)
        val quantileColumns = quantiles.map { formatQuantileColumn(it) }
        val header = listOf("item_id", "Month", "target_name", "predictions") + quantileColumns
        val rows = mutableListOf(header.joinToString(","))

        for (step in 0 until horizon) {
            val timestamp = if (step < timestamps.size) timestamps[step] else "step-${step + 1}"
            
            // Safe get
            val predictionSeries = forecast.byQuantile[predictionKey] ?: forecast.byQuantile["mean"]
            val prediction = predictionSeries?.getOrNull(step) ?: 0f
            
            val row = mutableListOf<String>()
            row.add(seriesId)
            row.add(timestamp)
            row.add(targetName)
            row.add(String.format("%.6f", prediction))
            
            for (q in quantiles) {
                val key = formatQuantileLabel(q)
                val series = forecast.byQuantile[key]
                val value = series?.getOrNull(step) ?: prediction
                row.add(String.format("%.6f", value))
            }
            rows.add(row.joinToString(","))
        }
        return rows.joinToString("\n")
    }
    
    // Direct mapping from data, no CSV parse needed for internal UI
    private fun parseForecastPoints(timestamps: List<String>, forecast: ForecastBundle): List<ForecastPoint> {
        // Need q0.1, q0.5, q0.9 specifically for the chart area/line
        // If user changed quantiles, we try to match closest or default.
        // The chart expects specific q10, q50, q90.
        // We look for keys from standard quantiles 0.1, 0.5, 0.9
        
        val q10Key = formatQuantileLabel(0.1f)
        val q50Key = formatQuantileLabel(0.5f)
        val q90Key = formatQuantileLabel(0.9f)
        
        val q10Series = forecast.byQuantile[q10Key] ?: forecast.byQuantile.values.firstOrNull() ?: emptyList()
        val q50Series = forecast.byQuantile[q50Key] ?: forecast.byQuantile["mean"] ?: emptyList()
        val q90Series = forecast.byQuantile[q90Key] ?: forecast.byQuantile.values.lastOrNull() ?: emptyList()
        
        val points = mutableListOf<ForecastPoint>()
        // Assume all series same length
        val size = q50Series.size
        
        for (i in 0 until size) {
            val ts = timestamps.getOrNull(i) ?: "step-$i"
            val q10 = q10Series.getOrNull(i) ?: 0f
            val q50 = q50Series.getOrNull(i) ?: 0f
            val q90 = q90Series.getOrNull(i) ?: 0f
            
            points.add(ForecastPoint(
                timestamp = ts,
                prediction = q50.toDouble(),
                q10 = q10.toDouble(), 
                q50 = q50.toDouble(),
                q90 = q90.toDouble()
            ))
        }
        return points
    }

    fun updateTableCell(rowIndex: Int, colIndex: Int, newValue: String) {
        val currentRows = _uiState.value.tableRows.toMutableList()
        if (rowIndex in currentRows.indices) {
            val row = currentRows[rowIndex].toMutableList()
            if (colIndex in row.indices) {
                row[colIndex] = newValue
                currentRows[rowIndex] = row
                
                // Reconstruct CSV
                val headers = _uiState.value.tableHeaders
                val csvContent = buildCsvFromTable(headers, currentRows)
                
                // Update State (this triggers re-import which updates chart too)
                importCSV(csvContent)
            }
        }
    }

    private fun parseCsvToTable(text: String): Pair<List<String>, List<List<String>>> {
        val lines = text.split("\n", "\r\n").filter { it.isNotBlank() }
        if (lines.isEmpty()) return Pair(emptyList(), emptyList())
        
        // Simple split by comma, respecting basic quotes if possible or just simple split
        // reusing CSVParser logic roughly but we need generic table
        val headers = lines.first().split(",").map { it.trim() }
        val rows = lines.drop(1).map { line ->
            line.split(",").map { it.trim() }
        }
        return Pair(headers, rows)
    }
    
    private fun buildCsvFromTable(headers: List<String>, rows: List<List<String>>): String {
        val headerLine = headers.joinToString(",")
        val dataLines = rows.joinToString("\n") { row -> row.joinToString(",") }
        return headerLine + "\n" + dataLines
    }

    private fun formatQuantileColumn(q: Float): String {
        val s = String.format("%.2f", q)
        if (s.endsWith(".00")) return s.dropLast(3)
        if (s.endsWith("0")) return s.dropLast(1)
        return s
    }
}
