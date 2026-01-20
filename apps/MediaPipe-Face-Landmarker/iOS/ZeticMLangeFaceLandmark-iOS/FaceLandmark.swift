import Foundation
import UIKit
import ZeticMLange

class FaceLandmark: ObservableObject {
    private let model = (try? ZeticMLangeModel(personalKey: "YOUR_MLANGE_KEY", name: "YOUR_PROJECT_NAME", version: 1))!
    private let wrapper = FaceLandmarkWrapper()
    
    func process(input: FaceLandmarkInput) -> FaceLandmarkOutput {
        do {
            let preprocess = wrapper.preprocess(input.image, input.roi)
            if (preprocess.isEmpty) {
                throw ZeticMLangeError("FaceLandmark", "preprocess failed")
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
