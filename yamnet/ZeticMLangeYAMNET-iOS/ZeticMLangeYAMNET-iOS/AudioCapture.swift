import AVFoundation
import ZeticMLange
import AVFoundation

class AudioCapture {
    private let audioEngine = AVAudioEngine()
    private let inputNode: AVAudioInputNode
    private let inputFormat: AVAudioFormat
    private var audioData = [Float]()
    private let totalSamples = 48000
    
    init?() {
        self.inputNode = audioEngine.inputNode
        guard let format = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: 16000, channels: 1, interleaved: false) else {
            return nil
        }
        self.inputFormat = format
        setupAudioSession()
    }
    
    private func setupAudioSession() {
        let audioSession = AVAudioSession.sharedInstance()
        do {
            try audioSession.setCategory(.playAndRecord, mode: .default)
            try audioSession.setPreferredSampleRate(16000)
            try audioSession.setActive(true)
            
        } catch {
            print("Failed to set up audio session: \(error)")
        }
    }
    
    func startCapturing(_ body: @escaping (Data) -> Void) {
        audioData.removeAll()
        let bufferSize: AVAudioFrameCount = 16000
        let overlapSize = 16000
        let totalCaptureSamples = 48000
        
        inputNode.installTap(onBus: 0, bufferSize: bufferSize, format: inputFormat) { [weak self] (buffer, time) in
            guard let self = self else { return }
            let frameLength = Int(buffer.frameLength)
            if let channelData = buffer.floatChannelData?[0] {
                let samples = UnsafeBufferPointer(start: channelData, count: frameLength)
                self.audioData.append(contentsOf: samples)
            }
            
            if self.audioData.count >= totalCaptureSamples {
                let data = self.audioData.prefix(totalCaptureSamples).withUnsafeBufferPointer { bufferPointer in
                    Data(buffer: bufferPointer)
                }
                
                if data.count == totalCaptureSamples * 4 {
                    body(data)
                } else {
                    print("Data size mismatch: expected 192000, got \(data.count)")
                }
                
                self.audioData.removeFirst(overlapSize)
            }
        }
        
        do {
            try audioEngine.start()
        } catch {
            print("Audio engine failed to start: \(error)")
        }
    }
    
    func stopCapturing() {
        inputNode.removeTap(onBus: 0)
        audioEngine.stop()
    }
}
@available(iOS 13.0, *)
class AudioProcessor: ObservableObject {
    
    @Published var scores: Dictionary<Int, Float>?
    
    let audioCapture = AudioCapture()!
    let model = ZeticMLangeModel("YAMNet")!
    
    func startProcessing() {
        audioCapture.startCapturing { audioData in
            self.processAudioData(audioData)
        }
    }
    
    func stopProcessing() {
        audioCapture.stopCapturing()
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
    
    private func processAudioData(_ audioData: Data) {
        do {
            try model.run([audioData])
            let outputs = model.getOutputDataArray().sorted { $0.count < $1.count }
            
            let floats = outputs[0].withUnsafeBytes { ptr -> [[Float]] in
                
                let rowCount = 6
                let columnCount = 521
                let totalCount = rowCount * columnCount
                let expectedByteCount = totalCount * MemoryLayout<Float>.size
                guard ptr.count >= expectedByteCount else {
                    print("Error: Not enough data in outputs[0].")
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
            
            DispatchQueue.main.async {
                self.scores = self.getTopClassIndices(scores: floats, topN: 5)
            }
        } catch {
            
        }
        
    }
    private func getTopClassIndices(scores: [[Float]], topN: Int) -> Dictionary<Int, Float> {
        let numberOfRows = scores.count
        guard numberOfRows > 0 else {
            print("Scores array is empty.")
            return [:]
        }
        
        let numberOfColumns = scores[0].count
        
        for (rowIndex, row) in scores.enumerated() {
            if row.count != numberOfColumns {
                print("Row \(rowIndex) has inconsistent number of columns.")
                return [:]
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
        
        return Dictionary(uniqueKeysWithValues: zip(topClassIndices, topClassScores))
    }
}
