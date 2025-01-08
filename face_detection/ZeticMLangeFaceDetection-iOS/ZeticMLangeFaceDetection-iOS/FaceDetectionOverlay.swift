import SwiftUI
import ZeticMLange

struct FaceDetectionOverlay: View {
    let results: [FaceDetectionResult]
    
    var body: some View {
        Canvas { context, size in
            let sizeWidth = Float(size.width)
            let sizeHeight = Float(size.height)
            let ratio = ((sizeWidth + sizeHeight) / 2) / sizeHeight
            let resizeFactor: Float = 1.3
            let verticalOffset: Float = 0.9
            
            results.forEach { result in
                let width = ((result.bbox.xmax - result.bbox.xmin) / ratio) * resizeFactor
                let height = ((result.bbox.ymax - result.bbox.ymin) * ratio) * resizeFactor
                let x = result.bbox.xmin - ((width - (result.bbox.xmax - result.bbox.xmin)) / 2)
                // multiply vertical offset to adjust box to fit user's face
                let y = (result.bbox.ymin - ((height - (result.bbox.ymax - result.bbox.ymin)) / 2)) * verticalOffset
                let path = Path(roundedRect: CGRect(x: Int(x * sizeWidth), y: Int(y * sizeHeight), width: Int(width * sizeWidth), height: Int(height * sizeHeight)), cornerSize: CGSize(width: 1, height: 1))
                
                context.stroke(path, with: .color(.green), lineWidth: 2)
                context.draw(Text("Conf. \(result.confidence)").foregroundColor(.green), in: CGRect(x: Int(x * sizeWidth), y: Int(y * sizeHeight) - 20, width: Int(width * sizeWidth), height: 5))
            }
        }
    }
}
