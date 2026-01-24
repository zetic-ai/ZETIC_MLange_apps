package com.zeticai.t5grammar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val modelManager = T5ModelManager(this)
        
        setContent {
            MaterialTheme {
                T5GrammarScreen(modelManager)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun T5GrammarScreen(modelManager: T5ModelManager) {
    val isLoaded by modelManager.isModelLoaded.collectAsState()
    val error by modelManager.error.collectAsState()
    
    var inputText by remember { mutableStateOf("") }
    var outputText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    val examples = listOf(
        "I has a apple",
        "He go to school yesterday",
        "She don't likes it",
        "My grammar are bad",
        "I am write a letter"
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("T5 Grammar Correction") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status Section
            StatusIndicator(isLoaded, error)
            
            // Input Section
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text("Input Text") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                enabled = !isProcessing
            )
            
            // Examples Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Just show first 2 for brevity on small screens, or scrollable row
                // Let's use a horizontal Scroll Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                       examples.forEach { example ->
                           SuggestionChip(
                               onClick = { inputText = example },
                               label = { Text(example) }
                           )
                       }
                }
            }

            // Action Button
            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        focusManager.clearFocus()
                        isProcessing = true
                        outputText = "" // Clear previous
                        scope.launch {
                            try {
                                outputText = modelManager.runInference(inputText)
                            } catch (e: Exception) {
                                outputText = "Error: ${e.message}"
                            } finally {
                                isProcessing = false
                            }
                        }
                    }
                },
                enabled = isLoaded && !isProcessing && inputText.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Correcting...")
                } else {
                    Text("Correct Grammar")
                }
            }
            
            // Output Section
            if (outputText.isNotEmpty()) {
                Text(
                    text = "Correction Result:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Start)
                )
                
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = outputText,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    }
}

@Composable
fun StatusIndicator(isLoaded: Boolean, error: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!isLoaded) {
            if (error != null) {
                Text("Error Loading Model", color = MaterialTheme.colorScheme.error)
            } else {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Loading Model...", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Text("✅ Model Ready", color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodySmall)
        }
    }
}
