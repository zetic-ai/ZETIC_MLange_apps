import SwiftUI
import ZeticMLange

struct ContentView: View {
    private let networkMonitor = NetworkMonitor.shared
    
    @State private var selectedView: Int? = nil
    @State private var showAlert = false
    
    @State private var featureItems: [FeatureItem] = [
        FeatureItem(name: "Object Detection", modelKeys: ["yolo-v8n-test"]),
        FeatureItem(name: "Face Detection", modelKeys: ["face_detection_short_range"]),
        FeatureItem(name: "Face Emotion Recognition", modelKeys: ["face_detection_short_range", "face_landmark", "face_emotion_recognition"]),
        FeatureItem(name: "Face Landmark Detection", modelKeys: ["face_detection_short_range", "face_landmark"]),
        FeatureItem(name: "Automatic Speech Recognition", modelKeys: ["whisper-tiny-encoder", "whisper-tiny-decoder"]),
        FeatureItem(name: "Sound Classification", modelKeys: ["YAMNet"])
    ]
    
    var body: some View {
        NavigationView {
            List {
                ForEach(0..<featureItems.count, id: \.self) { index in
                    NavigationLink(
                        destination: LazyView(destinationView(for: index)),
                        tag: index,
                        selection: $selectedView
                    ) {
                        FeatureItemView(featureItem: featureItems[index])
                    }
                    .buttonStyle(PlainButtonStyle())
                    .onChange(of: selectedView) { newValue in
                        if newValue == index {
                            handleNavigation(index)
                        }
                    }
                }
            }
            .navigationBarTitle("ZeticMLange")
            .alert("Network Error", isPresented: $showAlert) {
                Button("OK", role: .cancel) { }
            } message: {
                Text("Please check your network connection.")
            }
        }
    }
    
    private func handleNavigation(_ index: Int) {
        if featureItems[index].modelStatus == .ready {
            return
        } else if !networkMonitor.isConnected {
            showAlert = true
            selectedView = nil
            return
        } else if featureItems[index].modelStatus == .notReady {
            selectedView = nil
            featureItems[index].modelStatus = .fetching
            DispatchQueue.global().async {
                featureItems[index].modelKeys.forEach {
                    let _ = ZeticMLangeModel($0)
                }
                DispatchQueue.main.async {
                    UserDefaults.standard.set(true, forKey: featureItems[index].name)
                    featureItems[index].modelStatus = .ready
                }
            }
        } else {
            selectedView = nil
        }
    }
    
    private func destinationView(for index: Int) -> some View {
        switch index {
        case 0:
            return AnyView(YOLOv8View())
        case 1:
            return AnyView(FaceDetectionView())
        case 2:
            return AnyView(FaceEmotionRecognitionView())
        case 3:
            return AnyView(FaceLandmarkView())
        case 4:
            return AnyView(WhisperView())
        case 5:
            return AnyView(YAMNetView())
        default:
            return AnyView(EmptyView())
        }
    }
}
