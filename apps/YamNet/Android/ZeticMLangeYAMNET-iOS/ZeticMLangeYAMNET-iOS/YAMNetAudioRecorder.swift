import AVFoundation
import ZeticMLange
import AVFoundation

class YAMNetAudioRecorder {
    private let audioEngine = AVAudioEngine()
    private let inputNode: AVAudioInputNode
    private let inputFormat: AVAudioFormat
    private var audioData = [Float]()
    private let totalSamples = 48000
    private var converter: AVAudioConverter?
    
    init?() {
        self.inputNode = audioEngine.inputNode
        let hardwareFormat = inputNode.inputFormat(forBus: 0)
        
        guard let desiredFormat = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: 16000, channels: 1, interleaved: false) else {
            return nil
        }
        
        self.converter = AVAudioConverter(from: hardwareFormat, to: desiredFormat)
        self.inputFormat = desiredFormat
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
        
        let sampleRate: Double = 16000
        let bufferSize: AVAudioFrameCount = 1024
        let totalCaptureSamples = Int(sampleRate * 3)
        let overlapSize = Int(sampleRate)

        let inputFormat = inputNode.inputFormat(forBus: 0)
        
        guard let outputFormat = AVAudioFormat(commonFormat: .pcmFormatFloat32, sampleRate: sampleRate, channels: 1, interleaved: false),
              let converter = AVAudioConverter(from: inputFormat, to: outputFormat) else {
            print("Failed to create AVAudioConverter.")
            return
        }
        
        inputNode.installTap(onBus: 0, bufferSize: bufferSize, format: inputFormat) { [weak self] (buffer, time) in
            guard let self = self else { return }
            
            guard let convertedBuffer = self.convertSampleRate(buffer: buffer, converter: converter) else { return }
            let frameLength = Int(convertedBuffer.frameLength)
            
            if let channelData = convertedBuffer.floatChannelData?[0] {
                let samples = UnsafeBufferPointer(start: channelData, count: frameLength)
                self.audioData.append(contentsOf: samples)
            }
            
            if self.audioData.count >= totalCaptureSamples {
                let data = self.audioData.suffix(totalCaptureSamples).withUnsafeBufferPointer { bufferPointer in
                    Data(buffer: bufferPointer)
                }
                
                if data.count == totalCaptureSamples * 4 {
                    body(data)
                } else {
                    print("Data size mismatch: expected \(totalCaptureSamples * 4), got \(data.count)")
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

    private func convertSampleRate(buffer: AVAudioPCMBuffer, converter: AVAudioConverter) -> AVAudioPCMBuffer? {
        let inputFrameLength = buffer.frameLength
        let inputSampleRate = converter.inputFormat.sampleRate
        let outputSampleRate = converter.outputFormat.sampleRate

        let ratio = outputSampleRate / inputSampleRate
        let expectedFrameLength = AVAudioFrameCount(Double(inputFrameLength) * ratio)

        let outputBuffer = AVAudioPCMBuffer(pcmFormat: converter.outputFormat, frameCapacity: expectedFrameLength)!

        var error: NSError? = nil
        let inputBlock: AVAudioConverterInputBlock = { _, outStatus in
            outStatus.pointee = .haveData
            return buffer
        }
        
        converter.convert(to: outputBuffer, error: &error, withInputFrom: inputBlock)
        
        if let error = error {
            print("Error during conversion: \(error)")
            return nil
        }

        outputBuffer.frameLength = expectedFrameLength
        
        return outputBuffer
    }


    func stopCapturing() {
        inputNode.removeTap(onBus: 0)
        audioEngine.stop()
    }
}
class AudioClass {
    let index: Int
    let score: Float
    
    init(index: Int, score: Float) {
        self.index = index
        self.score = score
    }
}
