import Foundation
import ZeticMLange

class Whisper: AsyncFeature<WhisperInput, WhisperOutput> {
    
    let encoder: WhisperEncoder = WhisperEncoder("whisper-tiny-encoder")
    let decoder: WhisperDecoder = WhisperDecoder("whisper-tiny-decoder")
    let wrapper: WhisperWrapper = WhisperWrapper(Bundle.main.path(forResource: "vocab", ofType: "json")!)
    
    var audioRecorder: WhisperAudioRecorder = WhisperAudioRecorder()
    
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
        result = "Recording..."
        audioRecorder.startRecording { audio in
            DispatchQueue.main.async {
                self.result = "Processing..."
            }
            
            self.run(with: WhisperInput(audio: audio))
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
