import SwiftUI
import AVFoundation
import Vision

import ZeticMLange

struct ContentView: View {
    @StateObject private var cameraModel: CameraModel = CameraModel(mlange_model_key: "linematics_yolov8n")
    private let classes = CocoConfig.readClasses()
    
    private func getClassColor(classId: Int32) -> Color {
        let r = Double((Int(classId) + 72) * 1717 % 256) / 255.0
        let g = Double((Int(classId) + 7) * 33 % 126 + 70) / 255.0
        let b = Double((Int(classId) + 47) * 107 % 256) / 255.0
        return Color(red: r, green: g, blue: b)
    }
    
    private func calculateBox(for result: ZeticMLangeYoloV8Result, in geometry: GeometryProxy, _ cameraResolution: CGSize) -> [CGFloat] {
        [
            CGFloat(result.box[0]) * (geometry.size.width / cameraResolution.height),
            CGFloat(result.box[1]) * (geometry.size.height / cameraResolution.width),
            CGFloat(result.box[2]) * (geometry.size.width / cameraResolution.height),
            CGFloat(result.box[3]) * (geometry.size.height / cameraResolution.width)
        ]
    }
    
    var body: some View {
        let cameraResolution = cameraModel.resolution
        GeometryReader { geometry in
            ZStack {
                CameraPreview(session: cameraModel.session)
                    .ignoresSafeArea()
            }
            ZStack {
                    ForEach(Array(zip(cameraModel.detectionResults.indices, cameraModel.detectionResults)), id: \.0) { index, result in
                        let color = getClassColor(classId: result.classId)
                        let confidence = String(format: "%.2f", result.confidence)
                        let label = "\(classes[Int(result.classId)]) \(confidence)"
                        let box = calculateBox(for: result, in: geometry, cameraResolution)
                        
                        DetectionBoxView(
                            result: result,
                            color: color,
                            label: label,
                            box: box
                        )
                    }
                    
            }
//            ZStack {
//                FPSDisplayView(
//                    fps: cameraModel.currentFPS,
//                    modelKey: cameraModel.modelKey
//                )
//            }
        }
        .ignoresSafeArea()
        .onAppear {
            cameraModel.checkCameraPermission()
        }
    }
}
