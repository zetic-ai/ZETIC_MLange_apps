import SwiftUI
import ZeticMLange

struct FaceLandmarkOverlay: View {
    let result: FaceLandmarkOutput
    let roi: Box
    
    var body: some View {
        Canvas { context, size in
            let sizeWidth = Float(size.width)
            let sizeHeight = Float(size.height)
            let focalLength: Float = 500
            let landmarkCount: Int = 468
            
            for i in 0..<result.result.faceLandmark.count.clamped(to: 0...landmarkCount) {
                let landmark = result.result.faceLandmark[i]
                let x = landmark.x
                let y = landmark.y
                let z = landmark.z + focalLength
                
                let x2D = (x / z) * focalLength
                let y2D = (y / z) * focalLength
                
                let path = Path(
                    roundedRect: CGRect(
                        x: Int((x2D * roi.width * sizeWidth) + (roi.xmin * sizeWidth)),
                        y: Int((y2D * roi.height * sizeHeight) + (roi.ymin * sizeHeight)),
                        width: 1,
                        height: 1
                    ),
                    cornerSize: CGSize(width: 1, height: 1)
                )
                
                context.stroke(path, with: .color(.green), lineWidth: 1)
            }
        }
    }
}
