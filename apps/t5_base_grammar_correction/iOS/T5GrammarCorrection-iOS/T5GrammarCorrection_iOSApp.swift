import SwiftUI
import Combine
import Foundation
import ZeticMLange

// MARK: - App Entry Point
@main
struct T5GrammarCorrection_iOSApp: App {
    @StateObject private var modelManager = T5ModelManager()
    
    var body: some Scene {
        WindowGroup {
            T5GrammarCorrectionView()
                .environmentObject(modelManager)
        }
    }
}

// MARK: - View Model (Model Manager)
class T5ModelManager: ObservableObject {
    @Published var isLoaded: Bool = false
    @Published var isLoading: Bool = false
    @Published var isDownloading: Bool = false
    @Published var loadingStatus: String = "Loading Model..."
    @Published var lastError: String? = nil
    
    let handler = ModelHandler()
    
    init() {
        // Load on init (App Launch)
        load()
    }
    
    func load() {
        guard !isLoaded && !isLoading else { return }
        
        isLoading = true
        isDownloading = true
        loadingStatus = "Downloading Model..."
        lastError = nil
        
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            let startTime = Date()
            do {
                try self?.handler.loadModel()
                let elapsed = Date().timeIntervalSince(startTime)
                
                DispatchQueue.main.async {
                    // If it took more than 2 seconds, it was likely a download
                    if elapsed > 2.0 {
                        // Was downloading
                        self?.isDownloading = false
                        self?.isLoaded = true
                        self?.isLoading = false
                        self?.loadingStatus = "Ready"
                    } else {
                        // Quick load (model already cached)
                        self?.isDownloading = false
                        self?.isLoaded = true
                        self?.isLoading = false
                        self?.loadingStatus = "Ready"
                    }
                }
            } catch {
                DispatchQueue.main.async {
                    self?.isDownloading = false
                    self?.lastError = "Failed to load model: \(error.localizedDescription)"
                    self?.isLoading = false
                    self?.loadingStatus = "Load Failed"
                }
            }
        }
    }
    
    func runInference(text: String, completion: @escaping (Result<String, Error>) -> Void) {
        if !isLoaded {
             if isLoading {
                 completion(.failure(NSError(domain: "T5ModelManager", code: -1, userInfo: [NSLocalizedDescriptionKey: "Model is still loading..."])))
                 return
             }
        }
        
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self = self else { return }
            do {
                let result = try self.handler.run(text: text)
                DispatchQueue.main.async {
                    completion(.success(result))
                }
            } catch {
                DispatchQueue.main.async {
                    completion(.failure(error))
                }
            }
        }
    }
}

// MARK: - Model Logic
class ModelHandler {
    private var model: ZeticMLangeModel?
    private let tokenizer = Tokenizer()
    
    // Model keys provided by user
    private let modelName = "Team_ZETIC/t5-base-grammar-correction"
    private let privateTokenKey = "YOUR_PERSONAL_ACCESS_TOKEN"
    private let modelVersion = 3
    
    func loadModel() throws {
        // Check tokenizer first
        if !tokenizer.vocabLoaded {
            throw NSError(domain: "ModelHandler", code: -3, userInfo: [NSLocalizedDescriptionKey: "Tokenizer vocab not loaded. Please check t5_vocab.json is included in Bundle Resources."])
        }
        
        if model == nil {
            model = try ZeticMLangeModel(tokenKey: privateTokenKey, name: modelName, version: modelVersion)
        }
    }
    
    func run(text: String) throws -> String {
        guard let model = model else {
            try loadModel()
            if model == nil {
                throw NSError(domain: "ModelHandler", code: -1, userInfo: [NSLocalizedDescriptionKey: "Model not loaded"])
            }
            return try run(text: text) // Retry once
        }
        
        // 1. Tokenize Input
        let prompt = "grammar: " + text 
        let inputIdsInt64 = tokenizer.encode(prompt)
        
        guard !inputIdsInt64.isEmpty else {
            throw NSError(domain: "ModelHandler", code: -2, userInfo: [NSLocalizedDescriptionKey: "Tokenizer failed to encode input. Vocab may not be loaded."])
        }
        
        var inputIds = inputIdsInt64.map { Int32($0) }
        
        let fixedEncoderLength = 1024
        if inputIds.count > fixedEncoderLength {
            inputIds = Array(inputIds.prefix(fixedEncoderLength))
        } else {
            let padCount = fixedEncoderLength - inputIds.count
            inputIds += Array(repeating: Int32(0), count: padCount)
        }
        
        // Generate mask - Must match Python exactly
        let attentionMask = inputIds.map { $0 == 0 ? Int32(0) : Int32(1) }
        
        // 2. Prepare Fixed Buffer for Decoder
        let decoderStartTokenId: Int32 = 0
        let eosTokenId: Int32 = 1
        let fixedDecoderLength = 128
        
        // Buffer: [Start, Pad, Pad...]
        var decoderInputIds: [Int32] = Array(repeating: 0, count: fixedDecoderLength)
        decoderInputIds[0] = decoderStartTokenId
        
        // CRITICAL FIX: Track generated_ids separately like Python
        var generatedIds: [Int32] = []
        
        for step in 0..<(fixedDecoderLength - 1) {
            
            // Prepare Tensors (Full Buffer sent every time)
            let inputIdsData = toData(inputIds)
            let attentionMaskData = toData(attentionMask)
            let decoderInputIdsData = toData(decoderInputIds)
            
            let inputShape = [1, fixedEncoderLength]
            let decoderShape = [1, fixedDecoderLength]
            
            // CORRECT SPEC ORDER (Based on 'inspect_new_model.py'):
            // 1. input_ids
            // 2. attention_mask
            // 3. decoder_input_ids
            
            let inputs: [Tensor] = [
                Tensor(data: inputIdsData, dataType: BuiltinDataType.int32, shape: inputShape),
                Tensor(data: attentionMaskData, dataType: BuiltinDataType.int32, shape: inputShape),
                Tensor(data: decoderInputIdsData, dataType: BuiltinDataType.int32, shape: decoderShape)
            ]
            
            // Sync Run
            let outputs = try model.run(inputs: inputs)
            
            guard let logitsTensor = outputs.first else { break }
            
            let allLogits = fromFloatData(logitsTensor.data)
            
            var nextToken: Int32 = 0
            
            // LOGIT EXTRACTION LOGIC (From Python)
            // Output is [1, 128, Vocab] -> We need logits at index 'step'
            // Python: logits[0, step, :] -> flattened index = step * vocabSize
            let actualVocabSize = allLogits.count / (1 * 128)
            let vocabSize = actualVocabSize
            let targetIndex = step 
            let startIndex = targetIndex * vocabSize
            let endIndex = startIndex + vocabSize
            
            if endIndex <= allLogits.count {
                // Find Argmax in this slice
                var maxVal = -Float.infinity
                var maxIdx = 0
                
                for i in startIndex..<endIndex {
                    let val = allLogits[i]
                    if val > maxVal {
                        maxVal = val
                        maxIdx = i - startIndex
                    }
                }
                nextToken = Int32(maxIdx)
            } else {
                // Logits size error
                break
            }
            
            // Add to generated_ids like Python
            generatedIds.append(nextToken)
            
            if nextToken == eosTokenId {
                break
            }
            
            // UPDATE BUFFER IN-PLACE for next iteration
            decoderInputIds[step + 1] = nextToken
        }
        
        // 4. Decode Output - Use generatedIds like Python
        let resultText = tokenizer.decode(generatedIds.map { Int64($0) })
        return resultText
    }
    
    private func toData(_ array: [Int32]) -> Data {
        return array.withUnsafeBufferPointer { Data(buffer: $0) }
    }
    
    private func fromData(_ data: Data) -> [Int32] {
        return data.withUnsafeBytes {
            Array($0.bindMemory(to: Int32.self))
        }
    }
    
    private func fromFloatData(_ data: Data) -> [Float] {
        return data.withUnsafeBytes {
            Array($0.bindMemory(to: Float.self))
        }
    }
}

