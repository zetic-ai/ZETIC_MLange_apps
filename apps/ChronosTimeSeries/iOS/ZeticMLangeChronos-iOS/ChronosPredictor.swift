import Foundation
import ZeticMLange

// Public struct for Forecast Bundle
struct ForecastBundle {
    let byQuantile: [String: [Float]]
    
    func orderedKeys(preferredQuantiles: [Float]) -> [String] {
        let preferredLabels = preferredQuantiles.map { labelForQuantile($0) }
        let existingPreferred = preferredLabels.filter { byQuantile[$0] != nil }
        let remaining = byQuantile.keys.filter { !existingPreferred.contains($0) }.sorted()
        return existingPreferred + remaining
    }

    private func labelForQuantile(_ quantile: Float) -> String {
        let formatted = String(format: "%.2f", quantile)
        return "q\(formatted)".replacingOccurrences(of: ".", with: "_")
    }
}

struct ForecastResult {
    let forecast: ForecastBundle
}

class ChronosPredictor {
    private var model: ZeticMLangeModel?
    private let contextLength = 512
    private let modelQuantilesCount = 9
    
    enum PredictionError: Error {
        case modelNotLoaded
        case invalidInput
        case noOutput
    }

    /// Initialize the model with Zetic MLange
    func loadModel(token: String, name: String, version: Int, progressHandler: @escaping (Float) -> Void) async throws {
        self.model = try ZeticMLangeModel(tokenKey: token, name: name, version: version, onDownload: progressHandler)
    }

    /// Run inference for a given time series
    func runInference(values: [Float], horizon: Int, quantiles: [Float]) throws -> ForecastResult {
        guard let model = model else { throw PredictionError.modelNotLoaded }
        
        // 1. Prepare Inputs (Right-aligned, NaN-padded, [1, 512])
        let contextInput = prepareInputs(values: values, length: contextLength)
        let contextTensor = makeFloatTensor(values: contextInput, shape: [1, contextLength])
        
        // 2. Run Model
        let outputs = try model.run(inputs: [contextTensor])
        
        // 3. Parse Output
        guard let first = outputs.first else { throw PredictionError.noOutput }
        let forecastBundle = try parseForecastBolt(tensor: first, horizon: horizon, reqQuantiles: quantiles)
        
        return ForecastResult(forecast: forecastBundle)
    }

    // MARK: - Internal Logic

    private func prepareInputs(values: [Float], length: Int) -> [Float] {
        var buffer = Array(repeating: Float.nan, count: length)
        
        if values.count >= length {
            // Take last 'length'
            let suffix = values.suffix(length)
            for (i, v) in suffix.enumerated() {
                buffer[i] = v
            }
        } else {
            // Place at end (Right-aligned)
            let offset = length - values.count
            for (i, v) in values.enumerated() {
                buffer[offset + i] = v
            }
        }
        return buffer
    }

    private func parseForecastBolt(tensor: Tensor, horizon: Int, reqQuantiles: [Float]) throws -> ForecastBundle {
        let values = decodeFloatArray(from: tensor)
        let modelHorizon = values.count / modelQuantilesCount
        
        var byQuantile: [String: [Float]] = [:]
        
        if values.count >= modelQuantilesCount * modelHorizon {
            let stride = modelHorizon
            let readLen = min(horizon, modelHorizon)
            
            // Median Index is 4 (0..8)
            let medianIdx = 4
            let startM = medianIdx * stride
            byQuantile["mean"] = Array(values[startM..<(startM + readLen)])
            
            for q in reqQuantiles {
                // Map 0.1 -> 0, 0.5 -> 4, 0.9 -> 8
                let idx = max(0, min(8, Int(q * 10.0) - 1))
                let start = idx * stride
                let end = start + readLen
                byQuantile[formatQuantileLabel(q)] = Array(values[start..<end])
            }
        } else {
            // Fallback
            byQuantile["mean"] = Array(values.prefix(horizon))
        }
        
        return ForecastBundle(byQuantile: byQuantile)
    }
    
    private func decodeFloatArray(from tensor: Tensor) -> [Float] {
        let data = tensor.data
        return data.withUnsafeBytes { ptr in 
            let buff = ptr.bindMemory(to: Float.self)
            return Array(buff)
        }
    }
    
    private func makeFloatTensor(values: [Float], shape: [Int]) -> Tensor {
        let data = values.withUnsafeBufferPointer { Data(buffer: $0) }
        return Tensor(data: data, dataType: BuiltinDataType.float32, shape: shape)
    }
    
    private func formatQuantileLabel(_ quantile: Float) -> String {
        let formatted = String(format: "%.2f", quantile)
        return "q\(formatted)".replacingOccurrences(of: ".", with: "_")
    }
}
