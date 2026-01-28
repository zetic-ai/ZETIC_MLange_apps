import Foundation

struct Language: Identifiable, Hashable {
    let id = UUID()
    let name: String
    let abbr: String
    
    // Conforming to Hashable explicitly if needed, typically synthesized
    static func == (lhs: Language, rhs: Language) -> Bool {
        return lhs.abbr == rhs.abbr && lhs.name == rhs.name
    }
}

struct Languages {
    static let list = [
        Language(name: "Chinese", abbr: "zh"),
        Language(name: "English", abbr: "en"),
        Language(name: "French", abbr: "fr"),
        Language(name: "Portuguese", abbr: "pt"),
        Language(name: "Spanish", abbr: "es"),
        Language(name: "Japanese", abbr: "ja"),
        Language(name: "Turkish", abbr: "tr"),
        Language(name: "Russian", abbr: "ru"),
        Language(name: "Arabic", abbr: "ar"),
        Language(name: "Korean", abbr: "ko"),
        Language(name: "Thai", abbr: "th"),
        Language(name: "Italian", abbr: "it"),
        Language(name: "German", abbr: "de"),
        Language(name: "Vietnamese", abbr: "vi"),
        Language(name: "Malay", abbr: "ms"),
        Language(name: "Indonesian", abbr: "id"),
        Language(name: "Filipino", abbr: "tl"),
        Language(name: "Hindi", abbr: "hi"),
        Language(name: "Traditional Chinese", abbr: "zh-Hant"),
        Language(name: "Polish", abbr: "pl"),
        Language(name: "Czech", abbr: "cs"),
        Language(name: "Dutch", abbr: "nl"),
        Language(name: "Khmer", abbr: "km"),
        Language(name: "Burmese", abbr: "my"),
        Language(name: "Persian", abbr: "fa"),
        Language(name: "Gujarati", abbr: "gu"),
        Language(name: "Urdu", abbr: "ur"),
        Language(name: "Telugu", abbr: "te"),
        Language(name: "Marathi", abbr: "mr"),
        Language(name: "Hebrew", abbr: "he"),
        Language(name: "Bengali", abbr: "bn"),
        Language(name: "Tamil", abbr: "ta"),
        Language(name: "Ukrainian", abbr: "uk"),
        Language(name: "Tibetan", abbr: "bo"),
        Language(name: "Kazakh", abbr: "kk"),
        Language(name: "Mongolian", abbr: "mn"),
        Language(name: "Uyghur", abbr: "ug"),
        Language(name: "Cantonese", abbr: "yue")
    ]
}
