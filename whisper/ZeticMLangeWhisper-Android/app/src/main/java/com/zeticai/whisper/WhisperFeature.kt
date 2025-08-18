package com.zeticai.whisper

import android.content.Context
import com.zeticai.mlange.feature.automaticspeechrecognition.whisper.WhisperWrapper
import java.io.File
import java.io.FileOutputStream

class WhisperFeature(
    context: Context,
) {
    private val encoder by lazy {
        WhisperEncoder(context, ENCODER_MODEL_KEY)
    }

    private val decoder by lazy {
        WhisperDecoder(
            50258,
            50257,
            context,
            DECODER_MODEL_KEY,
        )
    }

    private val whisperWrapper by lazy {
        WhisperWrapper(copyAssetToInternalStorage(context))
    }

    private fun copyAssetToInternalStorage(
        context: Context,
        assetFileName: String = "vocab.json"
    ): String {
        val inputStream = context.assets.open(assetFileName)
        val outFile = File(context.filesDir, assetFileName)
        val outputStream = FileOutputStream(outFile)
        val newFilePath = outFile.absolutePath

        val buffer = ByteArray(1024)
        var read: Int
        while ((inputStream.read(buffer).also { read = it }) != -1) {
            outputStream.write(buffer, 0, read)
        }

        outputStream.flush()
        inputStream.close()
        outputStream.close()

        return newFilePath
    }

    fun run(audio: FloatArray): String {
        val encodedFeatures = whisperWrapper.process(audio)
        val outputs = encoder.process(encodedFeatures)
        val generatedIds = decoder.generateTokens(outputs)
        val text = whisperWrapper.decodeToken(generatedIds.toIntArray(), true)
        return text
    }

    fun close() {
        encoder.close()
        decoder.close()
        whisperWrapper.deinit()
    }

    companion object {
        const val ENCODER_MODEL_KEY: String = "OpenAI/whisper-tiny-encoder"
        const val DECODER_MODEL_KEY: String = "OpenAI/whisper-tiny-decoder"
    }
}




