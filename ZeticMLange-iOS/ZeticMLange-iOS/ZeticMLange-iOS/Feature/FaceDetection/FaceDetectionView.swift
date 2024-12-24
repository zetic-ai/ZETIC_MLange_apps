import SwiftUI
import AVFoundation
import Vision

import ZeticMLange

struct FaceDetectionView: View {
    @StateObject private var cameraModel: CameraModel = CameraModel(.front, .image)
    
    @StateObject private var faceDetection: FaceDetection = FaceDetection(label: "facedetection")
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                CameraPreview(session: cameraModel.session)
                    .ignoresSafeArea()
                
                ZStack {
                    if cameraModel.image != nil {
                        let _ = faceDetection.run(cameraModel.image!)
                        FaceDetectionOverlay(results: faceDetection.faces)
                    }
                }
                .frame(width: geometry.size.width, height: geometry.size.height)
                .clipped()
            }
        }
        .ignoresSafeArea()
        .onAppear {
            cameraModel.checkCameraPermission()
        }
        .onDisappear {
            faceDetection.waitForPendingOperations {
                faceDetection.close()
                cameraModel.close()
            }
        }
    }
}
