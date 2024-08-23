import SwiftUI
import AVFoundation
import Vision

import ZeticMLange

struct ContentView: View {
    @StateObject private var cameraModel = CameraModel(mlange_model_key: "yolo-v8n-test")
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                if let processedImage = cameraModel.processedImage {
                    Image(uiImage: processedImage)
                        .resizable()
                        .scaledToFill()
                        .frame(width: geometry.size.width, height: geometry.size.height)
                        .clipped()
                } else {
                    Color.black // Placeholder while waiting for the first processed image
                }
            }
        }
        .ignoresSafeArea()
        .onAppear {
            
            cameraModel.checkCameraPermission()
        }
    }
}

class CameraModel: NSObject, ObservableObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    @Published var processedImage: UIImage?
    
    private let session = AVCaptureSession()
    private let videoOutput = AVCaptureVideoDataOutput()
    private let processingQueue = DispatchQueue(label: "image_processing_queue", qos: .userInitiated)
    
    private var yoloModel: ZeticMLangeModel!
    private let yoloFeature = ZeticMLangeFeatureYolov8(Bundle.main.url(forResource: "coco", withExtension: "yaml")!.absoluteString)
    private let context = CIContext()
    
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
    
    init(mlange_model_key: String) {
        do {
            self.yoloModel = try ZeticMLangeModel(mlange_model_key)
        } catch {
            print("ZETIC MLANGE YOLO8 TEST", "Failed to read extra data: \(error)")
        }
        super.init()
    }
    
    private func setupCamera() {
        do {
            self.session.beginConfiguration()
            
            guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back) else {
                print("Failed to get camera device")
                return
            }
            
            let input = try AVCaptureDeviceInput(device: device)
            
            if self.session.canAddInput(input) {
                self.session.addInput(input)
            }
            
            if self.session.canAddOutput(self.videoOutput) {
                self.session.addOutput(self.videoOutput)
            }
            
            videoOutput.setSampleBufferDelegate(self, queue: processingQueue)
            
            if let connection = videoOutput.connection(with: .video) {
                connection.videoOrientation = .portrait
            }
            
            self.session.commitConfiguration()
            
            DispatchQueue.global(qos: .background).async { [weak self] in
                self?.session.startRunning()
            }
            
            
        } catch {
            print("Error setting up camera: \(error.localizedDescription)")
        }
    }
    
    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
            return
        }
        
        let ciImage = CIImage(cvPixelBuffer: pixelBuffer)
        
        guard let cgImage = context.createCGImage(ciImage, from: ciImage.extent) else {
            return
        }
        
        let image = UIImage(cgImage: cgImage)
        let processedImage = self.processYolo(image)
        
        
        DispatchQueue.main.async {
            self.processedImage = processedImage
        }
    }
    
    private func processYolo(_ image: UIImage) -> UIImage? {
        var yoloProcessedData = self.yoloFeature.preprocess(image)
        let yoloModelInput = [yoloProcessedData]
            
        do {
            
            try self.yoloModel.run(yoloModelInput);
            var outputs = self.yoloModel.getOutputDataArray()
                        
            let yoloResultImage = self.yoloFeature.postprocess(image, &outputs[0])
            return yoloResultImage
        } catch {
            return nil
        }
        
        return nil
    }
}
