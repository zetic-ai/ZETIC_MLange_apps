import Foundation
import UIKit
import ZeticMLange

class FaceEmotionRecognition {
    private let model = ZeticMLangeModel("face_emotion_recognition")!
    private let wrapper = FaceEmotionRecognitionWrapper()
    
    func process(input: FaceEmotionRecognitionInput) -> FaceEmotionRecognitionOutput {
        do {
            let preprocess = wrapper.preprocess(input.image, input.roi)
            if (preprocess.isEmpty) {
                throw ZeticMLangeError("preprocess failed")
            }
            try model.run([preprocess])
            var modelOutput = model.getOutputDataArray()
            let result = wrapper.postprocess(&modelOutput)
            return FaceEmotionRecognitionOutput(result: result)
        } catch {
            print(error)
        }
        return FaceEmotionRecognitionOutput(result: FaceEmotionRecognitionResult(emotion: "", confidence: 0))
    }
}

struct FaceEmotionRecognitionInput: AsyncFeatureInput {
    let image: UIImage
    let roi: Box
}

struct FaceEmotionRecognitionOutput: AsyncFeatureOutput {
    let result: FaceEmotionRecognitionResult
    
    init(result: FaceEmotionRecognitionResult = FaceEmotionRecognitionResult(emotion: "", confidence: 0)) {
        self.result = result
    }
}
