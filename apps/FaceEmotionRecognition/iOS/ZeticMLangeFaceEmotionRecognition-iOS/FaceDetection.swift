import Foundation
import UIKit
import ZeticMLange

class FaceDetection {
    private let model = (try? ZeticMLangeModel(personalKey: "YOUR_PERSONAL_KEY", name: "YOUR_PROJECT_NAME", version: 1))!
    private let wrapper = FaceDetectionWrapper()
    
    func process(input: FaceDetectionInput) -> FaceDetectionOutput {
        do {
            let preprocess = wrapper.preprocess(input.image)
            try model.run([preprocess])
            var modelOutput = model.getOutputDataArray()
            let faces = wrapper.postprocess(&modelOutput)
            return FaceDetectionOutput(faces: faces)
        } catch {
            print(error)
        }
        return FaceDetectionOutput(faces: [])
    }
}

struct FaceDetectionInput: AsyncFeatureInput {
    let image: UIImage
}

struct FaceDetectionOutput: AsyncFeatureOutput {
    let faces: Array<FaceDetectionResult>
    
    init(faces: Array<FaceDetectionResult> = []) {
        self.faces = faces
    }
}

extension FaceDetectionResult {
    func toBox(_ size: CGSize) -> Box {
        let sizeWidth = Float(size.width)
        let sizeHeight = Float(size.height)
        let ratio = ((sizeWidth + sizeHeight) / 2) / sizeHeight
        let resizeFactor: Float = 1.3
        
        let width = ((bbox.xmax - bbox.xmin) / ratio) * resizeFactor
        let height = ((bbox.ymax - bbox.ymin) * ratio) * resizeFactor
        let x = bbox.xmin - ((width - (bbox.xmax - bbox.xmin)) / 2)
        let y = (bbox.ymin - ((height - (bbox.ymax - bbox.ymin)) / 2)) * 0.9
        
        return Box(xmin: x.clamped(to: 0...1), ymin: y.clamped(to: 0...1), xmax: (x + width).clamped(to: 0...1), ymax: (y + height).clamped(to: 0...1))
    }
}

extension Box {
    var width: Float { xmax - xmin }
    var height: Float { ymax - ymin }
}
