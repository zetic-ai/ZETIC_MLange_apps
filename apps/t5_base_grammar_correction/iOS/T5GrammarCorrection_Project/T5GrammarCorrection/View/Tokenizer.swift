import Foundation

/// A placeholder Tokenizer. In a real app, implement SentencePiece or use a library.
/// T5 uses SentencePiece (spiece.model).
class Tokenizer {
    private var idToToken: [Int: String] = [:]
    private var tokenToId: [String: Int] = [:]
    
    init() {
        loadVocab()
    }
    
    private func loadVocab() {
        guard let url = Bundle.main.url(forResource: "t5_vocab", withExtension: "json") else {
            print("Tokenizer: t5_vocab.json not found in Bundle.")
            return
        }
        
        do {
            let data = try Data(contentsOf: url)
            let json = try JSONSerialization.jsonObject(with: data, options: []) as? [String: String] // Keys are usually strings in JSON
            
            // JSON keys are Strings "0", "1"... convert to Int
            if let json = json {
                for (key, value) in json {
                    if let id = Int(key) {
                        idToToken[id] = value
                        tokenToId[value] = id
                    }
                }
            }
            print("Tokenizer: Loaded \(idToToken.count) tokens.")
            
            // DEBUG: Verify critical tokens
            if let id = tokenToId["grammar"] { print("DEBUG: 'grammar' ID = \(id)") } else { print("DEBUG: 'grammar' NOT FOUND") }
            if let id = tokenToId["\u{2581}He"] { print("DEBUG: ' He' ID = \(id)") } else { print("DEBUG: ' He' NOT FOUND") }
            
        } catch {
            print("Tokenizer: Failed to load vocab - \(error)")
        }
    }
    
    // Optimized "Split & Merge" Encode for T5
    // 1. Split by space to get rough word boundaries.
    // 2. Prepend '▁' (\u{2581}) to non-start words.
    // 3. For each chunk, use greedy longest-prefix-match.
    func encode(_ text: String) -> [Int64] {
        var ids: [Int64] = []
        
        let words = text.split(separator: " ", omittingEmptySubsequences: false)
        for (i, wordSub) in words.enumerated() {
            var tokenString = String(wordSub)
            
            // T5 SentencePiece: words (except start) starting with space get the underscore prefix
            if i > 0 {
                tokenString = "\u{2581}" + tokenString
            }
            
            // Greedily consume this token string
            var remaining = tokenString
            // print("Tokenizer: Processing chunk '\(remaining)'") // Uncomment for deep debug
            while !remaining.isEmpty {
                var matchFound = false
                // Optimization: Check full string first (very common)
                if let id = tokenToId[remaining] {
                    ids.append(Int64(id))
                    // print("  -> Matched '\(remaining)' as \(id)")
                    remaining = ""
                    matchFound = true
                } else {
                    // Binary search or stride? Stride is fine for short fragments (words)
                    // limit max length check to remaining count
                    let maxLen = remaining.count
                    // We can cap at 20 or 30 if vocab max len is known, but maxLen is safe for words.
                    for len in stride(from: maxLen, through: 1, by: -1) {
                        let prefix = String(remaining.prefix(len))
                        if let id = tokenToId[prefix] {
                            ids.append(Int64(id))
                            remaining.removeFirst(len)
                            matchFound = true
                            break
                        }
                    }
                }
                
                if !matchFound {
                    // Unknown char fallback
                    // In real SP, backoff or byte fallback. Here, just UNK.
                    // Advance 1 char to avoid infinite loop
                    remaining.removeFirst()
                    ids.append(2) // <unk>
                }
            }
        }
        
        ids.append(1) // EOS
        return ids
    }

    func decode(_ tokens: [Int64]) -> String {
        var result = ""
        for (index, id) in tokens.enumerated() {
            if id == 1 { break } // EOS
            if let token = idToToken[Int(id)] {
                // T5 Token: " word" (U+2581 word) or "word"
                // We assume standard T5 spiece.model behavior
                var partial = token.replacingOccurrences(of: "\u{2581}", with: " ")
                
                // Fallback for literal escaped
                if partial.contains("\\u2581") {
                    partial = partial.replacingOccurrences(of: "\\\\u2581", with: " ")
                    partial = partial.replacingOccurrences(of: "\\u2581", with: " ")
                }

                result += partial
            }
        }
        
        var clean = result.trimmingCharacters(in: .whitespacesAndNewlines)
        
        // Post-Processing for Punctuation (Common in T5 detokenization)
        // Fix "word , word" -> "word, word"
        clean = clean.replacingOccurrences(of: " ,", with: ",")
        clean = clean.replacingOccurrences(of: " .", with: ".")
        clean = clean.replacingOccurrences(of: " !", with: "!")
        clean = clean.replacingOccurrences(of: " ?", with: "?")
        clean = clean.replacingOccurrences(of: " '", with: "'")
        clean = clean.replacingOccurrences(of: " `", with: "'") // Fix backtick artifacts
        
        // Reduce double spaces if any
        while clean.contains("  ") {
            clean = clean.replacingOccurrences(of: "  ", with: " ")
        }
        
        return clean
    }
}

