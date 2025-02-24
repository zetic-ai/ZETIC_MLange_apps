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
                        let result = self?.convertAPType(result)
                        self?.updateResult(result!)
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
    
    func convertAPType(_ result: BenchmarkResult) -> BenchmarkResult {
        let apType =  switch result.target {
        case .ZETIC_MLANGE_TARGET_COREML:
            APType.CPU_GPU
        case .ZETIC_MLANGE_TARGET_COREML_FP32:
            APType.NPU
        default:
            APType.NA
        }
        
        return BenchmarkResult(path: result.path, target: result.target, targetModelBenchmarkResult: TargetModelBenchmarkResult(latency: result.targetModelBenchmarkResult.latency, snrs: [], apType: apType))
    }
    
    func updateResult(_ result: BenchmarkResult) {
        let resultNotExists = !results.contains {
            $0.targetModelBenchmarkResult.apType == result.targetModelBenchmarkResult.apType
        }
        
        let indicesToRemove = results.indices.filter {
            results[$0].targetModelBenchmarkResult.apType == result.targetModelBenchmarkResult.apType &&
            results[$0].targetModelBenchmarkResult.latency > result.targetModelBenchmarkResult.latency
        }
        
        let hadSlowerResults = !indicesToRemove.isEmpty
        for index in indicesToRemove.reversed() {
            results.remove(at: index)
        }
        
        if resultNotExists || hadSlowerResults {
            results.append(result)
        }
    }
}
