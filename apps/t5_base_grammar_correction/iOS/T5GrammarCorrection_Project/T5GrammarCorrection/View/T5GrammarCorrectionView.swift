import SwiftUI

struct T5GrammarCorrectionView: View {
    @State private var inputText: String = ""
    @State private var outputText: String = ""
    @State private var isProcessing: Bool = false
    @State private var errorMessage: String? = nil
    
    // Use EnvironmentObject provided by App
    @EnvironmentObject var modelManager: T5ModelManager
    
    // Examples verified in Python
    private let examples = [
        "I has a apple",
        "He go to school yesterday",
        "She don't likes it",
        "My grammar are bad",
        "I am write a letter"
    ]
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                // Header
                Text("T5 Grammar Corrector")
                    .font(.largeTitle)
                    .fontWeight(.bold)
                    .padding(.top)
                
                // Status Indicator
                HStack {
                    if modelManager.isLoading {
                        ProgressView()
                            .scaleEffect(0.8)
                        Text("Loading Model...")
                            .font(.caption)
                            .foregroundColor(.gray)
                    } else if modelManager.isLoaded {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.green)
                        Text("Model Ready (FP16 Robust)")
                            .font(.caption)
                            .foregroundColor(.green)
                    } else if let error = modelManager.lastError {
                        Image(systemName: "exclamationmark.triangle.fill")
                            .foregroundColor(.red)
                        Text("Load Failed")
                            .font(.caption)
                            .foregroundColor(.red)
                    }
                }
                .padding(.bottom, 5)
                
                // Input Area
                VStack(alignment: .leading) {
                    Text("Input Text")
                        .font(.headline)
                        .foregroundColor(.secondary)
                    
                    TextEditor(text: $inputText)
                        .frame(height: 100)
                        .padding(8)
                        .background(Color(UIColor.systemGray6))
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color.gray.opacity(0.3), lineWidth: 1)
                        )
                }
                .padding(.horizontal)
                
                // Preset Examples
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(examples, id: \.self) { example in
                            Button(action: {
                                inputText = example
                            }) {
                                Text(example)
                                    .padding(.horizontal, 12)
                                    .padding(.vertical, 8)
                                    .background(Color.blue.opacity(0.1))
                                    .foregroundColor(.blue)
                                    .cornerRadius(20)
                            }
                        }
                    }
                    .padding(.horizontal)
                }
                
                // Action Button
                Button(action: correctGrammar) {
                    HStack {
                        if isProcessing {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                .padding(.trailing, 5)
                        }
                        Text(isProcessing ? "Correcting..." : "Correct Grammar")
                            .font(.headline)
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background((inputText.isEmpty || !modelManager.isLoaded || isProcessing) ? Color.gray : Color.blue) // GREY when processing
                    .foregroundColor(.white)
                    .cornerRadius(14)
                    .shadow(radius: 2)
                }
                .disabled(isProcessing || inputText.isEmpty || !modelManager.isLoaded) // CLICK BLOCKED when processed
                .padding(.horizontal)
                
                // Output Area
                if !outputText.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Correction")
                            .font(.headline)
                            .foregroundColor(.green)
                        
                        Text(outputText)
                            .font(.title3)
                            .fontWeight(.medium)
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
                    .transition(.opacity)
                }
                
                if let error = errorMessage {
                    Text(error)
                        .foregroundColor(.red)
                        .font(.caption)
                        .padding()
                        .background(Color.red.opacity(0.1))
                        .cornerRadius(8)
                }
                
                Spacer()
            }
            .navigationBarHidden(true)
            .onTapGesture {
                hideKeyboard()
            }
        }
    }
    
    private func correctGrammar() {
        guard !inputText.isEmpty else { return }
        
        hideKeyboard()
        isProcessing = true
        errorMessage = nil
        outputText = ""
        
        // Haptic Feedback
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.impactOccurred()
        
        modelManager.runInference(text: inputText) { result in
            withAnimation {
                switch result {
                case .success(let text):
                    self.outputText = text
                case .failure(let error):
                    self.errorMessage = error.localizedDescription
                }
                self.isProcessing = false
            }
        }
    }
    
    private func hideKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }
}

struct T5GrammarCorrectionView_Previews: PreviewProvider {
    static var previews: some View {
        T5GrammarCorrectionView()
    }
}
