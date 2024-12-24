import AVFoundation
import ZeticMLange
import AVFoundation

class YAMNet: AsyncFeature<YAMNetInput, YAMNetOutput> {
    @Published var scores: Array<AudioClass>?
    
    let audioRecorder = YAMNetAudioRecorder()!
    let model = ZeticMLangeModel("YAMNet", Target.ZETIC_MLANGE_TARGET_COREML_FP32)!
    
    func startProcessing() {
        audioRecorder.startCapturing { audio in
            self.run(with: YAMNetInput(audio: audio))
        }
    }
    
    func stopProcessing() {
        audioRecorder.stopCapturing()
    }
    override func process(input: YAMNetInput) -> YAMNetOutput {
        do {
            try model.run([input.audio])
            let outputs = model.getOutputDataArray()
            
            let floats = outputs[2].withUnsafeBytes { ptr -> [[Float]] in
                
                let rowCount = 6
                let columnCount = 521
                let totalCount = rowCount * columnCount
                let expectedByteCount = totalCount * MemoryLayout<Float>.size
                guard ptr.count == expectedByteCount else {
                    print("Error: Data Size Not Matching. Input Data : \(ptr.count), ExpectedByteCount : \(expectedByteCount).")
                    return []
                }
                let baseAddress = ptr.baseAddress!.assumingMemoryBound(to: Float.self)
                var result = [[Float]]()
                
                for i in 0..<rowCount {
                    let startIndex = i * columnCount + (i * rowCount + i)
                    let rowPointer = baseAddress.advanced(by: startIndex)
                    let row = Array(UnsafeBufferPointer(start: rowPointer, count: columnCount))
                    result.append(row)
                }
                return result
            }
            
            return YAMNetOutput(audioClasses: self.getTopClassIndices(scores: floats, topN: 5))
        }
        catch {
            return YAMNetOutput(audioClasses: [])
        }
    }
    
    override func handleOutput(_ output: YAMNetOutput) {
        self.scores = output.audioClasses
    }
    
    private func getTopClassIndices(scores: [[Float]], topN: Int) -> Array<AudioClass> {
        let numberOfRows = scores.count
        guard numberOfRows > 0 else {
            print("Scores array is empty.")
            return []
        }
        
        let numberOfColumns = scores[0].count
        
        for (rowIndex, row) in scores.enumerated() {
            if row.count != numberOfColumns {
                print("Row \(rowIndex) has inconsistent number of columns.")
                return []
            }
        }
        
        var meanScores = [Float](repeating: 0.0, count: numberOfColumns)
        
        for row in scores {
            for (index, value) in row.enumerated() {
                meanScores[index] += value
            }
        }
        
        let rowCount = Float(numberOfRows)
        for index in 0..<meanScores.count {
            meanScores[index] /= rowCount
        }
        
        let indices = Array(0..<meanScores.count)
        let sortedIndices = indices.sorted { meanScores[$0] > meanScores[$1] }
        let sortedScores = meanScores.sorted { $0 > $1 }
        let topClassIndices = Array(sortedIndices.prefix(topN))
        let topClassScores = Array(sortedScores.prefix(topN))
        
        return Array(zip(topClassIndices, topClassScores).map {
            AudioClass(index: $0, score: $1)
        })
    }
    
    private func readBinaryDataFromBundle(filename: String, fileExtension: String) -> Data? {
        if let fileURL = Bundle.main.url(forResource: filename, withExtension: fileExtension) {
            do {
                let data = try Data(contentsOf: fileURL)
                if data.count == 192000 {
                    print("Successfully read data of size: \(data.count) bytes")
                    return data
                } else {
                    print("Data size mismatch. Expected 192,000 bytes, got \(data.count) bytes")
                    return nil
                }
            } catch {
                print("Error reading file: \(error)")
                return nil
            }
        } else {
            print("File not found in bundle.")
            return nil
        }
    }
    
    private func printData(_ data: Data) {
        let numberOfFloats = data.count / MemoryLayout<Float>.size
        print(numberOfFloats)
        
        let floatArray: [Float] = data.withUnsafeBytes { ptr in
            let floatPtr = ptr.bindMemory(to: Float.self)
            return Array(UnsafeBufferPointer(start: floatPtr.baseAddress, count: numberOfFloats))
        }
        
        let numberOfValuesToPrint = 9999999
        for i in 0..<min(numberOfValuesToPrint, floatArray.count) {
            print("Value \(i): \(floatArray[i])")
        }
    }
    
}

struct YAMNetInput: AsyncFeatureInput {
    let audio: Data
}

struct YAMNetOutput: AsyncFeatureOutput {
    let audioClasses: Array<AudioClass>
}
