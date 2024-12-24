import Foundation
import UIKit
import ZeticMLange

class FaceEmotionRecognition: AsyncFeature<FaceEmotionRecognitionInput, FaceEmotionRecognitionOutput> {
    @Published var result: FaceEmotionRecognitionResult = FaceEmotionRecognitionResult(emotion: "", confidence: 0)
    
    private let model = ZeticMLangeModel("face_emotion_recognition")!
    private let wrapper = FaceEmotionRecognitionWrapper()
    
    override func process(input: FaceEmotionRecognitionInput) -> FaceEmotionRecognitionOutput {
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
    
    override func handleOutput(_ output: FaceEmotionRecognitionOutput) {
        self.result = output.result
    }
    
    func run(_ image: UIImage, _ roi: Box) {
        let input = FaceEmotionRecognitionInput(image: image, roi: roi)
        run(with: input)
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
