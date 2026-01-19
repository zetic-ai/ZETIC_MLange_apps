//
//  AnonymizerService.swift
//  TextAnonymizer
//
//  Service to handle text anonymization using Zetic MLange SDK
//

import Foundation
import ZeticMLange
import Combine
import CoreML

class AnonymizerViewModel: ObservableObject {
    @Published var anonymizedText = ""
    @Published var isProcessing = false
    @Published var showingError = false
    @Published var errorMessage = ""
    @Published var isModelLoaded = false
    
    private var model: ZeticMLangeModel?
    private let modelMaxLength = 128
    private var lastInputBytes: [UInt8] = []
    private var lastInputByteCount: Int = 0
    private let classLabels = [
        "O",
        "EMAIL",
        "PHONE_NUMBER",
        "CREDIT_CARD_NUMBER",
        "SSN",
        "NRP",
        "PERSON",
        "ADDRESS",
        "LOCATION",
        "DATE",
        "OTHER"
    ]
    private let placeholderByLabel: [String: String] = [
        "O": "[Text]",
        "EMAIL": "[Email]",
        "PHONE_NUMBER": "[Phone number]",
        "CREDIT_CARD_NUMBER": "[Credit card]",
        "SSN": "[SSN]",
        "NRP": "[NRP]",
        "PERSON": "[Person]",
        "ADDRESS": "[Address]",
        "LOCATION": "[Location]",
        "DATE": "[Date]",
        "OTHER": "[Sensitive]"
    ]
    
    private let modelName: String
    private let modelVersion: Int
    private let accessToken: String

    init() {
        // Read configuration from environment to avoid committing secrets.
        let env = ProcessInfo.processInfo.environment
        modelName = env["ZETIC_MODEL_NAME"] ?? "your-org/your-model"
        modelVersion = Int(env["ZETIC_MODEL_VERSION"] ?? "") ?? 1
        accessToken = env["ZETIC_ACCESS_TOKEN"] ?? ""

        // Load model asynchronously to avoid blocking UI
        loadModelAsync()
    }
    
    private func loadModelAsync() {
        print("🔄 Starting model loading...")
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self = self else { return }
            
            do {
                print("📦 Attempting to load Zetic MLange model...")
                guard !accessToken.isEmpty else {
                    throw NSError(
                        domain: "TextAnonymizer",
                        code: 1001,
                        userInfo: [NSLocalizedDescriptionKey: "Missing access token. Set ZETIC_ACCESS_TOKEN in your scheme environment."]
                    )
                }

                print("   Model: \(modelName)")
                print("   Version: \(modelVersion)")

                // Load Zetic MLange model with a token provided at runtime.
                let loadedModel = try ZeticMLangeModel(
                    tokenKey: accessToken,
                    name: modelName,
                    version: modelVersion
                )
                
                print("✅ Model instance created successfully")
                
                DispatchQueue.main.async {
                    self.model = loadedModel
                    self.isModelLoaded = true
                    print("✅ Model loaded and ready to use")
                }
            } catch {
                let errorDescription = error.localizedDescription
                print("❌ Model loading failed!")
                print("   Error: \(errorDescription)")
                print("   Error type: \(type(of: error))")
                
                DispatchQueue.main.async {
                    self.isModelLoaded = false
                    self.errorMessage = "Failed to load model: \(errorDescription)\n\nPossible causes:\n• Invalid token or authentication failed\n• Network connection issue\n• Model download timeout\n• Model not found\n\nCheck Xcode console for details."
                    self.showingError = true
                }
            }
        }
    }
    
    func anonymizeText(_ text: String) {
        guard !text.isEmpty else { return }
        guard let model = model else {
            errorMessage = "Model not loaded. Please restart the app."
            showingError = true
            return
        }
        
        isProcessing = true
        anonymizedText = ""
        
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self = self else { return }
            
            do {
                // Convert text to tensor input
                // The model expects 1 input tensor containing the text
                print("📝 Converting text to tensor...")
                print("   Input text: \(text)")
                
                // Create tensors from text string
                // Try different integer data types based on model requirements
                let candidateDataTypes: [BuiltinDataType] = [.uint8, .int32]
                var outputs: [Tensor] = []
                var lastError: Error?
                
                // Method 1: Try creating tensor from string directly
                // This is the most common approach for text models
                if !text.isEmpty {
                    // Convert text to bytes, then to tensor
                    // Note: You may need to adjust this based on ZeticMLange's actual Tensor API
                    // Common approaches:
                    // - Tensor from data
                    // - Tensor from string
                    // - Tensor from token IDs
                    
                    // Try creating tensor - adjust based on ZeticMLange API
                    // Example: inputTensor = Tensor(data: textData, shape: [1, textData.count])
                    // Or: inputTensor = Tensor(string: text)
                    
                    // For now, we'll try a common pattern
                    // You may need to check ZeticMLange docs for exact method
                    for dataType in candidateDataTypes {
                        do {
                            let inputs = try self.createInputTensorsFromText(text, dataType: dataType)
                            print("✅ Created input tensors. Count: \(inputs.count)")
                            outputs = try self.runModel(model, inputs: inputs)
                            lastError = nil
                            break
                        } catch {
                            lastError = error
                            print("⚠️ Inference failed with dataType \(dataType.rawValue): \(error.localizedDescription)")
                        }
                    }
                } else {
                    throw NSError(domain: "TextAnonymizer", code: 1, userInfo: [NSLocalizedDescriptionKey: "Failed to convert text to UTF-8 data"])
                }
                
                if let lastError = lastError {
                    throw lastError
                }
                print("✅ Model inference completed. Outputs count: \(outputs.count)")
                
                // Process outputs to get anonymized text
                // Extract the result from the output tensors
                // The exact method depends on ZeticMLange's Tensor API
                
                DispatchQueue.main.async {
                    self.isProcessing = false
                    
                    // Try to extract text from outputs
                    // Adjust this based on your model's output format
                    if let outputTensor = outputs.first {
                        // Attempt to extract string from tensor
                        // You may need to adjust this based on ZeticMLange's API
                        if let result = self.extractStringFromTensor(outputTensor) {
                            self.anonymizedText = result
                        } else {
                            // Fallback: regex-based masking on the original input
                            self.anonymizedText = self.maskSensitiveText(text)
                        }
                    } else {
                        self.errorMessage = "Model returned no output. Please check model configuration."
                        self.showingError = true
                    }
                }
            } catch {
                DispatchQueue.main.async {
                    self.isProcessing = false
                    self.errorMessage = "Anonymization failed: \(error.localizedDescription)\n\nPlease ensure:\n1. The model is correctly loaded\n2. Input format matches model requirements\n3. You have internet connection for initial model download"
                    self.showingError = true
                }
            }
        }
    }
    
    // Helper function to tokenize text (basic implementation)
    // For RoBERTa-based models, we need to tokenize text into token IDs
    // This is a simplified tokenizer - for production, use a proper RoBERTa tokenizer
    private func tokenizeTextWithMask(_ text: String, maxLength: Int) -> (tokenIds: [Int], attentionMask: [Int]) {
        // Basic tokenization: split by whitespace and punctuation
        // For production, you'd use the actual RoBERTa tokenizer
        // This is a placeholder that creates simple token IDs
        
        // Simple word-level tokenization
        let words = text.components(separatedBy: .whitespacesAndNewlines)
            .filter { !$0.isEmpty }
        
        // Convert words to simple token IDs (hash-based for now)
        // In production, use actual RoBERTa tokenizer vocabulary
        var tokenIds: [Int] = []
        
        // Add special tokens: [CLS] = 0, [SEP] = 2 (RoBERTa convention)
        tokenIds.append(0) // [CLS] token
        
        // Convert words to token IDs (simplified)
        for word in words.prefix(maxLength - 2) { // Reserve space for [CLS] and [SEP]
            // Simple hash-based token ID (not accurate, but works for testing)
            let tokenId = abs(word.hashValue) % 50000 + 1 // Map to reasonable range
            tokenIds.append(tokenId)
        }
        
        tokenIds.append(2) // [SEP] token
        
        // Build attention mask before padding (1 for real tokens, 0 for padding)
        let realTokenCount = tokenIds.count
        var attentionMask = Array(repeating: 1, count: realTokenCount)
        
        // Pad or truncate to maxLength
        if tokenIds.count < maxLength {
            let paddingCount = maxLength - tokenIds.count
            tokenIds.append(contentsOf: Array(repeating: 1, count: paddingCount)) // Pad with [PAD] = 1
            attentionMask.append(contentsOf: Array(repeating: 0, count: paddingCount))
        } else if tokenIds.count > maxLength {
            tokenIds = Array(tokenIds.prefix(maxLength))
            attentionMask = Array(attentionMask.prefix(maxLength))
        }
        
        return (tokenIds, attentionMask)
    }
    
    // Helper function to create input tensors from text
    // Creates input_ids and attention_mask tensors
    private func createInputTensorsFromText(_ text: String, dataType: BuiltinDataType) throws -> [Tensor] {
        print("🔤 Tokenizing text...")
        
        let maxLength = modelMaxLength
        let shape: [Int] = [1, maxLength]
        let inputIdsTensor: Tensor
        let attentionMaskTensor: Tensor

        let rawBytes = Array(text.utf8.prefix(maxLength))
        lastInputBytes = rawBytes + Array(repeating: 0, count: max(0, maxLength - rawBytes.count))
        lastInputByteCount = rawBytes.count

        switch dataType {
        case .uint8, .int8:
            let (bytes, attentionMask, byteCount) = bytesWithMask(text, maxLength: maxLength)
            print("   Byte count: \(bytes.count) (raw: \(byteCount))")
            print("   First 10 bytes: \(Array(bytes.prefix(10)))")
            inputIdsTensor = try createByteTensor(from: bytes, shape: shape, label: "input_ids", dataType: dataType)
            attentionMaskTensor = try createByteTensor(from: attentionMask, shape: shape, label: "attention_mask", dataType: dataType)
        default:
            let (tokenIds, attentionMask) = tokenizeTextWithMask(text, maxLength: maxLength)
            print("   Token count: \(tokenIds.count)")
            print("   First 10 tokens: \(Array(tokenIds.prefix(10)))")
            inputIdsTensor = try createIntegerTensor(from: tokenIds, shape: shape, label: "input_ids", dataType: dataType)
            attentionMaskTensor = try createIntegerTensor(from: attentionMask, shape: shape, label: "attention_mask", dataType: dataType)
        }
        
        // Note: If you add more required inputs (e.g., token_type_ids), include them here.
        return [inputIdsTensor, attentionMaskTensor]
    }

    private func createIntegerTensor(from values: [Int], shape: [Int], label: String, dataType: BuiltinDataType) throws -> Tensor {
        switch dataType {
        case .int64:
            let int64Values = values.map { Int64($0) }
            let data = int64Values.withUnsafeBufferPointer { Data(buffer: $0) }
            return try createTensor(from: data, dataType: .int64, shape: shape, label: label)
        case .int32:
            let int32Values = values.map { Int32($0) }
            let data = int32Values.withUnsafeBufferPointer { Data(buffer: $0) }
            return try createTensor(from: data, dataType: .int32, shape: shape, label: label)
        default:
            throw NSError(domain: "TextAnonymizer", code: 5, userInfo: [NSLocalizedDescriptionKey: "Unsupported integer data type for \(label): \(dataType.rawValue)"])
        }
    }

    private func createByteTensor(from values: [UInt8], shape: [Int], label: String, dataType: BuiltinDataType) throws -> Tensor {
        switch dataType {
        case .uint8:
            let data = values.withUnsafeBufferPointer { Data(buffer: $0) }
            return try createTensor(from: data, dataType: .uint8, shape: shape, label: label)
        case .int8:
            let int8Values = values.map { Int8(bitPattern: $0) }
            let data = int8Values.withUnsafeBufferPointer { Data(buffer: $0) }
            return try createTensor(from: data, dataType: .int8, shape: shape, label: label)
        default:
            throw NSError(domain: "TextAnonymizer", code: 7, userInfo: [NSLocalizedDescriptionKey: "Unsupported byte data type for \(label): \(dataType.rawValue)"])
        }
    }

    private func createTensor(from data: Data, dataType: BuiltinDataType, shape: [Int], label: String) throws -> Tensor {
        let tensor = Tensor(data: data, dataType: dataType, shape: shape)
        print("✅ Created Tensor for \(label) with dataType: \(dataType.rawValue)")
        return tensor
    }

    private func bytesWithMask(_ text: String, maxLength: Int) -> ([UInt8], [UInt8], Int) {
        let rawBytes = Array(text.utf8)
        let bytes = Array(rawBytes.prefix(maxLength))
        var values = bytes
        let paddingCount = maxLength - values.count
        if paddingCount > 0 {
            values.append(contentsOf: Array(repeating: 0, count: paddingCount))
        }
        let attentionMask = Array(repeating: UInt8(1), count: bytes.count) +
            Array(repeating: UInt8(0), count: max(0, paddingCount))
        return (values, attentionMask, bytes.count)
    }

    private func runModel(_ model: ZeticMLangeModel, inputs: [Tensor]) throws -> [Tensor] {
        print("🚀 Running model inference...")
        do {
            return try model.run(inputs: inputs)
        } catch {
            let errorText = error.localizedDescription
            // Some models only expect a single input_ids tensor.
            if (errorText.contains("input_ids") || errorText.contains("expected: 1")) && inputs.count > 1 {
                print("⚠️ Model reported missing input_ids. Retrying with input_ids only.")
                return try model.run(inputs: [inputs[0]])
            } else if errorText.contains("attention_mask") && inputs.count == 1 {
                print("⚠️ Model reported missing attention_mask. Retrying with input_ids + attention_mask.")
                return try model.run(inputs: inputs)
            }
            throw error
        }
    }
    
    // Helper function to extract string from tensor
    // IMPORTANT: Adjust this based on ZeticMLange's actual Tensor API
    private func extractStringFromTensor(_ tensor: Tensor) -> String? {
        print("📤 Extracting string from output tensor...")
        print("   Output shape: \(tensor.shape)")
        print("   Output dataType: \(tensor.dataType)")
        
        if let builtin = tensor.dataType as? BuiltinDataType {
            switch builtin {
            case .uint8, .int8:
                let bytes = [UInt8](tensor.data)
                if let stringValue = String(bytes: bytes, encoding: .utf8) {
                    return stringValue.trimmingCharacters(in: .controlCharacters)
                }
            case .float32:
                if let masked = maskBytesUsingLogits(tensor) {
                    return masked
                }
            default:
                break
            }
        } else if tensor.data.count % MemoryLayout<Float32>.size == 0, tensor.shape.count == 3 {
            if let masked = maskBytesUsingLogits(tensor) {
                return masked
            }
        }
        
        if let stringValue = String(data: tensor.data, encoding: .utf8) {
            return stringValue.trimmingCharacters(in: .controlCharacters)
        }
        
        print("⚠️ Tensor extraction not implemented for this output type. Check model output format.")
        return nil
    }

    private func maskSensitiveText(_ text: String) -> String {
        var result = text

        let patterns: [(pattern: String, placeholder: String)] = [
            (#"(?:[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,})"#, "[Email]"),
            (#"(?:\+?\d{1,2}[\s.-]?)?(?:\(?\d{3}\)?[\s.-]?)\d{3}[\s.-]?\d{4}"#, "[Phone number]"),
            (#"\b(?:\d[ -]*?){13,16}\b"#, "[Credit card]"),
            (#"\b\d{3}-\d{2}-\d{4}\b"#, "[SSN]"),
            (#"\b(?:NRP|nrp)\b"#, "[NRP]")
        ]

        for (pattern, placeholder) in patterns {
            if let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) {
                let range = NSRange(result.startIndex..., in: result)
                result = regex.stringByReplacingMatches(in: result, options: [], range: range, withTemplate: placeholder)
            }
        }

        // Try to mask names mentioned explicitly (e.g., "my name is jack") to cover cases where the model doesn't tag PERSON.
        let namePatterns: [(pattern: String, placeholder: String)] = [
            (#"\b(?:my name is|name is|i am|i'm|this is)\s+([A-Za-z]{2,})\b"#, "[Person]"),
            (#"\b(?:call me|i go by)\s+([A-Za-z]{2,})\b"#, "[Person]")
        ]

        for (pattern, placeholder) in namePatterns {
            if let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) {
                let matches = regex.matches(in: result, range: NSRange(result.startIndex..., in: result))
                for match in matches.reversed() {
                    guard match.numberOfRanges >= 2 else { continue }
                    let nameRange = match.range(at: 1)
                    guard let textRange = Range(nameRange, in: result) else { continue }
                    result.replaceSubrange(textRange, with: placeholder)
                }
            }
        }

        return result
    }

    private func maskBytesUsingLogits(_ tensor: Tensor) -> String? {
        guard tensor.shape.count == 3 else { return nil }
        let seqLen = tensor.shape[1]
        let classCount = tensor.shape[2]
        let expectedCount = seqLen * classCount
        let floatCount = tensor.data.count / MemoryLayout<Float32>.size
        guard floatCount >= expectedCount else { return nil }
        guard !lastInputBytes.isEmpty else { return nil }

        let floats = tensor.data.withUnsafeBytes { raw -> [Float32] in
            Array(raw.bindMemory(to: Float32.self))
        }

        let limit = min(lastInputByteCount, seqLen, lastInputBytes.count)
        let labels = classLabels

        var labelCounts: [String: Int] = [:]
        var spans: [(start: Int, end: Int, label: String, score: Float32)] = []

        var currentLabel: String?
        var currentStart = 0
        var currentScore: Float32 = 0

        for i in 0..<limit {
            let start = i * classCount
            var maxIndex = 0
            var maxValue = floats[start]
            for c in 1..<classCount {
                let v = floats[start + c]
                if v > maxValue {
                    maxValue = v
                    maxIndex = c
                }
            }

            let label = maxIndex < labels.count ? labels[maxIndex] : "UNKNOWN"
            labelCounts[label, default: 0] += 1

            if label == "O" {
                if let openLabel = currentLabel {
                    spans.append((currentStart, i, openLabel, currentScore))
                    currentLabel = nil
                }
                continue
            }

            if label != currentLabel {
                if let openLabel = currentLabel {
                    spans.append((currentStart, i, openLabel, currentScore))
                }
                currentLabel = label
                currentStart = i
                currentScore = maxValue
            } else {
                currentScore = max(currentScore, maxValue)
            }
        }

        if let openLabel = currentLabel {
            spans.append((currentStart, limit, openLabel, currentScore))
        }

        print("📊 Entity label counts: \(labelCounts)")
        if spans.isEmpty {
            print("📊 No entity spans detected.")
            logTopPredictions(floats: floats, seqLen: seqLen, classCount: classCount, labels: labels, limit: min(16, limit))
            return nil
        }

        for span in spans.prefix(10) {
            print("📌 Span \(span.start)-\(span.end) label=\(span.label) score=\(span.score)")
        }

        let text = String(bytes: lastInputBytes.prefix(limit), encoding: .utf8) ?? ""
        let masked = applyPlaceholders(text, spans: spans)
        return masked.trimmingCharacters(in: .controlCharacters)
    }

    private func applyPlaceholders(_ text: String, spans: [(start: Int, end: Int, label: String, score: Float32)]) -> String {
        guard !spans.isEmpty else { return text }

        let utf8 = Array(text.utf8)
        var replacements: [(range: Range<String.Index>, placeholder: String)] = []

        for span in spans {
            guard let placeholder = placeholderByLabel[span.label] else { continue }
            let start = min(span.start, utf8.count)
            let end = min(span.end, utf8.count)
            guard start < end else { continue }

            let startIndex = String.Index(utf16Offset: start, in: text)
            let endIndex = String.Index(utf16Offset: end, in: text)
            replacements.append((startIndex..<endIndex, placeholder))
        }

        if replacements.isEmpty { return text }

        // Apply from end to avoid offset shifts
        let sorted = replacements.sorted { $0.range.lowerBound > $1.range.lowerBound }
        var result = text
        for repl in sorted {
            result.replaceSubrange(repl.range, with: repl.placeholder)
        }
        return result
    }

    private func logTopPredictions(floats: [Float32], seqLen: Int, classCount: Int, labels: [String], limit: Int) {
        let maxPos = min(seqLen, limit)
        print("🧪 Raw model output preview (top-3 per position):")
        for i in 0..<maxPos {
            let start = i * classCount
            guard start + classCount <= floats.count else { break }
            var scored: [(idx: Int, score: Float32)] = []
            scored.reserveCapacity(classCount)
            for c in 0..<classCount {
                scored.append((c, floats[start + c]))
            }
            let top3 = scored.sorted { $0.score > $1.score }.prefix(3)
            let entries = top3.map { item -> String in
                let label = item.idx < labels.count ? labels[item.idx] : "C\(item.idx)"
                return "\(label):\(String(format: "%.3f", item.score))"
            }
            print("  [\(i)] \(entries.joined(separator: ", "))")
        }
    }
}

