//package com.zeticai.zetic_mlange_android
//
//import android.content.Context
//import com.zeticai.mlange.core.model.APType
//import com.zeticai.mlange.core.model.Target
//import com.zeticai.mlange.core.model.ZeticMLangeModel
//import java.nio.ByteBuffer
//
//class VitsFeature(
//    context: Context,
//    modelKey: String,
//    private val model: ZeticMLangeModel = ZeticMLangeModel(
//        context,
//        "ztp_68b79702a2524522a4ac799dedaf7854",
//        modelKey,
//        Target.QNN,
//        APType.CPU
//    )
//) {
//    fun process(input_ids: ByteBuffer, input_attention_mask: ByteBuffer): ByteBuffer {
//
//
//        model.run(arrayOf(input_ids, input_attention_mask))
//
//        return model.outputBuffers[0]
//    }
//
//    fun close() {
//        model.deinit()
//    }
//}