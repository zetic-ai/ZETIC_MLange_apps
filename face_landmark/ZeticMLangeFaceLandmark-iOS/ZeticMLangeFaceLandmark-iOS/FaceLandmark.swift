import Foundation
import UIKit
import ZeticMLange

class FaceLandmark: AsyncFeature<FaceLandmarkInput, FaceLandmarkOutput> {
    @Published var result: FaceLandmarkResult = FaceLandmarkResult(faceLandmark: [], confidence: 0)
    
    private let model = ZeticMLangeModel("face_landmark")!
    private let wrapper = FaceLandmarkWrapper()
    
    override func process(input: FaceLandmarkInput) -> FaceLandmarkOutput {
        do {
            let preprocess = wrapper.preprocess(input.image, input.roi)
            if (preprocess.isEmpty) {
                throw ZeticMLangeError("preprocess failed")
            }
            try model.run([preprocess])
            var modelOutput = model.getOutputDataArray()
            let result = wrapper.postprocess(&modelOutput)
            return FaceLandmarkOutput(result: result)
        } catch {
            print(error)
        }
        return FaceLandmarkOutput()
    }
    
    override func handleOutput(_ output: FaceLandmarkOutput) {
        result = output.result
    }
    
    func run(_ image: UIImage, _ roi: Box) {
        run(with: FaceLandmarkInput(image: image, roi: roi))
    }
}

struct FaceLandmarkInput: AsyncFeatureInput {
    let image: UIImage
    let roi: Box
}

struct FaceLandmarkOutput: AsyncFeatureOutput {
    let result: FaceLandmarkResult
    
    init(result: FaceLandmarkResult = FaceLandmarkResult(faceLandmark: [], confidence: 0)) {
        self.result = result
    }
}
