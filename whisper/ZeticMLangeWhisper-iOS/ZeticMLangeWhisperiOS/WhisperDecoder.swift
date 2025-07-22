import Foundation
import ZeticMLange

class WhisperDecoder {
    private let endToken = 50257

    private var model: ZeticMLangeModel

    init(_ modelKey: String) {
        model = (try? ZeticMLangeModel("{INPUT YOUR TOKEN}", modelKey, Target.ZETIC_MLANGE_TARGET_COREML, APType.NPU))!

    }
    
    func loadFloatArray(from fileURL: URL) throws -> [Float] {
        let data = try Data(contentsOf: fileURL)
        let count = data.count / MemoryLayout<Float>.stride
        return data.withUnsafeBytes {
            Array(UnsafeBufferPointer<Float>(
                start: $0.baseAddress!.assumingMemoryBound(to: Float.self),
                count: count
            ))
        }
    }

    func process(_ encoderOutput: Data, _ maxLength: Int = 448, temperature: Float = 1.0) -> [Int32] {
        var decoderTokenIds = Array(repeating: Int32(100), count: maxLength)
        decoderTokenIds[0] = 50258


        var generatedIds: [Int32] = []

        var decoderAttentionMask = [Int32](repeating: 0, count: maxLength)
        decoderAttentionMask[0] = 1
        
        
        for i in 0 ..< maxLength {
            let logits = decodeStep(decoderTokenIds: decoderTokenIds,
                                    encoderOutput: encoderOutput,
                                    decoderAttentionMask: decoderAttentionMask)

            let vocab = logits.count / maxLength

            let scaledLogits: [Float]
            let startIndex = vocab * (i)
            let endIndex = vocab * (i + 1)

            let currentLogits = Array(logits[startIndex ..< endIndex])

            let nextToken: Int
            nextToken = ProbabilityUtils.argmax(currentLogits)
            if nextToken == endToken {
                break
            }

            generatedIds.append(Int32(nextToken))
            
            decoderTokenIds[i+1] = Int32(nextToken)

            decoderAttentionMask[i+1] = 1
            print(generatedIds.map { "\($0)" }.joined(separator: ","))

        }


        return generatedIds
    }

    var decoderTokenIdsData = Data(count: 448 * MemoryLayout<Int32>.size)
    var decoderAttentionMaskData = Data(count: 448 * MemoryLayout<Int32>.size)
    
    private func decodeStep(decoderTokenIds: [Int32], encoderOutput: Data, decoderAttentionMask: [Int32]) -> [Float] {
        return autoreleasepool {
            decoderTokenIdsData.removeAll(keepingCapacity: true)
            decoderTokenIdsData.reserveCapacity(decoderTokenIds.count * MemoryLayout<Int32>.size)
            for token in decoderTokenIds {
                var le = token.littleEndian
                withUnsafeBytes(of: &le) { decoderTokenIdsData.append(contentsOf: $0) }
            }

            decoderAttentionMaskData.removeAll(keepingCapacity: true)
            decoderAttentionMaskData.reserveCapacity(decoderAttentionMask.count * MemoryLayout<Int32>.size)
            for mask in decoderAttentionMask {
                var le = mask.littleEndian
                withUnsafeBytes(of: &le) { decoderAttentionMaskData.append(contentsOf: $0) }
            }
            
            try? model.run([
                decoderTokenIdsData,
                encoderOutput,
                decoderAttentionMaskData,
                
            ])
            var outputData: Data? = nil
            autoreleasepool {
                
                outputData = model.getOutputDataArray()[0]
            }
                        
            var floatArray: [Float] = (outputData?.withUnsafeBytes { rawBuffer in
                let ptr = rawBuffer.bindMemory(to: UInt32.self)
                return ptr.map { word in
                    // ensure little‑endian order:
                    let le = UInt32(littleEndian: word)
                    // reinterpret the bitPattern as Float
                    return Float(bitPattern: le)
                }
            })!
            
            
            
            return floatArray
        }
    }
}
