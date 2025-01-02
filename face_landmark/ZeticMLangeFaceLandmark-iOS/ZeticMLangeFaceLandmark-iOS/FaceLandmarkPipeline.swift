import Foundation
import ZeticMLange
import UIKit

class FaceLandmarkPipeline: ObservableObject {
    private let faceDetection = FaceDetection(label: "fl_facedetection")
    private let faceLandmark = FaceLandmark(label: "fl_facelandmark")
    
    private var isProcessing = false
    
    @Published var result: FaceLandmarkPipelineOutput = FaceLandmarkPipelineOutput()
    
    func process(input: FaceDetectionInput) {
        if isProcessing {
            return
        }
        isProcessing = true
        
        DispatchQueue.global(qos: .userInitiated).async { [self] in
            let faceDetectionOutput = faceDetection.process(input: input)
            if faceDetectionOutput.faces.isEmpty {
                self.isProcessing = false
                return
            }
            let faceLandmarkInput = FaceLandmarkInput(image: input.image, roi: faceDetectionOutput.faces[0].toBox(input.image.size))
            let output = FaceLandmarkPipelineOutput(faceDetectionOutput: faceDetectionOutput, faceLandmarkOutput: faceLandmark.process(input: faceLandmarkInput))
            DispatchQueue.main.async {
                self.result = output
                self.isProcessing = false
            }
        }
    }
    func close() {
        faceDetection.close()
        faceLandmark.close()
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
