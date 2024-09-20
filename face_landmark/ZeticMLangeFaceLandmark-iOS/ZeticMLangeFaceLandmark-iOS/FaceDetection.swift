import Foundation
import UIKit
import ZeticMLange

class FaceDetection {
    private let model: ZeticMLangeModel
    private let featureModel: ZeticMLangeFeatureFaceDetection
    
    init (_ modelKey: String) {
        model = ZeticMLangeModel(modelKey)!
        featureModel = ZeticMLangeFeatureFaceDetection()
    }
    
    func run(_ image: UIImage) -> Array<FaceDetectionResult> {
        do {
            let preprocess = featureModel.preprocess(image)
            try model.run([preprocess])
            var modelOutput = model.getOutputDataArray()
            return featureModel.postprocess(&modelOutput)
        } catch {
            print(error)
            return []
        }
    }
}
