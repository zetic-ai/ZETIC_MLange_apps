import SwiftUI

class FeatureItem: ObservableObject, Identifiable {
    let name: String
    let modelKeys: Array<String>
    @Published var modelStatus: ModelStatus
    
    init(name: String, modelKeys: Array<String>) {
        self.name = name
        self.modelKeys = modelKeys
        self.modelStatus = if UserDefaults.standard.bool(forKey: name) { .ready } else { .notReady }
    }
}

enum ModelStatus {
    case notReady
    case fetching
    case ready
}
