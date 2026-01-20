import Foundation
import ZeticMLange

class WhisperEncoder {
    private var model: ZeticMLangeModel
    
    public static let modelKey = "OpenAI/whisper-tiny-encoder"
    
    init() {
        model = (try? ZeticMLangeModel(personalKey: "YOUR_MLANGE_KEY", name: "YOUR_PROJECT_NAME", version: 1))!
    }
    
    func process(_ audio: [Float32]) -> Data {
        let data = Data(bytes: audio, count: audio.count * MemoryLayout<Float32>.size)
        let tensor = Tensor(data: data, dataType: BuiltinDataType.int8, shape: [data.count])
        return try! model.run(inputs: [tensor])[0].data
    }
    
}
