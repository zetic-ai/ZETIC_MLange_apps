import Foundation
import ZeticMLange
import ext

class Whisper: AsyncFeature<WhisperInput, WhisperOutput> {
    let encoder: WhisperEncoder = WhisperEncoder()
    let decoder: WhisperDecoder = WhisperDecoder()
    let wrapper: WhisperWrapper = WhisperWrapper(Bundle.main.path(forResource: "vocab", ofType: "json")!)
    
    var audioRecorder: WhisperAudioRecorder = WhisperAudioRecorder()
    var isProcessing: Bool = false
    
    @Published private(set) var result: String = "Ready to listen."
    
    override func process(input: WhisperInput) -> WhisperOutput {
        let features = wrapper.process(input.audio)
        let outputs = encoder.process(features)
        let generatedIds = decoder.process(outputs)
        let text = wrapper.decodeToken(generatedIds, true)
        return WhisperOutput(text: text)
    }
    
    override func handleOutput(_ output: WhisperOutput) {
        self.result = output.text
    }
    
    func startRecording() {
        guard !isProcessing else { return }
        
        isProcessing = true
        
        result = "Recording..."
        audioRecorder.startRecording { audio in
            DispatchQueue.main.async {
                self.result = "Processing..."
            }
            
            self.run(with: WhisperInput(audio: audio))
            self.isProcessing = false
        }
    }
    
    func stopRecording() {
        audioRecorder.stopRecording()
    }
}

struct WhisperInput: AsyncFeatureInput {
    let audio: [Float]
}

struct WhisperOutput: AsyncFeatureOutput {
    let text: String
}
