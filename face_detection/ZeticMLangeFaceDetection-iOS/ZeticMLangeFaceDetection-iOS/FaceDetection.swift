import Foundation
import UIKit
import ZeticMLange

class FaceDetection: ObservableObject {
    @Published var faces: Array<FaceDetectionResult> = []
    
    private var isProcessing = false
    
    private let model = ZeticMLangeModel("face_detection_short_range")!
    private let wrapper = FaceDetectionWrapper()
    
    func process(input: FaceDetectionInput) {
        if isProcessing {
            return
        }
        isProcessing = true
        
        DispatchQueue.global().async { [self] in
            let preprocess = wrapper.preprocess(input.image)
            do {
                try model.run([preprocess])
            } catch {
                
            }
            var modelOutput = model.getOutputDataArray()
            let faces = wrapper.postprocess(&modelOutput)
            let output =  FaceDetectionOutput(faces: faces)
            DispatchQueue.main.async {
                self.faces = output.faces
                self.isProcessing = false
            }
        }
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
