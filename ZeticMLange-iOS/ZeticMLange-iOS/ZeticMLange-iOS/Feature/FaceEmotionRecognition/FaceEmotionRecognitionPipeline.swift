import Foundation
import ZeticMLange
import UIKit

class FaceEmotionRecognitionPipeline: AsyncFeature<FaceDetectionInput, FaceEmotionRecognitionPipelineOutput> {
    private let faceDetection = FaceDetection(label: "fer_facedetection")
    private let faceEmotionRecognition = FaceEmotionRecognition(label: "fer_emotionrecognition")
    
    @Published var result: FaceEmotionRecognitionPipelineOutput = FaceEmotionRecognitionPipelineOutput()
    
    override func process(input: FaceDetectionInput) -> FaceEmotionRecognitionPipelineOutput {
        let faceDetectionOutput = faceDetection.process(input: input)
        if faceDetectionOutput.faces.isEmpty {
            return FaceEmotionRecognitionPipelineOutput()
        }
        let faceEmotionRecognitionInput = FaceEmotionRecognitionInput(image: input.image, roi: faceDetectionOutput.faces[0].toBox(input.image.size))
        return FaceEmotionRecognitionPipelineOutput(faceDetectionOutput: faceDetectionOutput, faceEmotionRecognitionOutput: faceEmotionRecognition.process(input: faceEmotionRecognitionInput))
    }
    
    override func handleOutput(_ output: FaceEmotionRecognitionPipelineOutput) {
        self.result = output
    }
    
    func run(_ image: UIImage) {
        let input = FaceDetectionInput(image: image)
        run(with: input)
    }
    
    override func close() {
        faceDetection.close()
        faceEmotionRecognition.close()
        super.close()
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
