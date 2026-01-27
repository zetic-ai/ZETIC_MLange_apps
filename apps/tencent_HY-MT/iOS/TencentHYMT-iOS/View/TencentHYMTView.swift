import SwiftUI

struct TencentHYMTView: View {
    @StateObject private var model = TencentHYMT()
    
    @State private var inputMessage: String = ""
    @State private var chatHistory: [ChatMessage] = []
    
    @State private var selectedSourceLang: Language
    @State private var selectedTargetLang: Language
    
    // Auto-scroll
    @Namespace var bottomID
    
    init() {
        // Defaults: English -> Korean
        let source = Languages.list.first(where: { $0.name == "English" })!
        let target = Languages.list.first(where: { $0.name == "Korean" })!
        _selectedSourceLang = State(initialValue: source)
        _selectedTargetLang = State(initialValue: target)
    }
    
    var body: some View {
        VStack(spacing: 0) {
            // Header / Status
            VStack(spacing: 4) {
                HStack {
                    Text("Tencent HY-MT")
                        .font(.largeTitle)
                        .fontWeight(.heavy)
                    Spacer()
                    Text(model.loadingStatus)
                        .font(.caption)
                        .foregroundColor(model.isLoaded ? Color(red: 0x34/255, green: 0xA9/255, blue: 0xA3/255) : .gray)
                }
                
                Text("Powered by MLange")
                    .font(.title3)
                    .fontWeight(.bold)
                    .foregroundColor(Color(red: 0x34/255, green: 0xA9/255, blue: 0xA3/255))
                    .frame(maxWidth: .infinity, alignment: .center)
            }
            .padding()
            .background(Color(.systemGray6))
            
            // Language Selectors
            HStack {
                VStack(alignment: .leading) {
                    Text("Source")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Picker("Source", selection: $selectedSourceLang) {
                        ForEach(Languages.list, id: \.self) { lang in
                            Text(lang.name).tag(lang)
                        }
                    }
                    .pickerStyle(MenuPickerStyle())
                }
                
                Button(action: {
                    let temp = selectedSourceLang
                    selectedSourceLang = selectedTargetLang
                    selectedTargetLang = temp
                }) {
                    Image(systemName: "arrow.left.arrow.right")
                        .foregroundColor(Color(red: 0x34/255, green: 0xA9/255, blue: 0xA3/255))
                }
                
                VStack(alignment: .leading) {
                    Text("Target")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    Picker("Target", selection: $selectedTargetLang) {
                        ForEach(Languages.list, id: \.self) { lang in
                            Text(lang.name).tag(lang)
                        }
                    }
                    .pickerStyle(MenuPickerStyle())
                }
            }
            .padding()
            
            Divider()
            
            // Chat Area
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 12) {
                        ForEach(chatHistory) { message in
                            ChatBubble(message: message)
                        }
                        
                        // Streaming bubble
                        if model.isGenerating && !model.currentOutput.isEmpty {
                            ChatBubble(message: ChatMessage(content: model.currentOutput, isUser: false))
                        }
                        
                        Spacer()
                            .frame(height: 1)
                            .id(bottomID)
                    }
                    .padding()
                }
                .onChange(of: model.currentOutput) { _ in
                    proxy.scrollTo(bottomID, anchor: .bottom)
                }
                .onChange(of: chatHistory.count) { _ in
                    proxy.scrollTo(bottomID, anchor: .bottom)
                }
                .onChange(of: model.isGenerating) { isGenerating in
                    if !isGenerating {
                        finalizeMessage()
                    }
                }
            }
            
            // Input Area
            HStack {
                TextField("Type a message...", text: $inputMessage)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .disabled(!model.isLoaded || model.isGenerating)
                
                Button(action: sendMessage) {
                    Image(systemName: "paperplane.fill")
                        .resizable()
                        .frame(width: 20, height: 20)
                        .padding(10)
                }
                .disabled(!model.isLoaded || model.isGenerating)
            }
            .padding()
            .background(Color(.systemGray6))
        }
        .onAppear {
            model.loadModel()
        }
    }
    
    func sendMessage() {
        guard !inputMessage.isEmpty else { return }
        
        // 1. Add User Message
        let userMsg = ChatMessage(content: inputMessage, isUser: true)
        chatHistory.append(userMsg)
        
        let inputText = inputMessage
        inputMessage = ""
        
        // 2. Prepare Prompt
        // "Translate the following segment from ${sourceLang.name} into ${targetLang.name}, without additional explanation. \n$inputText"
        let prompt = "Translate the following segment from \(selectedSourceLang.name) into \(selectedTargetLang.name), without additional explanation. \n\(inputText)"
        
        // 3. Run Inference
        model.run(prompt: prompt, source: selectedSourceLang, target: selectedTargetLang)
    }
    
    // To append the final result to chatHistory when generation ends
    func finalizeMessage() {
        if !model.currentOutput.isEmpty {
            let modelMsg = ChatMessage(content: model.currentOutput, isUser: false)
            chatHistory.append(modelMsg)
            // Optional: clear currentOutput if we want, but likely TencentModel clears it on next run
        }
    }
}


// MARK: - Models for View
struct ChatMessage: Identifiable {
    let id = UUID()
    let content: String
    let isUser: Bool
}

// MARK: - Subviews
struct ChatBubble: View {
    let message: ChatMessage
    
    var body: some View {
        HStack {
            if message.isUser {
                Spacer()
                Text(message.content)
                    .padding()
                    .background(Color(red: 0x34/255, green: 0xA9/255, blue: 0xA3/255))
                    .foregroundColor(.white)
                    .cornerRadius(16)
                    .contextMenu {
                        Button(action: {
                            UIPasteboard.general.string = message.content
                        }) {
                            Text("Copy")
                            Image(systemName: "doc.on.doc")
                        }
                    }
            } else {
                Text(message.content)
                    .padding()
                    .background(Color(.systemGray5))
                    .foregroundColor(.primary)
                    .cornerRadius(16)
                    .contextMenu {
                        Button(action: {
                            UIPasteboard.general.string = message.content
                        }) {
                            Text("Copy")
                            Image(systemName: "doc.on.doc")
                        }
                    }
                Spacer()
            }
        }
    }
}
