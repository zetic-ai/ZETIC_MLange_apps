package com.zeticai.chronos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF34A9A3),
                    secondary = Color(0xFF757575), // Darker gray for light mode
                    background = Color.White,
                    surface = Color.White,
                    onPrimary = Color.White,
                    onSurface = Color.Black
                )
            ) {
                ChronosScreen()
            }
        }
    }
}

@Composable
fun ChronosScreen(viewModel: ChronosForecastViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var showSettings by remember { mutableStateOf(false) }

    // File Pickers
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
             try {
                 val inputStream = context.contentResolver.openInputStream(it)
                 val reader = BufferedReader(InputStreamReader(inputStream))
                 val text = reader.use { r -> r.readText() }
                 viewModel.importCSV(text)
             } catch (e: Exception) {
                 viewModel.updateState { s -> s.copy(errorMessage = "Import failed: ${e.localizedMessage}") }
             }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { stream ->
                    stream.write(state.outputCSV.toByteArray())
                }
            } catch (e: Exception) {
                 viewModel.updateState { s -> s.copy(errorMessage = "Export failed: ${e.localizedMessage}") }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF34A9A3), Color(0xFF34A9A3).copy(alpha = 0.8f), Color.White)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(scrollState)
                .padding(vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Header()

            // Model Status
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Model Status", style = MaterialTheme.typography.titleMedium, color = Color.Black)
                        
                        // Status Indicator
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val statusColor = when {
                                state.modelStatusText.contains("Ready", ignoreCase = true) -> Color(0xFF2E7D32) // Darker Green
                                state.modelStatusText.contains("Loading", ignoreCase = true) || state.modelStatusText.contains("Downloading", ignoreCase = true) -> Color(0xFFFF9800)
                                state.errorMessage != null -> Color.Red
                                else -> Color.Gray
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(statusColor, shape = androidx.compose.foundation.shape.CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(state.modelStatusText, style = MaterialTheme.typography.bodyMedium, color = statusColor)
                        }
                    }

                    if (state.modelStatusText.startsWith("Downloading") && state.downloadProgress > 0 && state.downloadProgress < 1.0f) {
                        LinearProgressIndicator(
                            progress = { state.downloadProgress },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = Color(0xFFFF9800), // Orange
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    if (state.errorMessage != null) {
                         Text(
                             text = state.errorMessage!!,
                             color = MaterialTheme.colorScheme.error,
                             style = MaterialTheme.typography.labelSmall,
                             modifier = Modifier.padding(top = 4.dp)
                         )
                    }
                }
            }

            // Input Card
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Top: Example Name
                    Column {
                         Text("Example Name:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                         Text(
                             state.selectedExample?.name ?: "User File",
                             style = MaterialTheme.typography.titleMedium,
                             fontWeight = FontWeight.Bold,
                             color = Color.Black
                         )
                    }
                    
                    // Bottom: Buttons (Right Aligned)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                         // Example Menu
                        Box {
                            var expanded by remember { mutableStateOf(false) }
                            Button(
                                onClick = { expanded = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Example")
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier.background(Color.White)
                            ) {
                                ExampleData.allValues.forEach { example ->
                                    DropdownMenuItem(
                                        text = { Text(example.name, color = Color.Black) },
                                        onClick = {
                                            viewModel.loadExample(example)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        Button(
                            onClick = { importLauncher.launch("text/*") },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("Import")
                        }
                    }
                }

                if (state.chartHistory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ForecastChart(
                        history = state.chartHistory,
                        forecast = emptyList(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "CSV should include time series values with columns for ID, timestamp, and target.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
                
                CsvEditor(
                    headers = state.tableHeaders,
                    rows = state.tableRows,
                    onCellChange = { r, c, v -> viewModel.updateTableCell(r, c, v) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            // Settings Card
            SectionCard {
                Text("Forecast Settings", style = MaterialTheme.typography.titleMedium, color = Color.Black)
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledField("Prediction Length", state.predictionLength, Modifier.weight(1f)) {
                        viewModel.updateState { s -> s.copy(predictionLength = it) }
                    }
                    LabeledField("Quantiles", state.quantilesText, Modifier.weight(1f)) {
                        viewModel.updateState { s -> s.copy(quantilesText = it) }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                     LabeledField("ID Column", state.idColumn, Modifier.weight(1f)) {
                        viewModel.updateState { s -> s.copy(idColumn = it) }
                     }
                     LabeledField("Timestamp Column", state.timestampColumn, Modifier.weight(1f)) {
                        viewModel.updateState { s -> s.copy(timestampColumn = it) }
                     }
                }
                Spacer(modifier = Modifier.height(12.dp))
                LabeledField("Target Column", state.targetColumn, Modifier.fillMaxWidth()) {
                     viewModel.updateState { s -> s.copy(targetColumn = it) }
                }
                
                if (state.availableSeriesIds.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Select Series ID", style = MaterialTheme.typography.labelSmall, color = Color.LightGray)
                    // Simple Dropdown or Scrollable Row
                    ScrollableTabRow(
                        selectedTabIndex = state.availableSeriesIds.indexOf(state.selectedSeriesId).coerceAtLeast(0),
                        edgePadding = 0.dp,
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        state.availableSeriesIds.forEach { id ->
                            Tab(
                                selected = state.selectedSeriesId == id,
                                onClick = { viewModel.onSeriesSelected(id) },
                                text = { Text(id) }
                            )
                        }
                    }
                }
            }

            // Run Button
            Button(
                onClick = { viewModel.runForecast() },
                enabled = !state.isRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                if (state.isRunning) {
                     CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                     Spacer(modifier = Modifier.width(12.dp))
                     Text("Running...")
                } else {
                     Text("Run Forecast", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Output Card
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Forecast Output", style = MaterialTheme.typography.titleMedium, color = Color.Black)
                    if (state.hasForecast && state.outputCSV.isNotEmpty()) {
                        Button(onClick = { exportLauncher.launch("chronos_forecast_output.csv") }) {
                            Text("Export CSV")
                        }
                    }
                }

                if (!state.hasForecast) {
                    Text(
                        "Run forecast to generate output.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    ForecastChart(
                        history = state.chartHistory, // Pass actual history
                        forecast = state.chartForecast,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            
            Text(
                text = "Powered by MLange",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Gray,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 16.dp)
            )
        }

        if (showSettings) {
             SettingsDialog(
                 state = state,
                 onDismiss = { showSettings = false },
                 onSave = { token, name, ver, norm ->
                     viewModel.updateState { it.copy(tokenKey = token, modelName = name, modelVersion = ver, normalizationMode = norm) }
                     viewModel.loadModel()
                     showSettings = false
                 }
             )
        }
    }
}

@Composable
fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Rounded.DateRange,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = Color.White
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                "Chronos Future Forecast",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                "Powered by MLange",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
fun LabeledField(label: String, value: String, modifier: Modifier = Modifier, onValueChange: (String) -> Unit) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.LightGray,
                focusedTextColor = Color.Black,
                unfocusedTextColor = Color.Black.copy(alpha = 0.8f),
                cursorColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
fun SettingsDialog(state: ChronosUiState, onDismiss: () -> Unit, onSave: (String, String, String, String) -> Unit) {
    var token by remember { mutableStateOf(state.tokenKey) }
    var name by remember { mutableStateOf(state.modelName) }
    var ver by remember { mutableStateOf(state.modelVersion) }
    var norm by remember { mutableStateOf(state.normalizationMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Model Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledField("Token Key", token) { token = it }
                LabeledField("Model Name", name) { name = it }
                LabeledField("Version", ver) { ver = it }
                LabeledField("Normalization (asinh_zscore | zscore | none)", norm) { norm = it }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(token, name, ver, norm) }) {
                Text("Load & Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ForecastChart(
    history: List<SeriesPoint>,
    forecast: List<ForecastPoint>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.3f))) {
        val width = size.width
        val height = size.height
        
        // Check bounds
        val allValues = history.map { it.value } + 
                        forecast.flatMap { listOf(it.q10, it.q50, it.q90) }
        
        if (allValues.isEmpty()) return@Canvas
        
        val minVal = allValues.minOrNull()?.toFloat() ?: 0f
        val maxVal = allValues.maxOrNull()?.toFloat() ?: 1f
        val range = (maxVal - minVal).coerceAtLeast(1e-6f)
        
        val totalPoints = history.size + forecast.size
        if (totalPoints < 2) return@Canvas
        
        val stepX = width / (totalPoints - 1).toFloat()
        
        fun x(index: Int) = index * stepX
        fun y(v: Double) = height - ((v.toFloat() - minVal) / range) * height
        
        // Draw History
        if (history.isNotEmpty()) {
            val path = Path()
            path.moveTo(x(0), y(history[0].value))
            for (i in 1 until history.size) {
                 path.lineTo(x(i), y(history[i].value))
            }
            drawPath(
                path = path,
                color = Color(0xFF5E5CE6),
                style = Stroke(width = 3.dp.toPx())
            )
        }
        
            // Draw Forecast
            if (forecast.isNotEmpty()) {
                val startIndex = history.size
                val startX = x(startIndex)
                
                // Draw Vertical Separator Line
                val separatorPath = Path()
                separatorPath.moveTo(startX, 0f)
                separatorPath.lineTo(startX, height)
                
                // Dash effect for separator
                 drawPath(
                    path = separatorPath,
                    color = Color.Gray.copy(alpha = 0.5f),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                )

                // Area (Band) between q10 and q90
                val areaPath = Path()
                
                // Calculate paths with offset
                areaPath.moveTo(x(startIndex), y(forecast[0].q90))
                for (i in 1 until forecast.size) {
                    areaPath.lineTo(x(startIndex + i), y(forecast[i].q90))
                }
                for (i in forecast.indices.reversed()) {
                    areaPath.lineTo(x(startIndex + i), y(forecast[i].q10))
                }
                areaPath.close() // Close shape
                
                drawPath(
                    path = areaPath,
                    color = Color(0xFFFF9500).copy(alpha = 0.3f) // Orange 30%
                )
                
                // Median Line (q50)
                val linePath = Path()
                // Connect from last history point if available
                if (history.isNotEmpty()) {
                    linePath.moveTo(x(startIndex - 1), y(history.last().value))
                    linePath.lineTo(x(startIndex), y(forecast[0].q50))
                } else {
                    linePath.moveTo(x(startIndex), y(forecast[0].q50))
                }
                
                for (i in 1 until forecast.size) {
                    linePath.lineTo(x(startIndex + i), y(forecast[i].q50))
                }
                 drawPath(
                    path = linePath,
                    color = Color(0xFFFF9500), // Orange
                    style = Stroke(width = 4.dp.toPx()) // Bold
                )
                
                // Draw dots for forecast
                 for (i in forecast.indices) {
                     drawCircle(
                         color = Color(0xFFFF9500),
                         radius = 3.dp.toPx(),
                         center = Offset(x(startIndex + i), y(forecast[i].q50))
                     )
                 }
            }
    }
}
