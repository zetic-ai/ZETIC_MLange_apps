import SwiftUI

struct T5GrammarCorrectionView: View {
    @State private var inputText: String = ""
    @State private var outputText: String = ""
    @State private var isProcessing: Bool = false
    @State private var errorMessage: String? = nil
    
    // Use EnvironmentObject provided by App
    @EnvironmentObject var modelManager: T5ModelManager
    
    // Examples with many grammar errors: tense, articles, subject-verb agreement, prepositions
    private let examples = [
        // Short examples with multiple error types
        "I has a apple",
        "He go to school yesterday",
        "She don't likes it",
        "My grammar are bad",
        "I am write a letter",
        // Medium examples with mixed errors
        "The students was studying hard for they exams but they was not prepare enough",
        "Yesterday I goes to the store and buyed some apples but I forgetted to bring my wallet so I had to goes back home",
        "She don't know what to do because her friends was not there and she feeled very alone",
        "The teacher explain the lesson but the students was not listening careful and they was making noise",
        "I was trying to write a essay about my summer vacation but I was having trouble with grammar and I was not sure if I was using the right words",
        // Long examples with many errors: tense, articles, agreement, prepositions
        "When I was a child I always wanted to be a doctor because I thought it was a very important job and I wanted to help peoples but as I growed up I realized that becoming a doctor was very difficult and required many years of studying and I was not sure if I was smart enough to do it because I was not good at sciences and I was always struggled with math and chemistry classes",
        "Last summer my family and I went on a vacation to the beach and we was planning to stay there for a whole week but the weather was not very good and it was raining almost every day so we was not able to do many of the activities that we was planning to do like swimming and playing volleyball on the sand because the rain was making everything wet and muddy and we was disappointed about our vacation",
        "The book that I was reading last week was very interesting and it was about a young boy who discover a secret garden behind his house and he was spending all his free time there planting flowers and vegetables and making friends with the animals that was living in the garden but his parents was not knowing about the garden and they was worried about where he was going every day",
        "My teacher always tell us that we should practice writing every day if we want to improve our skills but I was finding it very difficult to find time to write because I was having so many other things to do like homework and chores and spending time with my friends and I was always tired after school so I was not having enough energy to write",
        "The science project that we was working on for the school fair was about how plants grow in different conditions and we was doing experiments with different types of soil and amounts of water and sunlight to see which combination would help the plants grow the best and fastest but we was making many mistakes and our plants was not growing well so we was worried that we was going to fail the project",
        // Preposition/Collocation examples
        "We are interested on partnering with your company",
        "I will contact to you once I get the result",
        "This will have an effect to our performance",
        "I'm responsible of the onboarding process",
        "We need to focus in improving reliability",
        // More examples with mixed errors: articles, tense, agreement, prepositions
        "A students in my class was always late to the school and he was not doing his homeworks because he was spending too much time on playing video games",
        "The company that I work for was planning to expand their business to the new markets but they was not having enough resources and they was struggling with finding the right peoples for the job",
        "I was very excited about going to the university because I thought it would be a great opportunity to learn new things and meet new friends but when I actually went there I was finding it very difficult to adapt to the new environment",
        "The books that I was reading last month was very interesting and they was helping me to understand more about the world and the different cultures that exist in different countries around the world",
        "My parents was always telling me that I should study hard if I want to succeed in the life but I was not listening to their advices and I was making many mistakes that I was regretting later"
    ]
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {
                    // Header
                    Text("T5 Grammar Corrector")
                        .font(.largeTitle)
                        .fontWeight(.bold)
                        .padding(.top)
                    
                    // Powered by MLange
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
                    
                    // Status Indicator
                    HStack(spacing: 6) {
                        if modelManager.isLoading {
                            if modelManager.downloadProgress > 0 {
                                VStack(spacing: 4) {
                                    ProgressView(value: modelManager.downloadProgress, total: 100)
                                        .progressViewStyle(LinearProgressViewStyle())
                                        .frame(width: 150)
                                    Text("Downloading... \(Int(modelManager.downloadProgress))%")
                                        .font(.caption)
                                        .foregroundColor(.gray)
                                }
                            } else {
                                ProgressView()
                                    .scaleEffect(0.8)
                                Text(modelManager.loadingStatus)
                                    .font(.caption)
                                    .foregroundColor(.gray)
                            }
                        } else if modelManager.isLoaded {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.green)
                            Text("Ready")
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
                VStack(alignment: .leading, spacing: 8) {
                    Text("Input Text")
                        .font(.headline)
                        .foregroundColor(.secondary)
                    
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
                }
                .padding(.horizontal)
                
                // Preset Examples
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
                                        // Clear output when selecting a new example
                                        if inputText != example {
                                            outputText = ""
                                        }
                                        inputText = example
                                    }
                                    // Light haptic feedback
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
                
                // Action Button
                Button(action: correctGrammar) {
                    HStack(spacing: 8) {
                        if isProcessing {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        }
                        Text(isProcessing ? "Correcting..." : "Correct Grammar")
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
                .animation(.easeInOut(duration: 0.2), value: isProcessing)
                
                // Output Area
                if !outputText.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.green)
                            Text("Correction")
                                .font(.headline)
                                .foregroundColor(.green)
                            
                            Spacer()
                            
                            // Copy button
                            Button(action: {
                                UIPasteboard.general.string = outputText
                                // Haptic feedback
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
                        
                        // Highlighted text with differences
                        highlightDifferences(original: inputText, corrected: outputText)
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
                
                    Spacer(minLength: 20)
                }
                .padding(.bottom, 20)
            }
            .navigationBarHidden(true)
            .onTapGesture {
                hideKeyboard()
            }
        }
    }
    
    private func correctGrammar() {
        guard !inputText.isEmpty else { return }
        
        // Store current input to prevent changes during processing
        let currentInput = inputText
        
        hideKeyboard()
        
        // Smooth state transitions
        withAnimation(.easeInOut(duration: 0.2)) {
            isProcessing = true
            errorMessage = nil
            outputText = ""
        }
        
        // Haptic Feedback
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.impactOccurred()
        
        modelManager.runInference(text: currentInput) { result in
            DispatchQueue.main.async {
                // Only update if input hasn't changed
                guard self.inputText == currentInput else { return }
                
                withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
                    switch result {
                    case .success(let text):
                        self.outputText = text
                        // Success haptic
                        let successGenerator = UINotificationFeedbackGenerator()
                        successGenerator.notificationOccurred(.success)
                    case .failure(let error):
                        self.errorMessage = error.localizedDescription
                        // Error haptic
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
    
    // Highlight differences between original and corrected text
    private func highlightDifferences(original: String, corrected: String) -> Text {
        // Split into words with punctuation preserved
        let originalWords = splitWordsPreservingPunctuation(original)
        let correctedWords = splitWordsPreservingPunctuation(corrected)
        
        // Use a more sophisticated matching algorithm
        // Create a map of original words with their positions
        var originalWordPositions: [String: [Int]] = [:]
        for (index, word) in originalWords.enumerated() {
            let normalized = normalizeWord(word)
            if !normalized.isEmpty {
                if originalWordPositions[normalized] == nil {
                    originalWordPositions[normalized] = []
                }
                originalWordPositions[normalized]?.append(index)
            }
        }
        
        var result = Text("")
        var usedOriginalIndices = Set<Int>()
        var originalIndex = 0
        
        // Match corrected words with original words
        for (corrIndex, corrWord) in correctedWords.enumerated() {
            let corrNormalized = normalizeWord(corrWord)
            var isMatched = false
            
            // Try to match with current position in original
            if originalIndex < originalWords.count {
                let origNormalized = normalizeWord(originalWords[originalIndex])
                if origNormalized == corrNormalized && !usedOriginalIndices.contains(originalIndex) {
                    // Exact match at current position
                    result = result + Text(corrWord + (corrIndex < correctedWords.count - 1 ? " " : ""))
                        .foregroundColor(.primary)
                    usedOriginalIndices.insert(originalIndex)
                    originalIndex += 1
                    isMatched = true
                }
            }
            
            // If not matched, try to find the word elsewhere in original
            if !isMatched && !corrNormalized.isEmpty {
                if let positions = originalWordPositions[corrNormalized] {
                    for pos in positions {
                        if !usedOriginalIndices.contains(pos) {
                            // Check if position is reasonable (not too far)
                            let distance = abs(pos - originalIndex)
                            if distance <= 10 { // Allow some flexibility
                                result = result + Text(corrWord + (corrIndex < correctedWords.count - 1 ? " " : ""))
                                    .foregroundColor(.primary)
                                usedOriginalIndices.insert(pos)
                                // Update originalIndex to continue from matched position
                                if pos >= originalIndex {
                                    originalIndex = pos + 1
                                }
                                isMatched = true
                                break
                            }
                        }
                    }
                }
            }
            
            // If still not matched, it's a new/changed word - highlight it
            if !isMatched {
                result = result + Text(corrWord + (corrIndex < correctedWords.count - 1 ? " " : ""))
                    .foregroundColor(.green)
                    .fontWeight(.bold)
                    .underline()
            }
        }
        
        return result
    }
    
    // Split text into words preserving punctuation
    private func splitWordsPreservingPunctuation(_ text: String) -> [String] {
        return text.components(separatedBy: .whitespacesAndNewlines).filter { !$0.isEmpty }
    }
    
    // Normalize word for comparison (lowercase, remove punctuation)
    private func normalizeWord(_ word: String) -> String {
        return word.lowercased()
            .trimmingCharacters(in: .punctuationCharacters)
    }
}

struct T5GrammarCorrectionView_Previews: PreviewProvider {
    static var previews: some View {
        T5GrammarCorrectionView()
    }
}
