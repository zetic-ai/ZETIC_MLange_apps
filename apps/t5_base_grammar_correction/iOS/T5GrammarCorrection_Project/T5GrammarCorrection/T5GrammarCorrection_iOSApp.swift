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
    @Published var lastError: String? = nil
    
    let handler = ModelHandler()
    
    init() {
        // Load on init (App Launch)
        load()
    }
    
    func load() {
        guard !isLoaded && !isLoading else { return }
        
        isLoading = true
        lastError = nil
        
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            do {
                try self?.handler.loadModel()
                DispatchQueue.main.async {
                    self?.isLoaded = true
                    self?.isLoading = false
                    print("T5ModelManager: Model loaded successfully.")
                }
            } catch {
                DispatchQueue.main.async {
                    self?.lastError = "Failed to load model: \(error.localizedDescription)"
                    self?.isLoading = false
                    print("T5ModelManager: Error loading model - \(error)")
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
    private let modelName = "Team_ZETIC/t5-grammar-correction"
    private let mlangeKey = "YOUR_MLANGE_KEY"
    private let modelVersion = 1
    
    func loadModel() throws {
        if model == nil {
            print("Loading model: \(modelName)...")
            // USE RUN_ACCURACY (High Precision/CPU Preference) based on user req/Python findings
            model = try ZeticMLangeModel(tokenKey: mlangeKey, name: modelName, version: modelVersion, modelMode: ModelMode.RUN_ACCURACY)
            print("Model loaded successfully.")
        }
    }
    
    func run(text: String) throws -> String {
        print("ModelHandler: run() called with text: '\(text)'")
        guard let model = model else {
            print("ModelHandler: Model is nil, attempting to load...")
            try loadModel()
            if model == nil {
                throw NSError(domain: "ModelHandler", code: -1, userInfo: [NSLocalizedDescriptionKey: "Model not loaded"])
            }
            return try run(text: text) // Retry once
        }
        
        // 1. Tokenize Input
        let prompt = "grammar: " + text 
        let inputIdsInt64 = tokenizer.encode(prompt)
        var inputIds = inputIdsInt64.map { Int32($0) }
        
        let fixedEncoderLength = 1024
        if inputIds.count > fixedEncoderLength {
            inputIds = Array(inputIds.prefix(fixedEncoderLength))
        } else {
            let padCount = fixedEncoderLength - inputIds.count
            inputIds += Array(repeating: Int32(0), count: padCount)
        }
        
        // Generate mask
        let attentionMask = inputIds.map { $0 == 0 ? Int32(0) : Int32(1) }
        
        // 2. Prepare Fixed Buffer for Decoder
        // MATCHING PYTHON LOGIC EXACTLY: "inference.py"
        let decoderStartTokenId: Int32 = 0
        let eosTokenId: Int32 = 1
        let fixedDecoderLength = 128
        
        // Buffer: [Start, Pad, Pad...]
        var decoderInputIds: [Int32] = Array(repeating: 0, count: fixedDecoderLength)
        decoderInputIds[0] = decoderStartTokenId
        
        print("ModelHandler: Starting decoding loop (Fixed Buffer \(fixedDecoderLength))...")
        
        for step in 0..<(fixedDecoderLength - 1) {
            
            // Prepare Tensors (Full Buffer sent every time)
            let inputIdsData = toData(inputIds)
            let attentionMaskData = toData(attentionMask)
            let decoderInputIdsData = toData(decoderInputIds)
            
            let inputShape = [1, fixedEncoderLength]
            let decoderShape = [1, fixedDecoderLength]
            
            // REORDERING INPUTS TO ALPHABETICAL ORDER
            // CoreML often mandates Alphabetical order for Array inputs if names are not explicit.
            // 1. attention_mask
            // 2. decoder_input_ids
            // 3. input_ids
            
            let inputs: [Tensor] = [
                Tensor(data: attentionMaskData, dataType: BuiltinDataType.int32, shape: inputShape),
                Tensor(data: decoderInputIdsData, dataType: BuiltinDataType.int32, shape: decoderShape),
                Tensor(data: inputIdsData, dataType: BuiltinDataType.int32, shape: inputShape)
            ]
            
            // Sync Run
            let outputs = try model.run(inputs: inputs)
            
            guard let logitsTensor = outputs.first else { break }
            let allLogits = fromFloatData(logitsTensor.data)
            
            var nextToken: Int32 = 0
            
            // LOGIT EXTRACTION LOGIC (From Python)
            // Output is [1, 128, Vocab] -> We need logits at index 'step'
            let vocabSize = 32128
            let targetIndex = step 
            let startIndex = targetIndex * vocabSize
            let endIndex = startIndex + vocabSize
            
            if endIndex <= allLogits.count {
                // Slice for this step
                // Find Argmax in this slice manually
                var maxVal = -Float.infinity
                var maxIdx = 0
                
                // Optimized loop (stride is 1)
                for i in startIndex..<endIndex {
                    let val = allLogits[i]
                    if val > maxVal {
                        maxVal = val
                        maxIdx = i - startIndex
                    }
                }
                nextToken = Int32(maxIdx)
            } else {
                print("ModelHandler: Logits size error or OOB.")
            }
            
            if nextToken == eosTokenId {
                print("ModelHandler: EOS reached.")
                break
            }
            
            // UPDATE BUFFER IN-PLACE
            decoderInputIds[step + 1] = nextToken
        }
        
        // 4. Decode Output
        let outputTokens = decoderInputIds.filter { $0 != decoderStartTokenId }.map { Int64($0) }
        print("ModelHandler: Output tokens: \(outputTokens)")
        let resultText = tokenizer.decode(outputTokens)
        print("ModelHandler: Result text: '\(resultText)'")
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
