import Foundation
import ZeticMLange

class WhisperDecoder {
    private let endToken = 50257
    
    private var model: ZeticMLangeModel
    
    init(_ modelKey: String) {
        model = ZeticMLangeModel(modelKey)!
    }
    
    func process(_ encoderOutput: Data, _ maxLength: Int = 80, temperature: Float = 1.0) -> [Int32] {
        var decoderInputIds = Array(repeating: Int32(0), count: maxLength)
        decoderInputIds[0] = 50258
        decoderInputIds[1] = 50264
        decoderInputIds[2] = 50359
        
        var generatedIds: [Int32] = []
        
        for i in 3..<maxLength {
            let logits = decodeStep(decoderInputIds, encoderOutput)
            
            let logitsSize = logits.count / maxLength
            
            let scaledLogits: [Float]
            if temperature > 0 {
                scaledLogits = logits.map { $0 / temperature }
            } else {
                scaledLogits = logits
            }
            
            let startIndex = logitsSize * (i - 1)
            let endIndex = logitsSize * i
            let currentLogits = Array(scaledLogits[startIndex..<endIndex])
            
            let probs = ProbabilityUtils.softmax(currentLogits)
            
            let nextToken: Int
            if temperature > 0 {
                nextToken = ProbabilityUtils.sampleFromDistribution(probs)
            } else {
                nextToken = ProbabilityUtils.argmax(probs)
            }
            
            if nextToken == endToken {
                break
            }
            
            generatedIds.append(Int32(nextToken))
            
            decoderInputIds[i] = Int32(nextToken)
        }
        
        print(generatedIds.map { "\($0)"}.joined(separator: ","))
        
        return generatedIds
    }
    
    private func decodeStep(_ decoderInputIds: [Int32], _ encoderOutput: Data) -> [Float] {
        do {
            try model.run([
                Data(bytes: decoderInputIds, count: 80 * MemoryLayout<Int32>.size),
                encoderOutput
            ])
        } catch {
            
        }
        
        var array = model.getOutputDataArray()[0].withUnsafeBytes {
            Array($0.bindMemory(to: Float.self))
        }
        let gap: Int = Int((ceilf(51865 / 32) * 32) - 51865)
        for i in 1..<80 {
            array.removeSubrange(51865 * i..<51865 * i + gap)
            array = array + (Array(repeating: Float(0), count: gap))
        }
        return array
    }
}
