import SwiftUI
import AVFoundation
import Vision

import ZeticMLange

struct ContentView: View {
    let whisperEncoder: WhisperEncoder = WhisperEncoder("whisper-tiny-encoder")
    let whisperDecoder: WhisperDecoder = WhisperDecoder("whisper-tiny-decoder")
    let whisperProcessor: ZeticMLangeFeatureWhisper = ZeticMLangeFeatureWhisper(Bundle.main.path(forResource: "vocab", ofType: "json")!)
    
    var audioRecorder: AudioRecorder = AudioRecorder()
    
    @State private var audioText = "Ready to listen."
    
    var body: some View {
        ZStack {
            Color.clear
            
            VStack {
                Spacer()
                
                Text(audioText).font(.system(size: 22))
                
                Spacer()
                
                Button(action: {
                    audioText = "listening..."
                    audioRecorder.startRecording(completion: { result in
                        audioText = "processing..."
                        let features = whisperProcessor.process(result)
                        let outputs = whisperEncoder.process(features)
                        let generatedIds = whisperDecoder.process(outputs)
                        let text = whisperProcessor.decodeToken(generatedIds, true)
                        audioText = text
                    })
                }, label: {
                    Text("Record").padding()
                }).padding(.bottom, 50)
            }
        }
        .ignoresSafeArea()
        .onAppear {
        }
    }
}
