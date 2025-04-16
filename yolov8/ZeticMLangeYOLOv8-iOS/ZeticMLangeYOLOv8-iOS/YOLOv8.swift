import Foundation
import ZeticMLange
import AVFoundation

class YOLOv8: ObservableObject {
    @Published private(set) var currentFPS: Double = 0
    @Published private(set) var detectionResults: [YOLOv8Result] = []
    
    public static let modelKey = "b9f5d74e6f644288a32c50174ded828e"
    
    private let model = (try? ZeticMLangeModel("debug_cb6cb12939644316888f333523e42622", modelKey))!
    
    private var fpsCounter = 0
    private var processPerSecond = 0
    private var lastTimestamp = CMTime.zero
    
    private let wrapper: YOLOv8Wrapper
    
    init() {
        let yamlURL = Bundle.main.url(forResource: "coco", withExtension: "yaml")!
        self.wrapper = YOLOv8Wrapper(yamlURL.absoluteString)
    }
    
    func process(input: YOLOv8Input) {
        let frame = input.frame
        let totalTimeBegin = CACurrentMediaTime()
        
        let output = frame.withUnsafeBytes { frameBytes in
            let yoloProcessedData = wrapper.featurePreprocess(
                frameBytes.baseAddress!,
                frame.width,
                frame.height,
                frame.bytesPerRow
            )
            let yoloModelInput = [yoloProcessedData]
            
            do {
                try model.run(yoloModelInput)
                var outputs = model.getOutputDataArray()
                processPerSecond += 1
                
                return outputs[0].withUnsafeMutableBytes { bufferPtr in
                    let pointer = bufferPtr.baseAddress!.assumingMemoryBound(to: UInt8.self)
                    let detectionResults = wrapper.featurePostprocess(pointer)
                    let totalElapsedTime = CACurrentMediaTime() - totalTimeBegin
                    
                    return YOLOv8Output(results: detectionResults, fps: Double(1/totalElapsedTime))
                }
            } catch {
                print("YOLO processing error: \(error)")
                return YOLOv8Output(results: [], fps: 0)
            }
        }
        DispatchQueue.main.async {
            self.detectionResults = output.results
        }
    }
    
    func close() {
        detectionResults = []
    }
}

struct YOLOv8Input: AsyncFeatureInput {
    let frame: CameraFrame
    let timestamp: CMTime
}

struct YOLOv8Output: AsyncFeatureOutput {
    let results: [YOLOv8Result]
    let fps: Double
}
