import SwiftUI
import AVFoundation
import Vision

import ZeticMLange

struct YOLOv8View: View {
    @StateObject private var yolov8: YOLOv8 = YOLOv8(key: "yolo-v11n-test")
    @StateObject private var cameraModel: CameraModel = CameraModel(.back, .frame)
    private let classes = CocoConfig.readClasses()
    
    @State private var aspectRatio: CGFloat = 16.0 / 9.0 // Changed when resolution set
    
    var body: some View {
        let cameraResolution = cameraModel.resolution
        GeometryReader { geometry in
            ZStack {
                Color.black.edgesIgnoringSafeArea(.all)
                
                let width = geometry.size.width
                let height = geometry.size.height
                let targetWidth = width
                let targetHeight = targetWidth * aspectRatio
                
                CameraPreview(session: cameraModel.session)
                    .customFrame(targetWidth: targetWidth, targetHeight: targetHeight,
                                 geometryWidth: width, geometryHeight: height)
                
            }
            ZStack {
                if cameraModel.frame != nil && cameraModel.timestamp != nil {
                    let _ = yolov8.process(input: YOLOv8Input(frame: cameraModel.frame!, timestamp: cameraModel.timestamp!))
                }
                ForEach(Array(zip(yolov8.detectionResults.indices, yolov8.detectionResults)), id: \.0) { index, result in
                    let color = getClassColor(classId: result.classId)
                    let confidence = String(format: "%.2f", result.confidence)
                    let label = "\(classes[Int(result.classId)]) \(confidence)"
                    
                    let width = geometry.size.width
                    let height = geometry.size.height
                    let targetWidth = width
                    let targetHeight = targetWidth * aspectRatio
                    
                    let box = calculateBox(for: result, in: geometry, cameraResolution)
                    
                    
                    let box = calculateBox(for: result, in: CGSize(width: targetWidth, height: targetHeight), cameraResolution)
                    
                    DetectionBoxView(
                        result: result,
                        color: color,
                        label: label,
                        box: box)
                            .customFrame(targetWidth: targetWidth, targetHeight: targetHeight,
                                         geometryWidth: width, geometryHeight: height)
                }
                
            }
        }
        .ignoresSafeArea()
        .onAppear {
            cameraModel.checkCameraPermission()
        }.onDisappear {
            yolov8.close()
            cameraModel.close()
        }.onChange(of: cameraModel.resolution) { newResolution in
            self.aspectRatio = newResolution.width / newResolution.height
        }
    }
    
    private func getClassColor(classId: Int32) -> Color {
        let r = Double((Int(classId) + 72) * 1717 % 256) / 255.0
        let g = Double((Int(classId) + 7) * 33 % 126 + 70) / 255.0
        let b = Double((Int(classId) + 47) * 107 % 256) / 255.0
        return Color(red: r, green: g, blue: b)
    }
    
    private func calculateBox(for result: YOLOv8Result, in targetSize: CGSize, _ cameraResolution: CGSize) -> [CGFloat] {
        print(targetSize)
        let ret = [
            CGFloat(result.box[0]) * (targetSize.width / cameraResolution.height),
            CGFloat(result.box[1]) * (targetSize.height / cameraResolution.width),
            CGFloat(result.box[2]) * (targetSize.width / cameraResolution.height),
            CGFloat(result.box[3]) * (targetSize.height / cameraResolution.width)
        ]
        return ret
    }
    
}
