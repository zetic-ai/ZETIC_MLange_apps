import SwiftUI
import AVFoundation
import Vision

import ZeticMLange

struct FaceLandmarkView: View {
    @StateObject private var cameraModel: CameraModel = CameraModel(.front, .image)
    
    @StateObject private var faceLandmarkPipeline: FaceLandmarkPipeline = FaceLandmarkPipeline()
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                CameraPreview(session: cameraModel.session)
                    .ignoresSafeArea()
                
                ZStack {
                    if cameraModel.image != nil {
                        let _ = faceLandmarkPipeline.process(input: FaceDetectionInput(image: cameraModel.image!))
                    }
                    
                    FaceDetectionOverlay(
                        results: faceLandmarkPipeline.result.faceDetectionOutput.faces
                    )
                    
                    if !faceLandmarkPipeline.result.faceDetectionOutput.faces.isEmpty {
                        let roi = faceLandmarkPipeline.result.faceDetectionOutput.faces[0].toBox(geometry.size)
                        
                        FaceLandmarkOverlay(
                            result: faceLandmarkPipeline.result.faceLandmarkOutput,
                            roi: roi
                        )
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
        .onDisappear() {
            cameraModel.close()
            faceLandmarkPipeline.close()
        }
    }
}
