import Foundation
import UIKit
import ZeticMLange

class FaceLandmark {
    private let model: ZeticMLangeModel
    private let featureModel: ZeticMLangeFeatureFaceLandmark
    
    init (_ modelKey: String) {
        model = ZeticMLangeModel(modelKey)!
        featureModel = ZeticMLangeFeatureFaceLandmark()
    }
    
    func run(_ image: UIImage, _ roi: Box) -> FaceLandmarkResult? {
        do {
            let preprocess = featureModel.preprocess(image, roi)
            if (preprocess.isEmpty) {
                return nil
            }
            try model.run([preprocess])
            var modelOutput = model.getOutputDataArray()
            let result = featureModel.postprocess(&modelOutput)
            if result.faceLandmark.isEmpty {
                return nil
            }
            return result
        } catch {
            print(error)
            return nil
        }
    }
}
