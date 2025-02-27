import SwiftUI
import AVFoundation
import Vision

import ZeticMLange

struct FaceEmotionRecognitionView: View {
    @StateObject private var cameraModel: CameraModel = CameraModel(.front, .image)
    
    @StateObject private var faceEmotionRecognitionPipeline: FaceEmotionRecognitionPipeline = FaceEmotionRecognitionPipeline()
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                CameraPreview(session: cameraModel.session)
                    .ignoresSafeArea()
                
                ZStack {
                    if cameraModel.image != nil {
                        let _ = faceEmotionRecognitionPipeline.process(input: FaceDetectionInput(image: cameraModel.image!))
                    }
                    
                    FaceDetectionOverlay(
                        results: faceEmotionRecognitionPipeline.result.faceDetectionOutput.faces
                    )
                    
                    if cameraModel.image != nil && !faceEmotionRecognitionPipeline.result.faceDetectionOutput.faces.isEmpty {
                        let roi = faceEmotionRecognitionPipeline.result.faceDetectionOutput.faces[0].toBox(geometry.size)
                        FaceEmotionRecognitionOverlay(
                            result: faceEmotionRecognitionPipeline.result.faceEmotionRecognitionOutput,
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
        .onDisappear {
            cameraModel.close()
        }
    }
}
