import Foundation
import AVFoundation
import UIKit
import ZeticMLange

class CameraController: NSObject, ObservableObject, AVCaptureVideoDataOutputSampleBufferDelegate {
    @Published var processedImage: UIImage?
    @Published var imagePtr: Int64
    
    private let session = AVCaptureSession()
    private let videoOutput = AVCaptureVideoDataOutput()
    private let processingQueue = DispatchQueue(label: "image_processing_queue", qos: .userInitiated)
    
    private let context = CIContext()
    
    init(processedImage: UIImage? = nil) {
        self.processedImage = processedImage
        imagePtr = 0
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
    
    private func setupCamera() {
        do {
            self.session.beginConfiguration()
            
            guard let device = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .front) else {
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
        
        let transform = CGAffineTransform(scaleX: -1, y: 1)
        
        let flippedImage = ciImage.transformed(by: transform)
        
        
        guard let cgImage = context.createCGImage(flippedImage, from: flippedImage.extent) else {
            return
        }
        
        DispatchQueue.main.async {
            self.processedImage = UIImage(cgImage: cgImage)
        }
    }
}
