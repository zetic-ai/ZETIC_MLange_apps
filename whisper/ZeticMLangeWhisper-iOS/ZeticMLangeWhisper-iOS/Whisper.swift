import Foundation
import ZeticMLange

class Whisper: ObservableObject {
    let encoder: WhisperEncoder = WhisperEncoder("whisper-tiny-encoder")
    let decoder: WhisperDecoder = WhisperDecoder("whisper-tiny-decoder")
    let wrapper: WhisperWrapper = WhisperWrapper(Bundle.main.path(forResource: "vocab", ofType: "json")!)
    
    var audioRecorder: WhisperAudioRecorder = WhisperAudioRecorder()
    var isProcessing: Bool = false
    
    @Published private(set) var result: String = "Ready to listen."
    
    func process(input: WhisperInput) {
        DispatchQueue.global(qos: .userInitiated).async { [self] in
            
            let features = wrapper.process(input.audio)
            let outputs = encoder.process(features)
            let generatedIds = decoder.process(outputs)
            let text = wrapper.decodeToken(generatedIds, true)
            let output = WhisperOutput(text: text)
            
            DispatchQueue.main.async {
                self.result = output.text
                self.isProcessing = false
            }
        }
    }
    
    func startRecording() {
        guard !isProcessing else { return }
        
        isProcessing = true
        
        result = "Recording..."
        audioRecorder.startRecording { audio in
            DispatchQueue.main.async {
                self.result = "Processing..."
            }
            
            self.process(input: WhisperInput(audio: audio))
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
