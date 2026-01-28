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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextDecoration
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
    val progress by modelManager.downloadProgress.collectAsState()
    
    var inputText by remember { mutableStateOf("") }
    var executedInputText by remember { mutableStateOf("") }
    var outputText by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    
    val examples = listOf(
        // Short examples with multiple error types
        "I has a apple",
        "He go to school yesterday",
        "She don't likes it",
        "My grammar are bad",
        "I am write a letter",
        // Medium examples with mixed errors
        "The students was studying hard for they exams but they was not prepare enough",
        "Yesterday I goes to the store and buyed some apples but I forgetted to bring my wallet so I had to goes back home",
        "She don't know what to do because her friends was not there and she feeled very alone",
        "The teacher explain the lesson but the students was not listening careful and they was making noise",
        "I was trying to write a essay about my summer vacation but I was having trouble with grammar and I was not sure if I was using the right words",
        // Long examples with many errors: tense, articles, agreement, prepositions
        "When I was a child I always wanted to be a doctor because I thought it was a very important job and I wanted to help peoples but as I growed up I realized that becoming a doctor was very difficult and required many years of studying and I was not sure if I was smart enough to do it because I was not good at sciences and I was always struggled with math and chemistry classes",
        "Last summer my family and I went on a vacation to the beach and we was planning to stay there for a whole week but the weather was not very good and it was raining almost every day so we was not able to do many of the activities that we was planning to do like swimming and playing volleyball on the sand because the rain was making everything wet and muddy and we was disappointed about our vacation",
        "The book that I was reading last week was very interesting and it was about a young boy who discover a secret garden behind his house and he was spending all his free time there planting flowers and vegetables and making friends with the animals that was living in the garden but his parents was not knowing about the garden and they was worried about where he was going every day",
        "My teacher always tell us that we should practice writing every day if we want to improve our skills but I was finding it very difficult to find time to write because I was having so many other things to do like homework and chores and spending time with my friends and I was always tired after school so I was not having enough energy to write",
        "The science project that we was working on for the school fair was about how plants grow in different conditions and we was doing experiments with different types of soil and amounts of water and sunlight to see which combination would help the plants grow the best and fastest but we was making many mistakes and our plants was not growing well so we was worried that we was going to fail the project",
        // Preposition/Collocation examples
        "We are interested on partnering with your company",
        "I will contact to you once I get the result",
        "This will have an effect to our performance",
        "I'm responsible of the onboarding process",
        "We need to focus in improving reliability",
        // More examples with mixed errors: articles, tense, agreement, prepositions
        "A students in my class was always late to the school and he was not doing his homeworks because he was spending too much time on playing video games",
        "The company that I work for was planning to expand their business to the new markets but they was not having enough resources and they was struggling with finding the right peoples for the job",
        "I was very excited about going to the university because I thought it would be a great opportunity to learn new things and meet new friends but when I actually went there I was finding it very difficult to adapt to the new environment",
        "The books that I was reading last month was very interesting and they was helping me to understand more about the world and the different cultures that exist in different countries around the world",
        "My parents was always telling me that I should study hard if I want to succeed in the life but I was not listening to their advices and I was making many mistakes that I was regretting later"
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("T5 Grammar Correction")
                        Text(
                            text = "Powered by MLange", 
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                },
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
            StatusIndicator(isLoaded, error, progress)
            
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
                        executedInputText = inputText // Capture input for highlighting
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
                    HighlightedText(
                        original = executedInputText,
                        corrected = outputText
                    )
                }
            }
        }
    }
}

@Composable
fun StatusIndicator(isLoaded: Boolean, error: String?, progress: Float) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!isLoaded) {
            if (error != null) {
                Text("Error Loading Model: $error", color = MaterialTheme.colorScheme.error)
            } else {
                if (progress > 0) {
                     CircularProgressIndicator(
                        modifier = Modifier.size(16.dp), 
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Downloading Model... ${String.format("%.1f", progress)}%", style = MaterialTheme.typography.bodySmall)
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Initializing Model...", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Text("✅ Model Ready", color = Color(0xFF4CAF50), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun HighlightedText(original: String, corrected: String) {
    val annotatedString = remember(original, corrected) {
        highlightDifferences(original, corrected)
    }

    Text(
        text = annotatedString,
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer
    )
}

fun highlightDifferences(original: String, corrected: String): AnnotatedString {
    return buildAnnotatedString {
        val originalWords = splitWordsPreservingPunctuation(original)
        val correctedWords = splitWordsPreservingPunctuation(corrected)
        
        val originalWordPositions = mutableMapOf<String, MutableList<Int>>()
        originalWords.forEachIndexed { index, word ->
            val normalized = normalizeWord(word)
            if (normalized.isNotEmpty()) {
                originalWordPositions.getOrPut(normalized) { mutableListOf() }.add(index)
            }
        }
        
        val usedOriginalIndices = mutableSetOf<Int>()
        var originalIndex = 0
        
        for ((corrIndex, corrWord) in correctedWords.withIndex()) {
            val corrNormalized = normalizeWord(corrWord)
            var isMatched = false
            
            // Try match at current position
            if (originalIndex < originalWords.size) {
                val origNormalized = normalizeWord(originalWords[originalIndex])
                if (origNormalized == corrNormalized && !usedOriginalIndices.contains(originalIndex)) {
                    append(corrWord)
                    usedOriginalIndices.add(originalIndex)
                    originalIndex++
                    isMatched = true
                }
            }
            
            // Fuzzy match
            if (!isMatched && corrNormalized.isNotEmpty()) {
                originalWordPositions[corrNormalized]?.let { positions ->
                    for (pos in positions) {
                        if (!usedOriginalIndices.contains(pos)) {
                            val distance = kotlin.math.abs(pos - originalIndex)
                            if (distance <= 10) {
                                append(corrWord)
                                usedOriginalIndices.add(pos)
                                if (pos >= originalIndex) {
                                    originalIndex = pos + 1
                                }
                                isMatched = true
                                break
                            }
                        }
                    }
                }
            }
            
            // If new/changed
            if (!isMatched) {
                withStyle(SpanStyle(
                    color = Color(0xFF00C853), // Green
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline
                )) {
                    append(corrWord)
                }
            }
            
            if (corrIndex < correctedWords.size - 1) {
                append(" ")
            }
        }
    }
}

fun splitWordsPreservingPunctuation(text: String): List<String> {
    return text.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
}

fun normalizeWord(word: String): String {
    return word.lowercase().trim { !it.isLetterOrDigit() }
}
