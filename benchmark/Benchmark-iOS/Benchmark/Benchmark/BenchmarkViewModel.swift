import SwiftUI
import ZeticMLange

@MainActor
class BenchmarkViewModel: ObservableObject {
    @Published var modelKey: String = ""
    @Published var results: [BenchmarkResult] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    
    func runBenchmark() {
        guard !modelKey.isEmpty else {
            errorMessage = "Please enter a model key"
            return
        }
        
        let benchmarkModel = BenchmarkModel()
        isLoading = true
        results.removeAll()
        
        let key = modelKey
        
        Task.detached { [weak self] in
            do {
                try benchmarkModel.benchmarkAll(key) { result in
                    Task { @MainActor in
                        self?.results.append(result)
                    }
                }
            } catch {
                Task { @MainActor in
                    self?.errorMessage = "Error running benchmark: \(error.localizedDescription)"
                }
            }
            
            Task { @MainActor in
                self?.isLoading = false
            }
        }
    }
}
