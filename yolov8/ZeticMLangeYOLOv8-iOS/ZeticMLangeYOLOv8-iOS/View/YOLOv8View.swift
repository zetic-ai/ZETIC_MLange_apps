import SwiftUI
import AVFoundation
import Vision

import ZeticMLange

struct YOLOv8View: View {
    @StateObject private var yolov8: YOLOv8 = YOLOv8(key: "yolo-v8n-test")
    @StateObject private var cameraModel: CameraModel = CameraModel(.back, .frame)
    private let classes = CocoConfig.readClasses()
    
    var body: some View {
        let cameraResolution = cameraModel.resolution
        GeometryReader { geometry in
            ZStack {
                CameraPreview(session: cameraModel.session)
                    .ignoresSafeArea()
            }
            ZStack {
                if cameraModel.frame != nil && cameraModel.timestamp != nil {
                    let _ = yolov8.run(cameraModel.frame!, cameraModel.timestamp!)
                }
                ForEach(Array(zip(yolov8.detectionResults.indices, yolov8.detectionResults)), id: \.0) { index, result in
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
        }
        .ignoresSafeArea()
        .onAppear {
            cameraModel.checkCameraPermission()
        }.onDisappear {
            yolov8.waitForPendingOperations {
                yolov8.close()
                cameraModel.close()
            }
        }
    }
    
    private func getClassColor(classId: Int32) -> Color {
        let r = Double((Int(classId) + 72) * 1717 % 256) / 255.0
        let g = Double((Int(classId) + 7) * 33 % 126 + 70) / 255.0
        let b = Double((Int(classId) + 47) * 107 % 256) / 255.0
        return Color(red: r, green: g, blue: b)
    }
    
    private func calculateBox(for result: YOLOv8Result, in geometry: GeometryProxy, _ cameraResolution: CGSize) -> [CGFloat] {
        [
            CGFloat(result.box[0]) * (geometry.size.width / cameraResolution.height),
            CGFloat(result.box[1]) * (geometry.size.height / cameraResolution.width),
            CGFloat(result.box[2]) * (geometry.size.width / cameraResolution.height),
            CGFloat(result.box[3]) * (geometry.size.height / cameraResolution.width)
        ]
    }
    
}
