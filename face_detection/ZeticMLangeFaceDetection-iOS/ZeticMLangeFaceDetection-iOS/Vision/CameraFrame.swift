import Foundation

class CameraFrame {
    let imageAddress: UnsafeMutableRawPointer
    let width: Int32
    let height: Int32
    let bytesPerRow: Int32
    
    init(_ imageAddress: UnsafeMutableRawPointer, _ width: Int32, _ height: Int32, _ bytesPerRow: Int32) {
        self.imageAddress = imageAddress
        self.width = width
        self.height = height
        self.bytesPerRow = bytesPerRow
    }
}
