import SwiftUI
import AVFoundation
import Vision

import ZeticMLange

class CameraModel: NSObject, ObservableObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    class FrameResult {
        let fps: Double
        let results: [ZeticMLangeYoloV8Result]
        init(_ fps: Double, _ results: [ZeticMLangeYoloV8Result]) {
            self.fps = fps
            self.results = results
        }
    }
    
    @Published var currentFPS: Double = 0
    @Published var detectionResults: [ZeticMLangeYoloV8Result] = []
    
    public let modelKey: String
    
    @Published var session = AVCaptureSession()
    private let videoOutput = AVCaptureVideoDataOutput()
    private let processingQueue = DispatchQueue(label: "image_processing_queue", qos: .userInitiated)
    private let fpsQueue = DispatchQueue(label: "fps_queue", qos: .userInitiated)
    private var contextPool: CGContext?
    private let contextQueue = DispatchQueue(label: "context_queue")
    public var resolution = CGSize(width: 640, height: 480)
    
    private var yoloModel: ZeticMLangeModel
    private let yoloFeature: ZeticMLangeFeatureYolov8
    private let context: CIContext
    
    private var frameCount: Int = 0
    private let fpsUpdateInterval: CFTimeInterval = 1e-6
    private var isProcessing: Bool = false
    
    private var lastTimestamp = CMTime()
    private var fpsCounter = 0
    private var yoloPerFrame = 0
    
    init(mlange_model_key: String) {
        modelKey = mlange_model_key
        let contextOptions = [CIContextOption.useSoftwareRenderer: false]
        self.context = CIContext(options: contextOptions)
        
        let model = ZeticMLangeModel(mlange_model_key)!
        let yamlURL = Bundle.main.url(forResource: "coco", withExtension: "yaml")!
        
        let feature = ZeticMLangeFeatureYolov8(yamlURL.absoluteString)
        
        self.yoloModel = model
        self.yoloFeature = feature
        
        super.init()
    }
    
    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        autoreleasepool {
            let currentTimestamp = CMSampleBufferGetPresentationTimeStamp(sampleBuffer)
            
            guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
                return
            }
            
            CVPixelBufferLockBaseAddress(pixelBuffer, .readOnly)
            defer {
                CVPixelBufferUnlockBaseAddress(pixelBuffer, .readOnly)
            }
            
            let frameResult: FrameResult? = processYolo(pixelBuffer)
            DispatchQueue.main.async { [weak self] in
                if let result = frameResult {
                    self?.detectionResults = result.results
                }
            }
            
            if (lastTimestamp.value == 0) {
                lastTimestamp = currentTimestamp
            } else if (lastTimestamp != CMTime.zero) {
                fpsCounter += 1
                let deltaTime = CMTimeGetSeconds(currentTimestamp - lastTimestamp)
                
                if deltaTime > 1.0 {
                    let fps = Double(fpsCounter) / deltaTime
                    print("frame \(fps)")
                    DispatchQueue.main.async { [weak self] in
                        self?.currentFPS = fps
                        self?.fpsCounter = 0
                    }
                    print("yoloPerFrame \(yoloPerFrame)")
                    lastTimestamp = currentTimestamp
                    yoloPerFrame = 0
                }
            }
        }
    }
    
    func processPixelBuffer(_ pixelBuffer: CVPixelBuffer) -> (UnsafeMutableRawPointer, Int32, Int32, Int32)? {
        let width = CVPixelBufferGetWidth(pixelBuffer)
        let height = CVPixelBufferGetHeight(pixelBuffer)
        let bytesPerRow = CVPixelBufferGetBytesPerRow(pixelBuffer)
        let pixelFormat = CVPixelBufferGetPixelFormatType(pixelBuffer)
        
        guard let baseAddress = CVPixelBufferGetBaseAddress(pixelBuffer) else {
            print("Failed to get bass address of pixel buffer")
            CVPixelBufferUnlockBaseAddress(pixelBuffer, .readOnly)
            return nil
        }
        
        guard pixelFormat == kCVPixelFormatType_32BGRA else {
            print("Unsupported pixel format")
            CVPixelBufferUnlockBaseAddress(pixelBuffer, .readOnly)
            return nil
        }
        
        return (baseAddress, Int32(width), Int32(height), Int32(bytesPerRow))
    }
    
    private func processYolo(_ pixelBuffer: CVPixelBuffer) -> FrameResult? {
        autoreleasepool {
            let totalTimeBegin = CACurrentMediaTime()
            
            let processedData =  processPixelBuffer(pixelBuffer)!
            let yoloProcessedData = yoloFeature.preprocess(processedData.0, processedData.1, processedData.2, processedData.3)
            let yoloModelInput = [yoloProcessedData]
            
            do {
                try yoloModel.run(yoloModelInput)
                var outputs = yoloModel.getOutputDataArray()
                yoloPerFrame += 1
                
                let detectionResults = yoloFeature.postprocess(&outputs[0])
                let totalElapsedTime = CACurrentMediaTime() - totalTimeBegin
                
                return FrameResult(Double(1/totalElapsedTime), detectionResults)
            } catch {
                print("YOLO processing error: \(error)")
                return nil
            }
        }
    }
    
    private func setupCamera() {
        do {
            session.beginConfiguration()
            
            guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) else {
                print("Error: Unable to access the back camera.")
                session.commitConfiguration()
                return
            }
            
            // Find a suitable format that supports 240fps
            var selectedFormat: AVCaptureDevice.Format?
            var maxFrameRate: Double = 0
            
            for format in device.formats {
                let formatDescription = format.formatDescription
                let dimensions = CMVideoFormatDescriptionGetDimensions(formatDescription)
                
                print(format)
                // Check if the format supports 240fps at desired resolution
                //                if dimensions.width == 1280 && dimensions.height == 720 {
                for range in format.videoSupportedFrameRateRanges {
                    if range.maxFrameRate == 120 {
                        selectedFormat = format
                        maxFrameRate = range.maxFrameRate
                        break
                    }
                }
                if selectedFormat != nil {
                    break
                }
            }
            //            }
            
            if let selectedFormat = selectedFormat {
                try device.lockForConfiguration()
                device.activeFormat = selectedFormat
                device.activeVideoMinFrameDuration = CMTimeMake(value: 1, timescale: Int32(maxFrameRate))
                device.activeVideoMaxFrameDuration = CMTimeMake(value: 1, timescale: Int32(maxFrameRate))
                device.unlockForConfiguration()
                print("Selected format: \(selectedFormat)")
                print("Configured min frame duration: \(device.activeVideoMinFrameDuration)")
                print("Configured max frame duration: \(device.activeVideoMaxFrameDuration)")
                let configuredFPS = Double(device.activeVideoMaxFrameDuration.timescale) / Double(device.activeVideoMaxFrameDuration.value)
                print("Configured FPS: \(configuredFPS)")
            } else {
                print("Error: 240fps not supported on this device at the desired resolution.")
                session.commitConfiguration()
                return
            }
            
            let input = try AVCaptureDeviceInput(device: device)
            
            // Set session preset to input priority
            session.sessionPreset = .inputPriority
            
            if session.canAddInput(input) {
                session.addInput(input)
            } else {
                print("Error: Cannot add input to session.")
                session.commitConfiguration()
                return
            }
            
            // Configure video output
            videoOutput.alwaysDiscardsLateVideoFrames = true
            videoOutput.videoSettings = [
                kCVPixelBufferPixelFormatTypeKey as String: Int(kCVPixelFormatType_32BGRA)
            ]
            videoOutput.setSampleBufferDelegate(self, queue: processingQueue)
            
            if session.canAddOutput(videoOutput) {
                session.addOutput(videoOutput)
            } else {
                print("Error: Cannot add output to session.")
                session.commitConfiguration()
                return
            }
            
            // Set the video orientation and disable stabilization
            if let connection = videoOutput.connection(with: .video) {
                connection.videoOrientation = .portrait
                connection.isVideoMirrored = false
                if connection.isVideoStabilizationSupported {
                    connection.preferredVideoStabilizationMode = .off
                }
            }
            
            session.commitConfiguration()
            
            // Start running the session
            DispatchQueue.global(qos: .userInitiated).async { [weak self] in
                self?.session.startRunning()
                self?.resolution = CGSize(
                    width: CGFloat(device.activeFormat.formatDescription.dimensions.width),
                    height: CGFloat(device.activeFormat.formatDescription.dimensions.height)
                )
            }
        } catch {
            print("Camera setup error: \(error)")
            session.commitConfiguration()
        }
    }
    
    func checkCameraPermission() {
        switch AVCaptureDevice.authorizationStatus(for: .video) {
        case .authorized:
            setupCamera()
        case .notDetermined:
            AVCaptureDevice.requestAccess(for: .video) { [weak self] granted in
                if granted {
                    DispatchQueue.main.async {
                        self?.setupCamera()
                    }
                }
            }
        default:
            break
        }
    }
    
}
