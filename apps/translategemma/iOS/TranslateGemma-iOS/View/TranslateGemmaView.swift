import SwiftUI
import Vision
import VisionKit

struct TranslateGemmaView: View {
    @State private var inputText: String = ""
    @State private var outputText: String = ""
    @State private var isProcessing: Bool = false
    @State private var errorMessage: String? = nil
    
    // Target Language Selection
    @State private var selectedLanguage: String = "Korean"
    private let supportedLanguages = ["Korean", "English", "Japanese", "Chinese", "French", "Spanish", "German", "Italian"]
    
    // Camera / Photo Library States
    @State private var showCamera = false
    @State private var showPhotoLibrary = false
    @State private var selectedImage: UIImage?
    
    // Use EnvironmentObject provided by App
    @EnvironmentObject var modelManager: TranslateGemmaModelManager
    
    // Translation Examples (Raw text)
    private let examples = [
        "Hello, how are you?",
        "Where is the nearest station?",
        "I would like a coffee please.",
        "The weather is beautiful today.",
        "Can you help me find my hotel?",
        "What is your name?",
        "I am a developer.",
        "This food is delicious."
    ]
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {
                    headerView
                    statusIndicator
                    languageSelectionSection
                    inputSection
                    examplesSection
                    actionButton
                    outputSection
                    errorSection
                    Spacer(minLength: 20)
                }
                .padding(.bottom, 20)
            } // End ScrollView
            .navigationBarHidden(true)
            .onTapGesture {
                hideKeyboard()
            }
            .onChange(of: modelManager.currentPartialOutput) { newValue in
                if isProcessing && !newValue.isEmpty {
                    outputText = newValue
                }
            }
            .sheet(isPresented: $showCamera) {
                ImagePicker(selectedImage: $selectedImage, isPresented: $showCamera, sourceType: .camera)
            }
            .sheet(isPresented: $showPhotoLibrary) {
                ImagePicker(selectedImage: $selectedImage, isPresented: $showPhotoLibrary, sourceType: .photoLibrary)
            }
            .onChange(of: selectedImage) { newImage in
                if let image = newImage {
                    recognizeText(from: image)
                }
            }
        }
    }
    
    // MARK: - Subviews
    
    private var headerView: some View {
        VStack(spacing: 4) {
            Text("Translate Gemma")
                .font(.largeTitle)
                .fontWeight(.bold)
                .padding(.top)
            
            VStack(spacing: 4) {
                Text("Powered by")
                    .font(.caption2)
                    .foregroundColor(.secondary)
                Text("MLange")
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(.blue)
            }
            .padding(.top, -10)
            .padding(.bottom, 5)
        }
    }
    
    private var statusIndicator: some View {
        HStack(spacing: 6) {
            if modelManager.isLoading {
                ProgressView()
                    .scaleEffect(0.8)
                Text(modelManager.loadingStatus)
                    .font(.caption)
                    .foregroundColor(.gray)
            } else if modelManager.isLoaded {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundColor(.green)
                Text("Ready")
                    .font(.caption)
                    .foregroundColor(.green)
            } else if modelManager.lastError != nil {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundColor(.red)
                Text("Load Failed")
                    .font(.caption)
                    .foregroundColor(.red)
            }
        }
        .padding(.bottom, 5)
    }
    
    private var languageSelectionSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Target Language")
                .font(.headline)
                .foregroundColor(.secondary)
                .padding(.horizontal)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(supportedLanguages, id: \.self) { language in
                        Button(action: {
                            withAnimation {
                                selectedLanguage = language
                            }
                            let generator = UISelectionFeedbackGenerator()
                            generator.selectionChanged()
                        }) {
                            Text(language)
                                .font(.subheadline)
                                .fontWeight(selectedLanguage == language ? .bold : .medium)
                                .padding(.horizontal, 16)
                                .padding(.vertical, 10)
                                .background(
                                    selectedLanguage == language ? Color.blue : Color.gray.opacity(0.1)
                                )
                                .foregroundColor(selectedLanguage == language ? .white : .primary)
                                .cornerRadius(20)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 20)
                                        .stroke(Color.blue.opacity(0.3), lineWidth: selectedLanguage == language ? 0 : 1)
                                )
                        }
                    }
                }
                .padding(.horizontal)
            }
        }
    }
    
    private var inputSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Input Text")
                    .font(.headline)
                    .foregroundColor(.secondary)
                
                Spacer()
                
                // Camera / Photo Buttons
                HStack(spacing: 16) {
                    Button(action: {
                        showCamera = true
                    }) {
                        Image(systemName: "camera")
                            .font(.system(size: 20))
                            .foregroundColor(.blue)
                    }
                    
                    Button(action: {
                        showPhotoLibrary = true
                    }) {
                        Image(systemName: "photo")
                            .font(.system(size: 20))
                            .foregroundColor(.blue)
                    }
                }
            }
            .padding(.horizontal)
            
            TextEditor(text: Binding(
                get: { inputText },
                set: { newValue in
                    // Prevent editing while processing
                    guard !isProcessing else { return }
                    
                    // Clear output when input changes
                    if newValue != inputText && !outputText.isEmpty {
                        withAnimation {
                            outputText = ""
                        }
                    }
                    inputText = newValue
                }
            ))
                .frame(minHeight: 100, maxHeight: 200)
                .padding(8)
                .background(isProcessing ? Color(UIColor.systemGray5) : Color(UIColor.systemGray6))
                .cornerRadius(12)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(isProcessing ? Color.gray.opacity(0.2) : Color.gray.opacity(0.3), lineWidth: 1)
                )
                .scrollContentBackground(.hidden)
                .disabled(isProcessing)
                .opacity(isProcessing ? 0.6 : 1.0)
                .padding(.horizontal)
        }
    }
    
    private var examplesSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Examples")
                .font(.headline)
                .foregroundColor(.secondary)
                .padding(.horizontal)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(examples, id: \.self) { example in
                        Button(action: {
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                                if inputText != example {
                                    outputText = ""
                                }
                                inputText = example
                            }
                            let generator = UISelectionFeedbackGenerator()
                            generator.selectionChanged()
                        }) {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(example)
                                    .font(.subheadline)
                                    .fontWeight(.medium)
                                    .lineLimit(2)
                                    .multilineTextAlignment(.leading)
                                    .fixedSize(horizontal: false, vertical: true)
                                    .foregroundColor(.primary)
                            }
                            .padding(.horizontal, 14)
                            .padding(.vertical, 10)
                            .frame(maxWidth: 300, alignment: .leading)
                            .background(
                                LinearGradient(
                                    gradient: Gradient(colors: [
                                        Color.blue.opacity(0.08),
                                        Color.blue.opacity(0.12)
                                    ]),
                                    startPoint: .topLeading,
                                    endPoint: .bottomTrailing
                                )
                            )
                            .foregroundColor(.blue)
                            .cornerRadius(14)
                            .overlay(
                                RoundedRectangle(cornerRadius: 14)
                                    .stroke(Color.blue.opacity(0.25), lineWidth: 1.5)
                            )
                            .shadow(color: Color.blue.opacity(0.1), radius: 4, x: 0, y: 2)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
                .padding(.horizontal)
            }
        }
    }
    
    private var actionButton: some View {
        Button(action: runInference) {
            HStack(spacing: 8) {
                if isProcessing {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                }
                Text(isProcessing ? "Translating..." : "Translate")
                    .font(.headline)
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background(
                (inputText.isEmpty || !modelManager.isLoaded || isProcessing) 
                ? Color.gray.opacity(0.6) 
                : Color.blue
            )
            .foregroundColor(.white)
            .cornerRadius(14)
            .shadow(color: (inputText.isEmpty || !modelManager.isLoaded || isProcessing) ? .clear : .blue.opacity(0.3), radius: 8, x: 0, y: 4)
        }
        .disabled(isProcessing || inputText.isEmpty || !modelManager.isLoaded)
        .padding(.horizontal)
    }
    
    private var outputSection: some View {
        Group {
            if !outputText.isEmpty {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.green)
                        Text("Translation")
                            .font(.headline)
                            .foregroundColor(.green)
                        
                        Spacer()
                        
                        Button(action: {
                            UIPasteboard.general.string = outputText
                            let generator = UINotificationFeedbackGenerator()
                            generator.notificationOccurred(.success)
                        }) {
                            HStack(spacing: 4) {
                                Image(systemName: "doc.on.doc")
                                    .font(.caption)
                                Text("Copy")
                                    .font(.caption)
                                    .fontWeight(.medium)
                            }
                            .foregroundColor(.blue)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color.blue.opacity(0.1))
                            .cornerRadius(8)
                        }
                    }
                    
                    Text(outputText)
                        .font(.title3)
                        .padding()
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.green.opacity(0.1))
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color.green.opacity(0.3), lineWidth: 1)
                        )
                }
                .padding(.horizontal)
                .transition(.asymmetric(
                    insertion: .move(edge: .bottom).combined(with: .opacity),
                    removal: .opacity
                ))
            }
        }
    }
    
    private var errorSection: some View {
        Group {
            if let error = errorMessage {
                HStack {
                    Image(systemName: "exclamationmark.circle.fill")
                        .foregroundColor(.red)
                    Text(error)
                        .foregroundColor(.red)
                        .font(.caption)
                }
                .padding()
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.red.opacity(0.1))
                .cornerRadius(8)
                .padding(.horizontal)
                .transition(.opacity)
            }
        }
    }
    
    private func runInference() {
        guard !inputText.isEmpty else { return }
        
        // Capture current input state
        let currentInput = inputText
        
        // Construct Prompt with Target Language
        let prompt = "Translate to \(selectedLanguage): \(inputText)"
        
        hideKeyboard()
        
        withAnimation(.easeInOut(duration: 0.2)) {
            isProcessing = true
            errorMessage = nil
            outputText = ""
        }
        
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.impactOccurred()
        
        modelManager.runInference(text: prompt) { result in
            DispatchQueue.main.async {
                // Ignore result if input has changed
                guard self.inputText == currentInput else { return }
                
                withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
                    switch result {
                    case .success(let text):
                        self.outputText = text
                        let successGenerator = UINotificationFeedbackGenerator()
                        successGenerator.notificationOccurred(.success)
                    case .failure(let error):
                        self.errorMessage = error.localizedDescription
                        let errorGenerator = UINotificationFeedbackGenerator()
                        errorGenerator.notificationOccurred(.error)
                    }
                    self.isProcessing = false
                }
            }
        }
    }
    
    private func hideKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }
    
    private func recognizeText(from image: UIImage) {
        guard let cgImage = image.cgImage else { return }
        
        let request = VNRecognizeTextRequest { request, error in
            guard let observations = request.results as? [VNRecognizedTextObservation] else { return }
            
            let recognizedStrings = observations.compactMap { observation in
                observation.topCandidates(1).first?.string
            }
             
            let fullText = recognizedStrings.joined(separator: " ")
            
            DispatchQueue.main.async {
                self.inputText = fullText
            }
        }
        
        request.recognitionLevel = .accurate
        let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
        do {
            try handler.perform([request])
        } catch {
            print("Failed to perform text recognition request: \(error)")
            DispatchQueue.main.async {
                self.errorMessage = "Failed to extract text from image."
            }
        }
    }
}

// MARK: - ImageHelper
struct ImagePicker: UIViewControllerRepresentable {
    @Binding var selectedImage: UIImage?
    @Binding var isPresented: Bool
    var sourceType: UIImagePickerController.SourceType = .camera
    
    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.delegate = context.coordinator
        picker.sourceType = sourceType
        picker.allowsEditing = false
        return picker
    }
    
    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    class Coordinator: NSObject, UINavigationControllerDelegate, UIImagePickerControllerDelegate {
        let parent: ImagePicker
        
        init(_ parent: ImagePicker) {
            self.parent = parent
        }
        
        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey : Any]) {
            if let image = info[.originalImage] as? UIImage {
                parent.selectedImage = image
            }
            parent.isPresented = false
        }
        
        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.isPresented = false
        }
    }
}

struct TranslateGemmaView_Previews: PreviewProvider {
    static var previews: some View {
        TranslateGemmaView()
    }
}
