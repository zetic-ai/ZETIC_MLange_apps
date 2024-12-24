import SwiftUI
import ZeticMLange

struct FaceEmotionRecognitionOverlay: View {
    let result: FaceEmotionRecognitionOutput
    let roi: Box
    
    var body: some View {
        Canvas { context, size in
            let sizeWidth = Float(size.width)
            let sizeHeight = Float(size.height)
            
            context.draw(
                Text("Emotion : \(result.result.emotion) Conf. : \(result.result.confidence)").foregroundColor(.green),
                in: CGRect(
                    x: Int(roi.xmin * sizeWidth),
                    y: Int(roi.ymin * sizeHeight) - 40,
                    width: Int(roi.width * sizeWidth),
                    height: 5
                )
            )
        }
    }
}
