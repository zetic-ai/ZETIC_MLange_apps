import Foundation
import ZeticMLange

class WhisperEncoder {
    private var model: ZeticMLangeModel
    
    init(_ modelKey: String) {
        model = ZeticMLangeModel(modelKey, Target.ZETIC_MLANGE_TARGET_COREML_FP32)!
    }
    
    func process(_ audio: [Float32]) -> Data {
        do {
            let data = Data(bytes: audio, count: audio.count * MemoryLayout<Float32>.size)
            try model.run([data])
        } catch {
            
        }
        return model.getOutputDataArray()[0]
    }
    
}
