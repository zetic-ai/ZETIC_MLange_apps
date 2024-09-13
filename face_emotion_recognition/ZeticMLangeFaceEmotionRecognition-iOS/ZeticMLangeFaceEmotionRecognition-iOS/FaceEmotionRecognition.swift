import Foundation
import UIKit
import ZeticMLange

class FaceEmotionRecognition {
    private let model: ZeticMLangeModel
    private let featureModel: ZeticMLangeFeatureFaceEmotionRecognition
    
    init (_ modelKey: String) {
        model = ZeticMLangeModel(modelKey)!
        featureModel = ZeticMLangeFeatureFaceEmotionRecognition()
    }
    
    func run(_ image: UIImage, _ roi: Box) -> FaceEmotionRecognitionResult? {
        do {
            let preprocess = featureModel.preprocess(image, roi)
            if (preprocess.isEmpty) {
                return nil
            }
            try model.run([preprocess])
            var modelOutput = model.getOutputDataArray()
            let result = featureModel.postprocess(&modelOutput)
            return result
        } catch {
            print(error)
            return nil
        }
    }
}
