import SwiftUI
import UIKit

// MARK: - ImagePicker
struct ImagePicker: UIViewControllerRepresentable {
    @Binding var image: UIImage?
    @Environment(\.presentationMode) var presentationMode

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.delegate = context.coordinator
        picker.sourceType = .photoLibrary
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(parent: self)
    }

    class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let parent: ImagePicker

        init(parent: ImagePicker) {
            self.parent = parent
        }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
            if let uiImage = info[.originalImage] as? UIImage {
                parent.image = uiImage
            }
            parent.presentationMode.wrappedValue.dismiss()
        }
    }
}

// MARK: - ContentView
struct ContentView: View {
    enum DetectionMode {
        case image
        case camera
    }

    @State private var image: UIImage?
    @State private var showingImagePicker = false
    // @State private var boxes: [BoundingBox] = [] // Removed, using detector.boxes
    @State private var processingTime: Double = 0
    @StateObject private var detector = YOLOv26Model()
    
    @State private var detectionMode: DetectionMode = .image
    
    var body: some View {
        NavigationView {
            ZStack {
                if !detector.isModelLoaded {
                    VStack {
                        ProgressView()
                            .scaleEffect(2.0)
                        Text("Loading Model...")
                            .padding(.top, 20)
                    }
                } else {
                    VStack {
                        // Mode Selector
                        Picker("Mode", selection: $detectionMode) {
                            Text("Photo").tag(DetectionMode.image)
                            Text("Camera").tag(DetectionMode.camera)
                        }
                        .pickerStyle(SegmentedPickerStyle())
                        .padding()
                        
                        if detectionMode == .camera {
                            ZStack {
                                CameraView(detector: detector)
                                    .edgesIgnoringSafeArea(.all)
                                
                                // Overlay for Camera
                                GeometryReader { geometry in
                                    self.boundingBoxesView(geometry: geometry)
                                }
                            }
                        } else {
                            // Image Mode
                            if let image = image {
                                ZStack {
                                    Image(uiImage: image)
                                        .resizable()
                                        .aspectRatio(contentMode: .fit)
                                        .overlay(
                                            GeometryReader { geometry in
                                                self.boundingBoxesView(geometry: geometry)
                                            }
                                        )
                                }
                                .frame(maxHeight: 500)
                                .padding()
                            } else {
                                Text("Select an image to start detection")
                                    .foregroundColor(.gray)
                                    .padding()
                            }
                        }
                        
                        if processingTime > 0 {
                            Text(String(format: "Processing Time: %.3f s", processingTime))
                                .font(.caption)
                                .padding(.top, 5)
                            
                            Text(detector.debugText)
                                .font(.caption2)
                                .foregroundColor(.red)
                                .padding(.top, 2)
                        }
                        
                        Spacer()
                        
                        if detectionMode == .image {
                            Button(action: {
                                showingImagePicker = true
                            }) {
                                Text("Select Image")
                                    .font(.headline)
                                    .padding()
                                    .frame(minWidth: 200)
                                    .background(Color.blue)
                                    .foregroundColor(.white)
                                    .cornerRadius(10)
                            }
                            .padding(.bottom, 10)
                            
                            if !detector.boxes.isEmpty {
                                Button(action: {
                                    saveImage()
                                }) {
                                    Text("Save Result")
                                        .font(.headline)
                                        .padding()
                                        .frame(minWidth: 200)
                                        .background(Color.green)
                                        .foregroundColor(.white)
                                        .cornerRadius(10)
                                }
                                .padding(.bottom, 20)
                            }
                        }
                    }
                    .navigationTitle("YOLOv26 Detection")
                    .sheet(isPresented: $showingImagePicker, onDismiss: detectObjects) {
                        ImagePicker(image: $image)
                    }
                }
            }
        }
    }

    
    func detectObjects() {
        guard let image = image else { return }
        
        // Clear previous results
        detector.boxes = []
        self.processingTime = 0
        
        detector.detect(image: image) { results, time in
            // self.boxes = results // Updated via Published property
            self.processingTime = time
        }
    }
    
    func boundingBoxesView(geometry: GeometryProxy) -> some View {
        ForEach(detector.boxes) { box in
            let w = geometry.size.width
            let h = geometry.size.height
            
            let rect = box.rect
            let x = rect.minX * w
            let y = rect.minY * h
            let width = rect.width * w
            let height = rect.height * h
            
            let color = Color.color(for: box.classIndex)
            
            ZStack(alignment: .topLeading) {
                Rectangle()
                    .path(in: CGRect(x: x, y: y, width: width, height: height))
                    .stroke(color, lineWidth: 2)
                
                Text("\(box.label) \(String(format: "%.2f", box.score))")
                    .font(.system(size: 10, weight: .bold))
                    .padding(2)
                    .background(color)
                    .foregroundColor(.black)
                    .offset(x: x, y: y - 14) // Position label above box
            }
        }
    }
    
    // Draw boxes on high-res image
    func drawDetectionsOnImage(_ original: UIImage, boxes: [BoundingBox]) -> UIImage {
        let renderer = UIGraphicsImageRenderer(size: original.size)
        return renderer.image { context in
            original.draw(at: .zero)
            
            let attrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.boldSystemFont(ofSize: max(20, original.size.width / 50)),
                .foregroundColor: UIColor.white,
                .backgroundColor: UIColor.red
            ]
            
            for box in boxes {
                let color = Color.uiColor(for: box.classIndex)
                
                // Update attributes for this box
                var boxAttrs = attrs
                boxAttrs[.backgroundColor] = color
                boxAttrs[.foregroundColor] = UIColor.black
                
                context.cgContext.setLineWidth(max(2, original.size.width / 200))
                context.cgContext.setStrokeColor(color.cgColor)
                
                let rect = box.rect
                let x = rect.minX * original.size.width
                let y = rect.minY * original.size.height
                let w = rect.width * original.size.width
                let h = rect.height * original.size.height
                
                let drawRect = CGRect(x: x, y: y, width: w, height: h)
                context.cgContext.addRect(drawRect)
                context.cgContext.drawPath(using: .stroke)
                
                let label = "\(box.label) \(String(format: "%.2f", box.score))"
                let textPoint = CGPoint(x: x, y: y - max(20, original.size.width / 50))
                (label as NSString).draw(at: textPoint, withAttributes: boxAttrs)
            }
        }
    }
    
    func saveImage() {
        guard let original = image else { return }
        let result = drawDetectionsOnImage(original, boxes: detector.boxes)
        
        let av = UIActivityViewController(activityItems: [result], applicationActivities: nil)
        
        // KeyWindow lookup for SwiftUI
        if let windowScene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let rootVC = windowScene.windows.first?.rootViewController {
            rootVC.present(av, animated: true, completion: nil)
        }
    }
}

// MARK: - Color Extension
extension Color {
    static func color(for classIndex: Int) -> Color {
        let colors: [Color] = [
            .red, .green, .blue, .orange, .purple, .pink, .yellow, .cyan, .mint, .indigo, .brown, .teal
        ]
        return colors[classIndex % colors.count]
    }
    
    static func uiColor(for classIndex: Int) -> UIColor {
        let colors: [UIColor] = [
            .red, .green, .blue, .orange, .purple, .systemPink, .yellow, .cyan, .systemMint, .systemIndigo, .brown, .systemTeal
        ]
        return colors[classIndex % colors.count]
    }
}

