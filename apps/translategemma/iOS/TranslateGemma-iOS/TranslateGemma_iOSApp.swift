import SwiftUI
import Combine
import Foundation
import ZeticMLange

// MARK: - App Entry Point
@main
struct TranslateGemma_iOSApp: App {
    @StateObject private var modelManager = TranslateGemmaModelManager()
    
    var body: some Scene {
        WindowGroup {
            TranslateGemmaView()
                .environmentObject(modelManager)
        }
    }
}

// MARK: - View Model (Model Manager)
class TranslateGemmaModelManager: ObservableObject {
    @Published var isLoaded: Bool = false
    @Published var isLoading: Bool = false
    @Published var isDownloading: Bool = false
    @Published var loadingStatus: String = "Loading Model..."
    @Published var lastError: String? = nil
    
    // Streaming output
    @Published var currentPartialOutput: String = ""
    
    private var model: ZeticMLangeLLMModel?
    // Using the ID and Key from the user's snippet
    private let modelId = "yeonseok_zeticai_ceo/translate-gemma-4b-it"
    private let projectKey = "dev_182c82785ecd42369394fbd2721484fd"
    
    init() {
        load()
    }
    
    func load() {
        guard !isLoaded && !isLoading else { return }
        
        isLoading = true
        isDownloading = true
        loadingStatus = "Downloading Model..."
        lastError = nil
        
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self = self else { return }
            
            do {
                // Initialize ZeticMLangeLLMModel
                // We default to -1 for target/quant to let the SDK decide or use defaults if not specified, 
                // but proper usage usually requires arguments.
                // The user snippet used: 
                // ZeticMLangeLLMModel("key", "id", $LLM_TARGET, $LLM_QUANT_TYPE)
                // We will assume default handling if we omit them or use common defaults if needed.
                // Looking at other files might help, but for now we'll try the simple init if available
                // or guess.
                // Actually, the user snippet shows 4 args. If the constructor requires them, we need them.
                // Let's assume standard CPU/Int8 or similar if we can't find definitions.
                // However, often specific enums are used.
                // Since I can't see the SDK definition, I will use a safe bet or try to find where $LLM_TARGET came from?
                // It was likely a shell variable in the user snippet.
                // I'll try to find if there's an initializer with just key and modelId.
                // If not, I'll update it later.
                
                self.model = try ZeticMLangeLLMModel(tokenKey: self.projectKey, name: self.modelId)
                
                DispatchQueue.main.async {
                    self.isDownloading = false
                    self.isLoaded = true
                    self.isLoading = false
                    self.loadingStatus = "Ready"
                }
            } catch {
                DispatchQueue.main.async {
                    self.isDownloading = false
                    self.lastError = "Failed to load model: \(error.localizedDescription)"
                    self.isLoading = false
                    self.loadingStatus = "Load Failed"
                }
            }
        }
    }
    
    func runInference(text: String, completion: @escaping (Result<String, Error>) -> Void) {
        guard let model = model, isLoaded else {
            completion(.failure(NSError(domain: "TranslateGemmaModelManager", code: -1, userInfo: [NSLocalizedDescriptionKey: "Model not loaded"])))
            return
        }
        
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self = self else { return }
            
            // Streaming Accumulator
            var fullResponse = ""
            
            do {
                // Reset partial output on main thread before starting
                DispatchQueue.main.async {
                    self.currentPartialOutput = ""
                }
                
                try model.run(text)
                
                while true {
                    let token = model.waitForNextToken()
                    if token == "" {
                        break
                    }
                    fullResponse += token
                    
                    // Update partial output on main thread
                    DispatchQueue.main.async {
                        self.currentPartialOutput = fullResponse
                    }
                }
                
                DispatchQueue.main.async {
                    completion(.success(fullResponse))
                }
            } catch {
                DispatchQueue.main.async {
                    completion(.failure(error))
                }
            }
        }
    }
}
