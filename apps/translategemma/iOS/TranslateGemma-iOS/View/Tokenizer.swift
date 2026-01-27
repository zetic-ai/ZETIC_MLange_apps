import Foundation

/// A placeholder Tokenizer. In a real app, implement SentencePiece or use a library.
/// T5 uses SentencePiece (spiece.model).
class Tokenizer {
    private var idToToken: [Int: String] = [:]
    private var tokenToId: [String: Int] = [:]
    private var isVocabLoaded: Bool = false
    
    init() {
        loadVocab()
        isVocabLoaded = !idToToken.isEmpty
        if !isVocabLoaded {
            print("❌ WARNING: Tokenizer initialized but vocab is empty!")
        }
    }
    
    var vocabLoaded: Bool {
        return isVocabLoaded
    }
    
    private func loadVocab() {
        // Try multiple possible locations
        var url: URL?
        
        // Try main bundle first
        url = Bundle.main.url(forResource: "t5_vocab", withExtension: "json")
        
        // Try in View subdirectory
        if url == nil {
            if let viewBundlePath = Bundle.main.path(forResource: "t5_vocab", ofType: "json", inDirectory: "View") {
                url = URL(fileURLWithPath: viewBundlePath)
            }
        }
        
        // Try direct path search
        if url == nil {
            if let bundlePath = Bundle.main.resourcePath {
                let possiblePaths = [
                    "\(bundlePath)/t5_vocab.json",
                    "\(bundlePath)/View/t5_vocab.json",
                    "\(bundlePath)/TranslateGemma-iOS/View/t5_vocab.json"
                ]
                for path in possiblePaths {
                    if FileManager.default.fileExists(atPath: path) {
                        url = URL(fileURLWithPath: path)
                        break
                    }
                }
            }
        }
        
        guard let vocabUrl = url else {
            return
        }
        
        do {
            let data = try Data(contentsOf: vocabUrl)
            guard let json = try JSONSerialization.jsonObject(with: data, options: []) as? [String: Any] else {
                print("Tokenizer: Failed to parse JSON")
                return
            }
            
            // New format has metadata and vocab dict
            if let vocabDict = json["vocab"] as? [String: String] {
                // New format with metadata
                for (key, value) in vocabDict {
                    if let id = Int(key) {
                        idToToken[id] = value
                        tokenToId[value] = id
                    }
                }
                print("Tokenizer: Loaded \(idToToken.count) tokens.")
            } else if let vocabDict = json as? [String: String] {
                // Old format - direct vocab dict
                for (key, value) in vocabDict {
                    if let id = Int(key) {
                        idToToken[id] = value
                        tokenToId[value] = id
                    }
                }
                print("Tokenizer: Loaded \(idToToken.count) tokens.")
            }
            
        } catch {
            print("Tokenizer: Failed to load vocab - \(error)")
        }
    }
    
    // Optimized "Split & Merge" Encode for T5
    // T5 SentencePiece tokenizer simulation
    // This is a simplified version that tries to match Python tokenizer behavior
    func encode(_ text: String) -> [Int64] {
        // Check if vocab is loaded
        guard !tokenToId.isEmpty else {
            return []
        }
        
        var ids: [Int64] = []
        var remaining = text
        var isFirstToken = true
        
        // Process text, handling spaces correctly
        // T5 SentencePiece: spaces are NOT tokens, they're represented by ▁ prefix on following word
        while !remaining.isEmpty {
            // Skip leading whitespace (it becomes part of the next token's ▁ prefix)
            if remaining.first?.isWhitespace == true {
                remaining.removeFirst()
                isFirstToken = false // After space, next token is not first
                continue
            }
            
            var matchFound = false
            var bestMatch: (id: Int, length: Int)? = nil
            
            // Try to find the longest matching token starting from current position
            let maxCheckLen = min(remaining.count, 50) // Limit check length for performance
            
            for len in stride(from: maxCheckLen, through: 1, by: -1) {
                let candidate = String(remaining.prefix(len))
                
                // T5 SentencePiece: Most tokens have ▁ prefix
                // First token after text start might not have prefix, but words after space do
                if isFirstToken {
                    // First token - try without prefix first, then with prefix
                    if let id = tokenToId[candidate] {
                        bestMatch = (id, len)
                        break
                    }
                    let withPrefix = "\u{2581}" + candidate
                    if let id = tokenToId[withPrefix] {
                        bestMatch = (id, len)
                        break
                    }
                } else {
                    // Non-first token (after space) - try with ▁ prefix first (T5 standard)
                    let withPrefix = "\u{2581}" + candidate
                    if let id = tokenToId[withPrefix] {
                        bestMatch = (id, len)
                        break
                    }
                    // Also try without prefix (for punctuation like :, ., etc.)
                    if let id = tokenToId[candidate] {
                        bestMatch = (id, len)
                        break
                    }
                }
            }
            
            if let match = bestMatch {
                ids.append(Int64(match.id))
                remaining.removeFirst(match.length)
                matchFound = true
                isFirstToken = false
            }
            
            if !matchFound {
                // Unknown character - use UNK token
                // This shouldn't happen often with proper vocab
                ids.append(2) // <unk>
                remaining.removeFirst()
                isFirstToken = false
            }
        }
        
        // CRITICAL: Python tokenizer with padding="max_length" DOES add EOS
        // Check Python: tokenizer(text, padding="max_length", truncation=True) adds EOS token (1)
        // We need to add EOS for consistency with Python behavior
        // But only if we're going to pad (not if we're truncating)
        // For now, add EOS to match Python behavior
        ids.append(1) // EOS token
        return ids
    }

    func decode(_ tokens: [Int64]) -> String {
        var result = ""
        for (index, id) in tokens.enumerated() {
            if id == 1 { break } // EOS
            if let token = idToToken[Int(id)] {
                // T5 Token: "▁word" (U+2581 word) should be converted to " word"
                // Replace U+2581 (▁) with space
                var partial = token.replacingOccurrences(of: "\u{2581}", with: " ")
                
                // If it's the first token and starts with space, keep it
                // Otherwise, just append
                if index == 0 && partial.hasPrefix(" ") {
                    // First token with space prefix - keep as is
                } else if index > 0 && !partial.hasPrefix(" ") {
                    // Add space before non-space-prefixed tokens (except first)
                    partial = " " + partial
                }
                
                result += partial
            }
        }
        // Remove leading/trailing whitespace and normalize
        return result.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

