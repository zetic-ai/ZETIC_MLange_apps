import SwiftUI
import ZeticMLange

struct BenchmarkResultRow: View {
    let result: BenchmarkResult
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(String(describing: String(describing: result.targetModelBenchmarkResult.apType)))
                .font(.headline)
            
            HStack {
                Text("Latency:")
                    .foregroundColor(.secondary)
                Text(String(format: "%.8f sec", result.targetModelBenchmarkResult.latency))
            }
        }
        .padding(.vertical, 8)
    }
}
