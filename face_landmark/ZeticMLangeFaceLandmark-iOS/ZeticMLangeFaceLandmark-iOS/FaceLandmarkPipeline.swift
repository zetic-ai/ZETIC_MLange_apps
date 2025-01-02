import Foundation
import ZeticMLange
import UIKit

class FaceLandmarkPipeline: AsyncFeature<FaceDetectionInput, FaceLandmarkPipelineOutput> {
    private let faceDetection = FaceDetection(label: "fl_facedetection")
    private let faceLandmark = FaceLandmark(label: "fl_facelandmark")
    
    @Published var result: FaceLandmarkPipelineOutput = FaceLandmarkPipelineOutput()
    
    override func process(input: FaceDetectionInput) -> FaceLandmarkPipelineOutput {
        let faceDetectionOutput = faceDetection.process(input: input)
        if faceDetectionOutput.faces.isEmpty {
            return FaceLandmarkPipelineOutput()
        }
        let faceLandmarkInput = FaceLandmarkInput(image: input.image, roi: faceDetectionOutput.faces[0].toBox(input.image.size))
        return FaceLandmarkPipelineOutput(faceDetectionOutput: faceDetectionOutput, faceLandmarkOutput: faceLandmark.process(input: faceLandmarkInput))
    }
    
    override func handleOutput(_ output: FaceLandmarkPipelineOutput) {
        self.result = output
    }
    
    func run(_ image: UIImage) {
        let input = FaceDetectionInput(image: image)
        run(with: input)
    }
    
    override func close() {
        faceDetection.close()
        faceLandmark.close()
        super.close()
    }
}

struct FaceLandmarkPipelineOutput: AsyncFeatureOutput {
    let faceDetectionOutput: FaceDetectionOutput
    let faceLandmarkOutput: FaceLandmarkOutput
    
    init(faceDetectionOutput: FaceDetectionOutput = FaceDetectionOutput(),
        faceLandmarkOutput: FaceLandmarkOutput = FaceLandmarkOutput()) {
        self.faceDetectionOutput = faceDetectionOutput
        self.faceLandmarkOutput = faceLandmarkOutput
    }
}
