package com.zeticai.textanonymizer

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zeticai.textanonymizer.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private val viewModel: AnonymizerViewModel by lazy {
        ViewModelProvider(
            this,
            viewModelFactory {
                initializer {
                    AnonymizerViewModel(this@MainActivity)
                }
            }
        )[AnonymizerViewModel::class.java]
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupObservers()
        setupClickListeners()
        setupTextWatcher()
        updateAnonymizeButtonState()
    }
    
    private fun setupObservers() {
        // Observe model loading state
        viewModel.isModelLoaded.observe(this) { isLoaded ->
            if (isLoaded) {
                binding.progressModelLoading.visibility = View.GONE
                binding.textModelLoading.visibility = View.GONE
            } else {
                binding.progressModelLoading.visibility = View.VISIBLE
                binding.textModelLoading.visibility = View.VISIBLE
            }
            updateAnonymizeButtonState()
        }
        
        // Observe processing state
        viewModel.isProcessing.observe(this) { isProcessing ->
            updateAnonymizeButtonState()
            if (isProcessing) {
                binding.progressAnonymizing.visibility = View.VISIBLE
                binding.buttonAnonymize.text = "Anonymizing..."
            } else {
                binding.progressAnonymizing.visibility = View.GONE
                binding.buttonAnonymize.text = "Anonymize Text"
            }
        }
        
        // Observe anonymized text
        viewModel.anonymizedText.observe(this) { text ->
            if (text.isNotEmpty()) {
                binding.textAnonymizedOutput.text = text
                binding.layoutOutputSection.visibility = View.VISIBLE
            } else {
                binding.layoutOutputSection.visibility = View.GONE
            }
        }
        
        // Observe errors
        viewModel.showingError.observe(this) { showing ->
            if (showing) {
                val message = viewModel.errorMessage.value ?: "An error occurred"
                AlertDialog.Builder(this)
                    .setTitle("Error")
                    .setMessage(message)
                    .setPositiveButton("OK") { _, _ ->
                        // Error dialog dismissed
                    }
                    .setOnDismissListener {
                        // Reset error state after dialog is dismissed
                        viewModel.clearError()
                    }
                    .show()
            }
        }
    }
    
    private fun setupClickListeners() {
        // Anonymize button
        binding.buttonAnonymize.setOnClickListener {
            val inputText = binding.editTextInput.text.toString()
            if (inputText.isNotEmpty()) {
                hideKeyboard()
                viewModel.anonymizeText(inputText)
            }
        }
        
        // Copy button
        binding.buttonCopy.setOnClickListener {
            val text = viewModel.anonymizedText.value
            if (!text.isNullOrEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Anonymized Text", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Share button
        binding.buttonShare.setOnClickListener {
            val text = viewModel.anonymizedText.value
            if (!text.isNullOrEmpty()) {
                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, text)
                    type = "text/plain"
                }
                startActivity(Intent.createChooser(shareIntent, "Share anonymized text"))
            }
        }
    }

    private fun setupTextWatcher() {
        binding.editTextInput.doAfterTextChanged {
            updateAnonymizeButtonState()
        }
    }
    
    private fun updateAnonymizeButtonState() {
        val inputText = binding.editTextInput.text.toString()
        val isModelLoaded = viewModel.isModelLoaded.value ?: false
        val isProcessing = viewModel.isProcessing.value ?: false
        
        val isEnabled = inputText.isNotEmpty() && isModelLoaded && !isProcessing
        binding.buttonAnonymize.isEnabled = isEnabled
        
        // Update button appearance
        binding.buttonAnonymize.alpha = if (isEnabled) 1.0f else 0.5f
        
        // Update button text if not processing
        if (!isProcessing) {
            binding.buttonAnonymize.text = "Anonymize Text"
        }
    }
    
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.editTextInput.windowToken, 0)
    }
}

