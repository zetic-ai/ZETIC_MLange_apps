import AVFoundation

class AudioRecorder {
    private var audioEngine: AVAudioEngine!
    private var inputNode: AVAudioInputNode!
    private var recordingBuffer: AVAudioPCMBuffer?
    private let sampleRate: Double = 16000
    private let recordingLength: TimeInterval = 3.0
    private var recordingStartTime: Date?
    
    init() {
        setupAudioEngine()
    }
    
    private func setupAudioEngine() {
        audioEngine = AVAudioEngine()
        inputNode = audioEngine.inputNode
        
        // Configure the audio session
        do {
            let audioSession = AVAudioSession.sharedInstance()
            try audioSession.setCategory(.record, mode: .measurement)
            
            // Get the native hardware format
            let hardwareFormat = inputNode.outputFormat(forBus: 0)
            print("Hardware format: \(hardwareFormat.sampleRate)Hz")
            
            // Create our target 16kHz format
            let targetFormat = AVAudioFormat(
                commonFormat: .pcmFormatFloat32,
                sampleRate: sampleRate,
                channels: 1,
                interleaved: false
            )!
            
            let numberOfFrames = AVAudioFrameCount(sampleRate * recordingLength)
            print("Setting up buffer with capacity: \(numberOfFrames) frames")
            
            recordingBuffer = AVAudioPCMBuffer(
                pcmFormat: targetFormat,
                frameCapacity: numberOfFrames
            )
            recordingBuffer?.frameLength = 0
            
        } catch {
            print("Error setting up audio session: \(error)")
        }
    }
    
    func startRecording(completion: @escaping ([Float]) -> Void) {
        AVAudioSession.sharedInstance().requestRecordPermission { [weak self] allowed in
            guard let self = self, allowed else { return }
            
            do {
                try AVAudioSession.sharedInstance().setActive(true)
                
                self.recordingBuffer?.frameLength = 0
                self.recordingStartTime = Date()
                
                // Use native hardware format for the tap
                let hardwareFormat = self.inputNode.outputFormat(forBus: 0)
                
                // Create 16kHz format for conversion
                let destinationFormat = AVAudioFormat(
                    commonFormat: .pcmFormatFloat32,
                    sampleRate: self.sampleRate,
                    channels: 1,
                    interleaved: false
                )!
                
                let converter = AVAudioConverter(from: hardwareFormat,
                                               to: destinationFormat)!
                
                print("Input format: \(hardwareFormat.sampleRate)Hz")
                print("Target format: \(destinationFormat.sampleRate)Hz")
                
                // Install tap with hardware format
                self.inputNode.installTap(
                    onBus: 0,
                    bufferSize: 1024,
                    format: hardwareFormat  // Use hardware format here
                ) { [weak self] buffer, time in
                    guard let self = self,
                          let recordingBuffer = self.recordingBuffer,
                          let startTime = self.recordingStartTime else { return }
                    
                    let currentDuration = Date().timeIntervalSince(startTime)
                    
                    // Calculate conversion ratio for buffer sizing
                    let ratio = destinationFormat.sampleRate / hardwareFormat.sampleRate
                    let convertedFrameCount = AVAudioFrameCount(Double(buffer.frameLength) * ratio)
                    
                    let convertedBuffer = AVAudioPCMBuffer(pcmFormat: destinationFormat,
                                                         frameCapacity: convertedFrameCount)!
                    
                    var error: NSError?
                    let inputBlock: AVAudioConverterInputBlock = { inNumPackets, outStatus in
                        outStatus.pointee = .haveData
                        return buffer
                    }
                    
                    converter.convert(to: convertedBuffer,
                                    error: &error,
                                    withInputFrom: inputBlock)
                    
                    if let error = error {
                        print("Conversion error: \(error)")
                        return
                    }
                    
                    let remainingFrames = recordingBuffer.frameCapacity - recordingBuffer.frameLength
                    let framesToCopy = min(convertedBuffer.frameLength, remainingFrames)
                    
                    if let bufferPointer = convertedBuffer.floatChannelData?[0],
                       let recordingBufferPointer = recordingBuffer.floatChannelData?[0] {
                        memcpy(
                            recordingBufferPointer.advanced(by: Int(recordingBuffer.frameLength)),
                            bufferPointer,
                            Int(framesToCopy) * MemoryLayout<Float32>.size
                        )
                    }
                    
                    recordingBuffer.frameLength += framesToCopy
                    
                    if currentDuration >= self.recordingLength {
                        self.stopRecording()
                        
                        let channelData = recordingBuffer.floatChannelData?[0]
                        let floatArray = Array(UnsafeBufferPointer(start: channelData,
                                                                 count: Int(recordingBuffer.frameCapacity)))
                        completion(floatArray)
                    }
                }
                
                try self.audioEngine.start()
                
            } catch {
                print("Recording error: \(error)")
            }
        }
    }
    
    func stopRecording() {
        inputNode.removeTap(onBus: 0)
        audioEngine.stop()
        try? AVAudioSession.sharedInstance().setActive(false)
    }
}
