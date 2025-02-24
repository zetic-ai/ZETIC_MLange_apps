import SwiftUI
import AVFoundation
import Vision
import ZeticMLange

class ModelManager: ObservableObject {
    @Published var isModelReady = false
    @Published var downloadProgress: Float = 0
    @Published var model: ZeticMLangeLLMModel?
    
    func initializeModel() {
        DispatchQueue.global(qos: .background).async { [weak self] in
            if let model = ZeticMLangeLLMModel(key: "deepseek-r1-distill-qwen-1.5b-f16") {
                DispatchQueue.main.async {
                    self?.model = model
                    self?.isModelReady = true
                    self?.downloadProgress = 1.0
                }
            }
        }
    }
}

struct ContentView: View {
    @StateObject private var modelManager = ModelManager()
    @State private var messages: [Message] = []
    @State private var inputText: String = ""
    @State private var isAITyping = false
    @FocusState private var isFocused: Bool
    
    var body: some View {
        VStack(spacing: 0) {
            if !modelManager.isModelReady {
                VStack(spacing: 16) {
                    Text("Please wait while we download the model.\nThis may take a few minutes.")
                        .multilineTextAlignment(.center)
                        .foregroundColor(.gray)
                        .padding()
                }
                .padding()
            } else {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            ForEach(messages) { message in
                                ChatBubble(message: message)
                            }
                            
                            if isAITyping {
                                HStack {
                                    Text("AI is typing...")
                                        .font(.caption)
                                        .foregroundColor(.gray)
                                    Spacer()
                                }
                                .padding(.horizontal, 16)
                                .padding(.vertical, 8)
                            }
                        }
                    }
                    .onChange(of: messages) { _ in
                        withAnimation {
                            proxy.scrollTo(messages.last?.id, anchor: .bottom)
                        }
                    }
                    .simultaneousGesture(
                        TapGesture()
                            .onEnded { _ in
                                isFocused = false
                            }
                    )
                }
                
                Divider()
                
                HStack(spacing: 8) {
                    TextField("Message", text: $inputText)
                        .padding(12)
                        .background(Color(.systemGray6))
                        .cornerRadius(20)
                        .focused($isFocused)
                        .disabled(isAITyping)
                    
                    Button(action: sendMessage) {
                        Image(systemName: "arrow.up.circle.fill")
                            .font(.system(size: 24))
                            .foregroundColor(canSendMessage ? .blue : .gray)
                    }
                    .disabled(!canSendMessage)
                }
                .padding(12)
            }
        }
        .onAppear {
            modelManager.initializeModel()
        }
    }
    
    private var canSendMessage: Bool {
        !inputText.isEmpty && !isAITyping && modelManager.isModelReady
    }
    
    private func sendMessage() {
        guard canSendMessage, let model = modelManager.model else { return }
        
        let text = inputText.trimmingCharacters(in: .whitespaces)
        
        let userMessage = Message(content: inputText, isFromUser: true)
        messages.append(userMessage)
        inputText = ""
        
        isAITyping = true
        
        let aiMessage = Message(content: "", isFromUser: false)
        messages.append(aiMessage)
        
        var response = ""
        
        DispatchQueue.global().async {
            do {
                try model.run(text)
                
                while true {
                    let token = model.waitForNextToken()
                    if token.isEmpty {
                        DispatchQueue.main.async {
                            isAITyping = false
                        }
                        break
                    }
                    
                    response.append(token)
                    
                    DispatchQueue.main.async {
                        if let msgIndex = messages.firstIndex(where: { $0.id == aiMessage.id }) {
                            messages[msgIndex].content = response
                        }
                    }
                }
            } catch {
                print("Error running model: \(error)")
                DispatchQueue.main.async {
                    isAITyping = false
                }
            }
        }
    }
}
