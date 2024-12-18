import SwiftUI
import AVFoundation
import Vision

import ZeticMLange

@main
struct ZeticMLangeFaceEmotionRecognition_iOSApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
struct ContentView: View {
    @StateObject private var cameraController = CameraController()
    private let faceDetection = FaceDetection("face_detection_short_range")
    private let faceLandmark = FaceLandmark("face_landmark")
    private let faceEmotionRecognition = FaceEmotionRecognition("face_emotion_recognition")
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                if let processedImage = cameraController.processedImage {
                    Image(uiImage: processedImage)
                        .resizable()
                        .scaledToFill()
                        .frame(width: geometry.size.width, height: geometry.size.height)
                        .clipped()
                    Canvas { context, size in
                        let sizeWidth = Float(size.width)
                        let sizeHeight = Float(size.height)
                        let ratio = ((sizeWidth + sizeHeight) / 2) / sizeHeight
                        let resizeFactor: Float = 1.4
                        let faceDetectionResults = faceDetection.run(processedImage).map { result in
                            let width = ((result.bbox.xmax - result.bbox.xmin) / ratio) * resizeFactor
                            let height = ((result.bbox.ymax - result.bbox.ymin) * ratio) * resizeFactor
                            let x = result.bbox.xmin - ((width - (result.bbox.xmax - result.bbox.xmin)) / 2)
                            let y = (result.bbox.ymin - ((height - (result.bbox.ymax - result.bbox.ymin)) / 2)) * 0.9
                            let path = Path(roundedRect: CGRect(x: Int(x * sizeWidth), y: Int(y * sizeHeight), width: Int(width * sizeWidth), height: Int(height * sizeHeight)), cornerSize: CGSize(width: 1, height: 1))
                            
                            context.stroke(path, with: .color(.green), lineWidth: 2)
                            context.draw(Text("Conf. \(result.confidence)").foregroundColor(.green), in: CGRect(x: Int(x * sizeWidth), y: Int(y * sizeHeight) - 20, width: Int(width * sizeWidth), height: 5))
                            
                            return Box(xmin: x.clamped(to: 0...1), ymin: y.clamped(to: 0...1), xmax: (x + width).clamped(to: 0...1), ymax: (y + height).clamped(to: 0...1))
                        }
                        if faceDetectionResults.isEmpty {
                            return
                        }
                        let boxWidth = (faceDetectionResults[0].xmax - faceDetectionResults[0].xmin)
                        let boxHeight = (faceDetectionResults[0].ymax - faceDetectionResults[0].ymin)
//                        let faceLandmarkResults = faceLandmark.run(processedImage, faceDetectionResults[0])
//                        let focalLength: Float = 500
//                        if faceLandmarkResults != nil {
//                            for i in 0..<468 {
//                                let x = faceLandmarkResults!.faceLandmark[i].x
//                                let y = faceLandmarkResults!.faceLandmark[i].y
//                                let z = faceLandmarkResults!.faceLandmark[i].z + focalLength
//                                
//                                let x2D = (x / z) * focalLength
//                                let y2D = (y / z) * focalLength
//                                
//                                let path = Path(
//                                    roundedRect: CGRect(x: Int((x2D * boxWidth * sizeWidth) + (faceDetectionResults[0].xmin * sizeWidth)), y: Int((y2D * boxHeight * sizeHeight) + (faceDetectionResults[0].ymin * sizeHeight)), width: 1, height: 1), cornerSize: CGSize(width: 1, height: 1))
//                                
//                                context.stroke(path, with: .color(.green), lineWidth: 1)
//                            }
//                        }
                        
                        let faceEmotionRecognitionResult = faceEmotionRecognition.run(processedImage, faceDetectionResults[0])
                        if faceEmotionRecognitionResult != nil {
                            context.draw(Text("Emotion : \(faceEmotionRecognitionResult!.emotion) Conf. : \(faceEmotionRecognitionResult!.confidence)").foregroundColor(.green), in: CGRect(x: Int(faceDetectionResults[0].xmin * sizeWidth), y: Int(faceDetectionResults[0].ymin * sizeHeight) - 40, width: Int(boxWidth * sizeWidth), height: 5))
                        }
                    }.frame(width: geometry.size.width, height: geometry.size.height)
                        .clipped()
                } else {
                    Color.black
                }
            }
        }
        .ignoresSafeArea()
        .onAppear {
            cameraController.checkCameraPermission()
        }
    }
}

extension Comparable {
    func clamped(to limits: ClosedRange<Self>) -> Self {
        return min(max(self, limits.lowerBound), limits.upperBound)
    }
}
