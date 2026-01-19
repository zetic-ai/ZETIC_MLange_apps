import Foundation
import UIKit
import ZeticMLange
import CoreVideo

// MARK: - CocoClasses
enum CocoClasses {
    // Sourced from apps/YOLOv26/coco.yaml
    static let names: [String] = [
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat", "traffic light",
        "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog", "horse", "sheep", "cow",
        "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
        "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove", "skateboard", "surfboard",
        "tennis racket", "bottle", "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple",
        "sandwich", "orange", "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
        "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone",
        "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
        "hair drier", "toothbrush"
    ]
}

// MARK: - BoundingBox
struct BoundingBox: Identifiable {
    let id = UUID()
    let rect: CGRect
    let classIndex: Int
    let score: Float
    let label: String
}

// MARK: - ImageUtils
class ImageUtils {
    static func resize(image: UIImage, to size: CGSize) -> UIImage? {
        UIGraphicsBeginImageContextWithOptions(size, false, 1.0)
        image.draw(in: CGRect(origin: .zero, size: size))
        let resizedImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return resizedImage
    }

    static func prepareInput(image: UIImage, targetSize: CGSize = CGSize(width: 640, height: 640)) -> [Float]? {
        // 1. Resize and normalize orientation using UIKit
        // This 'bakes' the orientation into the pixel data ensuring upright image
        guard let resized = resize(image: image, to: targetSize),
              let cgImage = resized.cgImage else { return nil }
        
        let width = Int(targetSize.width)
        let height = Int(targetSize.height)
        let totalPixels = width * height
        
        // Force RGBA (ByteOrder32Big + PremultipliedLast)
        // R G B A
        let colorSpace = CGColorSpaceCreateDeviceRGB()
        let bitmapInfo = CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue | CGBitmapInfo.byteOrder32Big.rawValue)
        
        guard let context = CGContext(data: nil,
                                      width: width,
                                      height: height,
                                      bitsPerComponent: 8,
                                      bytesPerRow: width * 4,
                                      space: colorSpace,
                                      bitmapInfo: bitmapInfo.rawValue) else { return nil }
        
        context.draw(cgImage, in: CGRect(x: 0, y: 0, width: CGFloat(width), height: CGFloat(height)))
        
        guard let data = context.data else { return nil }
        
        let ptr = data.bindMemory(to: UInt8.self, capacity: totalPixels * 4)
        var normalizedArray = [Float](repeating: 0.0, count: 3 * totalPixels)
        
        // Extract RGB Planar [3, H, W]
        // Source is Packed RGBA [H, W, 4]
        for i in 0..<totalPixels {
            let offset = i * 4
            let r = Float(ptr[offset]) / 255.0
            let g = Float(ptr[offset + 1]) / 255.0
            let b = Float(ptr[offset + 2]) / 255.0
            
            normalizedArray[i] = r
            normalizedArray[totalPixels + i] = g
            normalizedArray[2 * totalPixels + i] = b
        }
        
        return normalizedArray
    }
}

// MARK: - YOLOv26Model
class YOLOv26Model: ObservableObject {
    private var model: ZeticMLangeModel?
    
    // Config
    private let targetSize = CGSize(width: 640, height: 640)
    private let confidenceThreshold: Float = 0.25
    private let iouThreshold: Float = 0.45
    private let classCount = 80
    private let totalOutput = 8400 
    
    init() {
        do {
            self.model = try ZeticMLangeModel(tokenKey: "dev_d786c1fd7f2848acb9b0bf8060aa10b2", name: "Team_ZETIC/YOLOv26", version: 3)
        } catch {
            print("Failed to load ZeticMLangeModel: \(error)")
        }
    }
    
    @Published var debugText: String = ""
    
    func detect(image: UIImage, completion: @escaping ([BoundingBox], Double) -> Void) {
        guard let model = model else {
            print("Model not initialized")
            completion([], 0)
            return
        }
        
        DispatchQueue.global(qos: .userInitiated).async {
            let startTime = CFAbsoluteTimeGetCurrent()
            
            guard let inputData = ImageUtils.prepareInput(image: image, targetSize: self.targetSize) else {
                DispatchQueue.main.async { completion([], 0) }
                return
            }
            
            // Debug: Check Input Data Stats
            let count = inputData.count
            var minVal: Float = 1000.0
            var maxVal: Float = -1000.0
            var sumVal: Float = 0.0
            for v in inputData {
                if v < minVal { minVal = v }
                if v > maxVal { maxVal = v }
                sumVal += v
            }
            let avgVal = sumVal / Float(count)
            print("DEBUG: Input Check - Count: \(count), Min: \(minVal), Max: \(maxVal), Avg: \(avgVal)")
            
            do {
                let inputDataBytes = inputData.withUnsafeBufferPointer { Data(buffer: $0) }
                let inputTensor = ZeticMLange.Tensor(data: inputDataBytes, dataType: ZeticMLange.BuiltinDataType.float32, shape: [1, 3, 640, 640])
                let inputs: [ZeticMLange.Tensor] = [inputTensor]
                
                let outputs = try model.run(inputs: inputs)
                
                guard let outputTensor = outputs.first else {
                    DispatchQueue.main.async { completion([], 0) }
                    return
                }
                
                
                // Inspect Shape
                let shape = outputTensor.shape
                let dim1 = shape.count > 1 ? shape[1] : 0
                let dim2 = shape.count > 2 ? shape[2] : 0
                
                var results: [BoundingBox] = []
                var debugInfo = ""
                
                // CASE 1: Standard YOLO Output with NMS (e.g. CoreML Export) -> [1, 300, 6]
                // Layout: [Batch, Boxes, 6] where 6 = [x, y, w, h, score, class]
                if dim1 == 300 && dim2 == 6 {
                    results = self.postprocessNMS(output: outputTensor, originalSize: image.size)
                    debugInfo = "Shape: \(shape) | Pre-NMS Output (Standard Export)"
                }
                // CASE 2: Raw YOLO Output -> [1, 84, 8400] (or similar)
                else {
                    var detectedClassCount = 80
                    var outputAnchors = 8400
                    
                    // Heuristic: The dimension closer to 8400 is anchors.
                    if dim2 > dim1 {
                        detectedClassCount = dim1 - 4
                        outputAnchors = dim2
                    } else {
                        detectedClassCount = dim2 - 4
                        outputAnchors = dim1
                    }
                    
                    results = self.postprocess(output: outputTensor, originalSize: image.size, classCount: detectedClassCount, outputAnchors: outputAnchors)
                    debugInfo = "Shape: \(shape) | Classes: \(detectedClassCount) | Anchors: \(outputAnchors) (Raw Output)"
                }
                
                let duration = CFAbsoluteTimeGetCurrent() - startTime
                print("DEBUG: \(debugInfo)")
                
                DispatchQueue.main.async {
                    self.debugText = debugInfo
                    completion(results, duration)
                }
            } catch {
                print("Inference failed: \(error)")
                DispatchQueue.main.async {
                    self.debugText = "Error: \(error.localizedDescription)"
                    completion([], 0)
                }
            }
        }
    }
    
    private func postprocessNMS(output: ZeticMLange.Tensor, originalSize: CGSize) -> [BoundingBox] {
        let data: [Float] = output.data.withUnsafeBytes {
            let buffer = $0.bindMemory(to: Float.self)
            return Array(buffer)
        }
        
        var boxes: [BoundingBox] = []
        let rows = 300
        let cols = 6
        
        for i in 0..<rows {
            let offset = i * cols
            if offset + 5 >= data.count { break }
            
            let score = data[offset + 4]
            if score > confidenceThreshold {
                let classIndex = Int(data[offset + 5])
                
                let x1 = data[offset + 0]
                let y1 = data[offset + 1]
                let x2 = data[offset + 2]
                let y2 = data[offset + 3]
                
                // CoreML Export NMS Output is [x1, y1, x2, y2] in absolute pixels (0-640)
                // Normalize to 0-1
                let rect = CGRect(
                    x: CGFloat(x1) / 640.0,
                    y: CGFloat(y1) / 640.0,
                    width: CGFloat(x2 - x1) / 640.0,
                    height: CGFloat(y2 - y1) / 640.0
                )
                
                let label = (classIndex >= 0 && classIndex < CocoClasses.names.count) ? CocoClasses.names[classIndex] : "Class \(classIndex)"
                
                boxes.append(BoundingBox(
                    rect: rect,
                    classIndex: classIndex,
                    score: score,
                    label: label
                ))
            }
        }
        // No NMS needed, model did it
        return boxes
    }

    private func postprocess(output: ZeticMLange.Tensor, originalSize: CGSize, classCount: Int, outputAnchors: Int) -> [BoundingBox] {
        let data: [Float] = output.data.withUnsafeBytes {
            let buffer = $0.bindMemory(to: Float.self)
            return Array(buffer)
        }
        
        var boxes: [BoundingBox] = []
        
        for anchorIndex in 0..<outputAnchors {
            var maxScore: Float = 0
            var maxClassIndex = -1
            
            for c in 0..<classCount {
                let classRow = 4 + c
                // Assuming [1, 4+C, Anchors] layout flattened: row * outputAnchors + col
                let index = classRow * outputAnchors + anchorIndex
                if index < data.count {
                    let score = data[index]
                    if score > maxScore {
                        maxScore = score
                        maxClassIndex = c
                    }
                }
            }
            
                if maxScore > confidenceThreshold {

                    // Debug: Specifically hunt for the Refrigerator (Class 72) or any non-person
                    if maxClassIndex != 0 {
                        let printLabel = (maxClassIndex >= 0 && maxClassIndex < CocoClasses.names.count) ? CocoClasses.names[maxClassIndex] : "Class \(maxClassIndex)"
                        print("!!! ANOMALY DETECTED !!! Anchor [\(anchorIndex)] Label: \(printLabel) (\(maxClassIndex)) MaxScore: \(maxScore)")
                        
                        // Print top 5 scores
                        var topAny: [(Int, Float)] = []
                        for c in 0..<classCount {
                           let idx = (4+c) * outputAnchors + anchorIndex
                           if idx < data.count {
                               topAny.append((c, data[idx]))
                           }
                        }
                        // Filter: Only show classes with > 5% confidence
                        let meaningful = topAny.filter { $0.1 > 0.05 }
                        
                        let topStr = meaningful.prefix(5).map { "Cls \($0.0)=\(String(format: "%.3f", $0.1))" }.joined(separator: ", ")
                        print("  -> Candidates (>5%): \(topStr.isEmpty ? "None" : topStr)")

                        
                        // Print Raw Box Values to check normalization
                        let rX = data[0 * outputAnchors + anchorIndex]
                        let rY = data[1 * outputAnchors + anchorIndex]
                        let rW = data[2 * outputAnchors + anchorIndex]
                        let rH = data[3 * outputAnchors + anchorIndex]
                        print("  -> Raw Box: [\(rX), \(rY), \(rW), \(rH)]")
                    }
                
                let xc = data[0 * outputAnchors + anchorIndex]
                let yc = data[1 * outputAnchors + anchorIndex]
                let w = data[2 * outputAnchors + anchorIndex]
                let h = data[3 * outputAnchors + anchorIndex]
                
                let x = xc - w / 2
                let y = yc - h / 2
                
                let rect = CGRect(
                    x: CGFloat(x) / 640.0,
                    y: CGFloat(y) / 640.0,
                    width: CGFloat(w) / 640.0,
                    height: CGFloat(h) / 640.0
                )
                
                let label = (maxClassIndex >= 0 && maxClassIndex < CocoClasses.names.count) ? CocoClasses.names[maxClassIndex] : "Class \(maxClassIndex)"
                
                boxes.append(BoundingBox(
                    rect: rect,
                    classIndex: maxClassIndex,
                    score: maxScore,
                    label: label
                ))
            }
        }
        
        return nms(boxes: boxes)
    }
    
    private func nms(boxes: [BoundingBox]) -> [BoundingBox] {
        let sortedBoxes = boxes.sorted { $0.score > $1.score }
        var selected: [BoundingBox] = []
        
        for box in sortedBoxes {
            var keep = true
            for other in selected {
                if iou(boxA: box.rect, boxB: other.rect) > CGFloat(iouThreshold) {
                    keep = false
                    break
                }
            }
            if keep {
                selected.append(box)
            }
        }
        return selected
    }
    
    private func iou(boxA: CGRect, boxB: CGRect) -> CGFloat {
        let intersection = boxA.intersection(boxB)
        let interArea = intersection.width * intersection.height
        if intersection.width <= 0 || intersection.height <= 0 { return 0 }
        
        let boxAArea = boxA.width * boxA.height
        let boxBArea = boxB.width * boxB.height
        
        return interArea / (boxAArea + boxBArea - interArea)
    }
}
