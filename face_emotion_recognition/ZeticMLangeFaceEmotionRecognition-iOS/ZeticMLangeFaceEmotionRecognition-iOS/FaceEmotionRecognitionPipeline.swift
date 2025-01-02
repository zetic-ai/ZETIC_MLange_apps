import Foundation
import ZeticMLange
import UIKit

class FaceEmotionRecognitionPipeline: ObservableObject {
    private let faceDetection = FaceDetection(label: "fer_facedetection")
    private let faceEmotionRecognition = FaceEmotionRecognition(label: "fer_emotionrecognition")
    private var isProcessing = false
    
    @Published var result: FaceEmotionRecognitionPipelineOutput = FaceEmotionRecognitionPipelineOutput()
    
    func process(input: FaceDetectionInput) {
        if isProcessing {
            return
        }
        isProcessing = true
        DispatchQueue.global(qos: .userInitiated).async {
            let faceDetectionOutput = self.faceDetection.process(input: input)
            if faceDetectionOutput.faces.isEmpty {
                return
            }
            let faceEmotionRecognitionInput = FaceEmotionRecognitionInput(image: input.image, roi: faceDetectionOutput.faces[0].toBox(input.image.size))
            let output = FaceEmotionRecognitionPipelineOutput(faceDetectionOutput: faceDetectionOutput, faceEmotionRecognitionOutput: self.faceEmotionRecognition.process(input: faceEmotionRecognitionInput))
            DispatchQueue.main.async {
                self.result = output
                self.isProcessing = false
            }
        }
    }
    
    func close() {
        faceDetection.close()
        faceEmotionRecognition.close()
    }
}

struct FaceEmotionRecognitionPipelineOutput: AsyncFeatureOutput {
    let faceDetectionOutput: FaceDetectionOutput
    let faceEmotionRecognitionOutput: FaceEmotionRecognitionOutput
    
    init(faceDetectionOutput: FaceDetectionOutput = FaceDetectionOutput(), faceEmotionRecognitionOutput: FaceEmotionRecognitionOutput = FaceEmotionRecognitionOutput()) {
        self.faceDetectionOutput = faceDetectionOutput
        self.faceEmotionRecognitionOutput = faceEmotionRecognitionOutput
    }
}
