import Foundation
import ZeticMLange
import AVFoundation

class YOLOv8: AsyncFeature<YOLOv8Input, YOLOv8Output> {
    @Published private(set) var currentFPS: Double = 0
    @Published private(set) var detectionResults: [YOLOv8Result] = []
    
    private var fpsCounter = 0
    private var processPerSecond = 0
    private var lastTimestamp = CMTime.zero
    
    private let model: ZeticMLangeModel
    private let wrapper: YOLOv8Wrapper
    
    init(key: String) {
        let yamlURL = Bundle.main.url(forResource: "coco", withExtension: "yaml")!
        self.model = ZeticMLangeModel(key)!
        self.wrapper = YOLOv8Wrapper(yamlURL.absoluteString)
        super.init(label: "yolov8")
    }
    
    override func process(input: YOLOv8Input) -> YOLOv8Output {
        let frame = input.frame
        
        let totalTimeBegin = CACurrentMediaTime()
        
        let yoloProcessedData = wrapper.featurePreprocess(frame.imageAddress, frame.width, frame.height, frame.bytesPerRow)
        let yoloModelInput = [yoloProcessedData]
        
        do {
            try model.run(yoloModelInput)
            var outputs = model.getOutputDataArray()
            processPerSecond += 1
            
            let pointer = FeatureUtils.dataToMutableBytePointer(data: &outputs[0])!
            
            let detectionResults = wrapper.featurePostprocess(pointer)
            let totalElapsedTime = CACurrentMediaTime() - totalTimeBegin
            
            return YOLOv8Output(results: detectionResults, fps: Double(1/totalElapsedTime))
        } catch {
            print("YOLO processing error: \(error)")
            return YOLOv8Output(results: [], fps: 0)
        }
    }
    
    override func handleOutput(_ output: YOLOv8Output) {
        self.detectionResults = output.results
        self.currentFPS = output.fps
    }
    
    func run(_ frame: CameraFrame, _ timestamp: CMTime) {
        let input = YOLOv8Input(frame: frame, timestamp: timestamp)
        run(with: input)
    }
    
    override func close() {
        detectionResults = []
        super.close()
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
