import SwiftUI
import ZeticMLange

struct FaceEmotionRecognitionOverlay: View {
    let result: FaceEmotionRecognitionOutput
    let roi: Box
    
    var body: some View {
        VStack {
            Spacer()
            HStack(alignment: .bottom) {
                Text("Emotion : \(result.result.emotion)\nConfidence : \(result.result.confidence)")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(.white)
                    .padding(8)
                    .background(Color.black.opacity(0.6))
                    .cornerRadius(8)
                    .padding()
                Spacer()
            }
        }
    }
}
