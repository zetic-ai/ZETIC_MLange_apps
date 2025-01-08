import SwiftUI
import AVFoundation
import Vision

import ZeticMLange

struct WhisperView: View {
    @StateObject private var whisper = Whisper()
    
    var body: some View {
        ZStack {
            Color.clear
            
            VStack {
                Spacer()
                
                Text(whisper.result).font(.system(size: 22))
                
                Spacer()
                
                Button(action: {
                    whisper.startRecording()
                }, label: {
                    Text("Record").padding()
                }).padding(.bottom, 50)
            }
        }
        .ignoresSafeArea()
        .onAppear {
        }
        .onDisappear {
            whisper.stopRecording()
        }
    }
}
