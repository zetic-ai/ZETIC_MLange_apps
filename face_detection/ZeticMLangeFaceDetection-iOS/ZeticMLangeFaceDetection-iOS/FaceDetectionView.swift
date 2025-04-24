import SwiftUI
import AVFoundation
import Vision

import ZeticMLange

struct FaceDetectionView: View {
    @StateObject private var cameraModel: CameraModel = CameraModel(.front, .image)
    
    @StateObject private var faceDetection: FaceDetection = FaceDetection()
    @Environment(\.scenePhase) private var scenePhase
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                CameraPreview(session: cameraModel.session)
                    .ignoresSafeArea()
                
                ZStack {
                    if cameraModel.image != nil {
                        let input = FaceDetectionInput(image: cameraModel.image!)
                        let _ = faceDetection.process(input: input)
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
            cameraModel.close()
        }
        .onChange(of: scenePhase) { newPhase in
            switch newPhase {
            case .background:
                cameraModel.close()
            case .inactive:
                break
            case .active:
                cameraModel.checkCameraPermission()
                break
            @unknown default:
                break
            }
        }
    }
}
