//package com.zeticai.zetic_mlange_android
//
//import android.content.Context
//import android.os.Bundle
//import android.widget.Button
//import android.widget.EditText
//import android.widget.TextView
//import androidx.appcompat.app.AppCompatActivity
//import androidx.lifecycle.lifecycleScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import java.io.File
//import java.io.FileOutputStream
//import java.nio.ByteBuffer
//import java.nio.ByteOrder
//import android.media.AudioAttributes
//import android.media.AudioFormat
//import android.media.AudioTrack
//
//import com.zeticai.mlange.feature.texttospeech.vits.VitsWrapper
//import com.zeticai.mlange.feature.automaticspeechrecognition.whisper.WhisperWrapper
//import android.util.Log
//
//class MainActivity : AppCompatActivity() {
//    private val vitsModel by lazy {
////        VitsFeature(this, "c07b87d3ad2a4099af965cd8d8d99688")
////        VitsFeature(this, "7b2a68cf651f487888994fc1552ba49b")
//        VitsFeature(this, "d2e86ac110074d1abd07b20eb754f744")
////        VitsFeature(this, "b9f5d74e6f644288a32c50174ded828e")
//    }
//
//    private val vitsTokenizer by lazy {
//        VitsWrapper(copyAssetToInternalStorage(this, "vocab_whisper.json"))
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//
//        val inputText = findViewById<EditText>(R.id.inputText)
//        val submitButton = findViewById<Button>(R.id.submitButton)
//        val outputText = findViewById<TextView>(R.id.outputText)
//
////        val id_path = copyAssetToInternalStorage(this, "input_ids.bin")
////        val id_bytes = File(id_path).readBytes()
////        val id_buffer = ByteBuffer.wrap(id_bytes)
////        id_buffer.order(ByteOrder.LITTLE_ENDIAN)
////        id_buffer.put(id_bytes)
//
////        val attention_mask_path = copyAssetToInternalStorage(this, "attention_mask.bin")
////        val attention_mask_bytes = File(attention_mask_path).readBytes()
////        val attention_mask_buffer = ByteBuffer.wrap(attention_mask_bytes)
////        attention_mask_buffer.order(ByteOrder.LITTLE_ENDIAN)
////        attention_mask_buffer.put(attention_mask_bytes)
//
//
//        submitButton.setOnClickListener {
//            val text = inputText.text.toString()
//
//            lifecycleScope.launch {
//                outputText.text = "처리 중..."
//                try {
//                    val (ids, attention_mask) = vitsTokenizer.convertTextToIds(text, 101);
//                    val org_ids_size = ids.size
//                    Log.d("pilmo", org_ids_size.toString())
//
//                    val ids_buffer = intArrayToByteBuffer(ids)
//                    val attention_mask_buffer = intArrayToByteBuffer(attention_mask)
//
//                    val data = withContext(Dispatchers.IO) {
//                        vitsModel.process(ids_buffer, attention_mask_buffer)
//                    }
//
//                    val result = byteBufferToFloatArray(data)
//                    val predictedLength = result.last().toInt()
//
//
//                    val sliced = data.duplicate()
//                    sliced.position(0)
//                    sliced.limit(predictedLength * 256 * 4)
//                    val real_data = sliced.slice()
//
////                  여기서 재생시키도록..
//                    playFloatPcm(real_data)
//
//
//
//
//                    outputText.text = "결과: $data"
//                } catch (e: Exception) {
//                    outputText.text = "에러: ${e.message}"
//                    e.printStackTrace()
//                }
//            }
//        }
//    }
//
//    private fun copyAssetToInternalStorage(context: Context, assetFileName: String): String {
//        val inputStream = context.assets.open(assetFileName)
//        val outFile = File(context.filesDir, assetFileName)
//        val outputStream = FileOutputStream(outFile)
//        val newFilePath = outFile.absolutePath
//
//        val buffer = ByteArray(1024)
//        var read: Int
//        while ((inputStream.read(buffer).also { read = it }) != -1) {
//            outputStream.write(buffer, 0, read)
//        }
//
//        outputStream.flush()
//        inputStream.close()
//        outputStream.close()
//
//        return newFilePath
//    }
//
//    fun playFloatPcm(
//        byteBuffer: ByteBuffer,
//        sampleRate: Int = 16000 // samplin rate
//    ) {
//        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
//        val floatBuffer = byteBuffer.asFloatBuffer()
//
//        val audioFormat = AudioFormat.Builder()
//            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
//            .setSampleRate(sampleRate)
//            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
//            .build()
//
//        val attrs = AudioAttributes.Builder()
//            .setUsage(AudioAttributes.USAGE_MEDIA)
//            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
//            .build()
//
//        val track = AudioTrack.Builder()
//            .setAudioAttributes(attrs)
//            .setAudioFormat(audioFormat)
//            .setBufferSizeInBytes(byteBuffer.capacity())
//            .setTransferMode(AudioTrack.MODE_STATIC)
//            .build()
//
//        track.write(byteBuffer, byteBuffer.capacity(), AudioTrack.WRITE_BLOCKING)
//        track.play()
//    }
//
//    fun intArrayToByteBuffer(intArray: IntArray): ByteBuffer {
//        val longArray = LongArray(intArray.size) { intArray[it].toLong() }
//
//        val byteBuffer = ByteBuffer.allocate(longArray.size * java.lang.Long.BYTES)
//        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
//
//        for (value in longArray) {
//            byteBuffer.putLong(value)
//        }
//
//        byteBuffer.flip()
//        return byteBuffer
//    }
//
//    fun byteBufferToFloatArray(buffer: ByteBuffer): FloatArray {
//        buffer.order(ByteOrder.nativeOrder())
//
//        val floatCount = buffer.remaining() / 4
//
//        val floatArray = FloatArray(floatCount)
//        buffer.asFloatBuffer().get(floatArray)
//
//        return floatArray
//    }
//
//}
