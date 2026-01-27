import Foundation
import ZeticMLange

final class ChronosForecastViewModel: ObservableObject {
    private static let patchLength = 48
    private static let patchCount = 512
    @Published var csvText = "" {
        didSet {
            parseCSVIfReady()
        }
    }
    @Published var outputCSV = ""
    @Published var predictionLength = "36"
    @Published var quantilesText = "0.1,0.5,0.9"
    @Published var idColumn = "item_id"
    @Published var timestampColumn = "Month"
    @Published var targetColumn = "#Passengers"
    @Published var availableSeriesIds: [String] = []
    @Published var selectedSeriesId = ""
    @Published var tokenKey = ""
    @Published var modelName = "jathin-zetic/chronos-2"
    @Published var modelVersion = "1"
    @Published var normalizationMode = "asinh_zscore"
    @Published var showSettings = false
    @Published var isRunning = false
    @Published var errorMessage: String?
    @Published var modelStatusText = "Model not loaded"
    @Published var hasForecast = false
    @Published var selectedExample: ExampleDataset?

    private var model: ZeticMLangeModel?
    private var dataset: TimeSeriesDataset?

    var historicalSeries: [SeriesPoint] {
        dataset?.series[selectedSeriesId] ?? []
    }

    var forecastSeries: [ForecastPoint] {
        parseForecastCSV(outputCSV)
    }

    init() {
        let env = ProcessInfo.processInfo.environment
        tokenKey = env["ZETIC_ACCESS_TOKEN"] ?? ""
        modelName = env["ZETIC_MODEL_NAME"] ?? modelName
        modelVersion = env["ZETIC_MODEL_VERSION"] ?? modelVersion
        normalizationMode = env["ZETIC_NORM_MODE"] ?? normalizationMode
        loadExample(ExampleData.airPassengers)
        loadModel()
    }

    func loadExample(_ example: ExampleDataset) {
        selectedExample = example
        csvText = example.csvContent
        idColumn = example.idColumn
        timestampColumn = example.timestampColumn
        targetColumn = example.targetColumn
        outputCSV = ""
        hasForecast = false
        parseCSVIfReady()
    }

    func loadModel() {
        errorMessage = nil
        modelStatusText = "Loading model..."
        let token = tokenKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let name = modelName.trimmingCharacters(in: .whitespacesAndNewlines)
        let version = Int(modelVersion.trimmingCharacters(in: .whitespacesAndNewlines)) ?? 1

        guard !token.isEmpty else {
            modelStatusText = "Missing access token"
            errorMessage = "Set a valid token in Model Settings or Xcode scheme env (ZETIC_ACCESS_TOKEN)."
            return
        }

        DispatchQueue.global(qos: .userInitiated).async {
            do {
                let loadedModel = try ZeticMLangeModel(tokenKey: token, name: name, version: version)
                DispatchQueue.main.async {
                    self.model = loadedModel
                    self.modelStatusText = "Model ready"
                }
            } catch {
                DispatchQueue.main.async {
                    self.modelStatusText = "Model failed to load"
                    self.errorMessage = "Failed to load model: \(error.localizedDescription)"
                }
            }
        }
    }


    func importCSV(result: Result<URL, Error>) {
        do {
            let url = try result.get()
            let didAccess = url.startAccessingSecurityScopedResource()
            defer {
                if didAccess { url.stopAccessingSecurityScopedResource() }
            }
            let data = try Data(contentsOf: url)
            guard let text = String(data: data, encoding: .utf8) else {
                throw CocoaError(.fileReadCorruptFile)
            }
            outputCSV = ""
            hasForecast = false
            csvText = text
            parseCSVIfReady()
        } catch {
            errorMessage = "CSV import failed: \(error.localizedDescription)"
        }
    }

    func runForecast() {
        errorMessage = nil
        outputCSV = ""
        hasForecast = false
        guard let model else {
            errorMessage = "Model not loaded yet."
            return
        }
        parseCSVIfReady()
        guard let dataset else {
            errorMessage = "Please import a CSV file first."
            return
        }
        guard let series = dataset.series[selectedSeriesId], !series.isEmpty else {
            errorMessage = "No time series values found for the selected ID."
            return
        }
        guard let horizon = Int(predictionLength.trimmingCharacters(in: .whitespacesAndNewlines)), horizon > 0 else {
            errorMessage = "Prediction length must be a positive integer."
            return
        }

        let quantiles = parseQuantiles(quantilesText)
        isRunning = true

        DispatchQueue.global(qos: .userInitiated).async {
            do {
                let values = series.map { Float($0.value) }
                let (patchInput, attentionMask, scale) = self.buildPatchInputs(from: values)
                let patchTensor = self.makeFloatTensor(values: patchInput, shape: [1, Self.patchCount, Self.patchLength])
                let maskTensor = self.makeFloatTensor(values: attentionMask, shape: [1, Self.patchCount])

                let inputOptions: [[Tensor]] = [
                    [patchTensor, maskTensor],
                    [patchTensor]
                ]

                var outputs: [Tensor] = []
                var lastError: Error?
                for option in inputOptions {
                    do {
                        outputs = try model.run(inputs: option)
                        lastError = nil
                        break
                    } catch {
                        lastError = error
                        if !self.shouldRetryWithDifferentInputCount(error, attemptedCount: option.count) {
                            break
                        }
                    }
                }

                if let lastError {
                    throw lastError
                }

                var forecast = try self.parseForecast(outputs: outputs, horizon: horizon, quantiles: quantiles)
                forecast = self.denormalizeForecast(forecast, scale: scale)
                let timestamps = self.buildFutureTimestamps(
                    lastTimestamp: series.last?.timestamp ?? "",
                    horizon: horizon
                )
                let csv = self.buildOutputCSV(
                    seriesId: self.selectedSeriesId,
                    targetName: self.targetColumn,
                    timestamps: timestamps,
                    forecast: forecast,
                    horizon: horizon,
                    quantiles: quantiles
                )

                DispatchQueue.main.async {
                    self.outputCSV = csv
                    self.hasForecast = true
                    self.isRunning = false
                }
            } catch {
                DispatchQueue.main.async {
                    self.errorMessage = "Forecast failed: \(error.localizedDescription)"
                    self.hasForecast = false
                    self.isRunning = false
                }
            }
        }
    }

    private func parseCSVIfReady() {
        do {
            let dataset = try parseCSV(
                csvText,
                idColumn: idColumn,
                timestampColumn: timestampColumn,
                targetColumn: targetColumn
            )
            self.dataset = dataset
            self.availableSeriesIds = dataset.series.keys.sorted()
            if !availableSeriesIds.contains(selectedSeriesId) {
                selectedSeriesId = availableSeriesIds.first ?? ""
            }
            errorMessage = nil
        } catch {
            errorMessage = "CSV parse error: \(error.localizedDescription)"
        }
    }

    private func parseQuantiles(_ text: String) -> [Float] {
        let parts = text.split(separator: ",")
        let values = parts.compactMap { Float($0.trimmingCharacters(in: .whitespaces)) }
        return values.isEmpty ? [0.1, 0.5, 0.9] : values
    }

    private func buildPatchInputs(from values: [Float]) -> ([Float], [Float], NormalizationScale) {
        let patchLength = Self.patchLength
        let patchCount = Self.patchCount
        let totalValues = patchLength * patchCount
        let padded: [Float]
        if values.count >= totalValues {
            padded = Array(values.suffix(totalValues))
        } else {
            let padding = Array(repeating: Float.nan, count: totalValues - values.count)
            padded = padding + values
        }

        let scale = NormalizationScale.from(padded, mode: normalizationMode)
        let normalized = padded.map { scale.normalize($0) }
        let patches = normalized.map { $0.isNaN ? 0 : $0 }

        var attentionMask = [Float](repeating: 0, count: patchCount)
        for patchIndex in 0..<patchCount {
            let start = patchIndex * patchLength
            let end = start + patchLength
            let hasValue = padded[start..<end].contains { !$0.isNaN }
            attentionMask[patchIndex] = hasValue ? 1 : 0
        }

        return (patches, attentionMask, scale)
    }

    private func denormalizeForecast(_ forecast: ForecastBundle, scale: NormalizationScale) -> ForecastBundle {
        let updated = forecast.byQuantile.mapValues { series in
            series.map { scale.denormalize($0) }
        }
        return ForecastBundle(byQuantile: updated)
    }

    private func shouldRetryWithDifferentInputCount(_ error: Error, attemptedCount: Int) -> Bool {
        let message = error.localizedDescription.lowercased()
        if message.contains("expected: 2") || message.contains("expected 2") {
            return attemptedCount != 2
        }
        if message.contains("expected: 3") || message.contains("expected 3") {
            return attemptedCount != 3
        }
        if message.contains("expected: 1") || message.contains("expected 1") {
            return attemptedCount != 1
        }
        if message.contains("input count") || message.contains("input") && message.contains("expected") {
            return false
        }
        return true
    }

    private func makeFloatTensor(values: [Float], shape: [Int]) -> Tensor {
        let data = values.withUnsafeBufferPointer { Data(buffer: $0) }
        return Tensor(data: data, dataType: BuiltinDataType.float32, shape: shape)
    }

    private func makeIntTensor(values: [Int], shape: [Int]) -> Tensor {
        let int32Values = values.map { Int32($0) }
        let data = int32Values.withUnsafeBufferPointer { Data(buffer: $0) }
        return Tensor(data: data, dataType: BuiltinDataType.int32, shape: shape)
    }

    private func parseForecast(outputs: [Tensor], horizon: Int, quantiles: [Float]) throws -> ForecastBundle {
        guard let first = outputs.first else {
            throw NSError(domain: "Chronos", code: 1001, userInfo: [NSLocalizedDescriptionKey: "Model returned no outputs."])
        }

        if outputs.count > 1, outputs.count == quantiles.count {
            var byQuantile: [String: [Float]] = [:]
            for (index, tensor) in outputs.enumerated() {
                let values = decodeFloatArray(from: tensor)
                byQuantile[formatQuantileLabel(quantiles[index])] = Array(values.prefix(horizon))
            }
            return ForecastBundle(byQuantile: byQuantile)
        }

        let values = decodeFloatArray(from: first)
        let quantileCount = max(quantiles.count, 1)

        if quantileCount > 1, values.count >= horizon * quantileCount {
            var byQuantile: [String: [Float]] = [:]
            for q in 0..<quantileCount {
                let label = quantiles.indices.contains(q) ? formatQuantileLabel(quantiles[q]) : "q\(q)"
                var series: [Float] = []
                for step in 0..<horizon {
                    // Output is (Batch, Quantiles, Horizon)
                    // Flattened: [q0_h0, q0_h1... q0_hN, q1_h0...]
                    let index = q * horizon + step
                    if index < values.count {
                        series.append(values[index])
                    }
                }
                byQuantile[label] = series
            }
            return ForecastBundle(byQuantile: byQuantile)
        }

        return ForecastBundle(byQuantile: ["mean": Array(values.prefix(horizon))])
    }

    private func decodeFloatArray(from tensor: Tensor) -> [Float] {
        let data = tensor.data
        let count = data.count / MemoryLayout<Float>.size
        return data.withUnsafeBytes { pointer in
            let buffer = pointer.bindMemory(to: Float.self)
            return Array(buffer.prefix(count))
        }
    }

    private func formatQuantileLabel(_ quantile: Float) -> String {
        let formatted = String(format: "%.2f", quantile)
        return "q\(formatted)".replacingOccurrences(of: ".", with: "_")
    }

    private func buildOutputCSV(
        seriesId: String,
        targetName: String,
        timestamps: [String],
        forecast: ForecastBundle,
        horizon: Int,
        quantiles: [Float]
    ) -> String {
        let predictionKey = formatQuantileLabel(0.5)
        let quantileColumns = quantiles.map { formatQuantileColumn($0) }
        let header = ["item_id", "Month", "target_name", "predictions"] + quantileColumns
        var rows = [header.joined(separator: ",")]

        for step in 0..<horizon {
            let timestamp = step < timestamps.count ? timestamps[step] : "step-\(step + 1)"
            let prediction = forecast.byQuantile[predictionKey]?[safe: step] ??
                forecast.byQuantile["mean"]?[safe: step] ?? 0
            var row = [seriesId, timestamp, targetName, String(format: "%.6f", prediction)]
            for quantile in quantiles {
                let key = formatQuantileLabel(quantile)
                let value = forecast.byQuantile[key]?[safe: step] ?? prediction
                row.append(String(format: "%.6f", value))
            }
            rows.append(row.joined(separator: ","))
        }
        return rows.joined(separator: "\n")
    }

    private func buildFutureTimestamps(lastTimestamp: String, horizon: Int) -> [String] {
        guard let date = parseTimestamp(lastTimestamp) else {
            return (1...horizon).map { "step-\($0)" }
        }
        var result: [String] = []
        var current = date
        let calendar = Calendar(identifier: .gregorian)
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.dateFormat = "yyyy-MM-dd"
        for _ in 0..<horizon {
            current = calendar.date(byAdding: .month, value: 1, to: current) ?? current
            result.append(formatter.string(from: current))
        }
        return result
    }

    private func parseTimestamp(_ value: String) -> Date? {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        let formats = ["yyyy-MM-dd", "yyyy-MM", "yyyy/MM/dd", "yyyy/M/d"]
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        for format in formats {
            formatter.dateFormat = format
            if let date = formatter.date(from: trimmed) {
                return date
            }
        }
        return nil
    }

    private func formatQuantileColumn(_ quantile: Float) -> String {
        let formatted = String(format: "%.2f", quantile)
        if formatted.hasSuffix(".00") { return String(formatted.dropLast(3)) }
        if formatted.hasSuffix("0") { return String(formatted.dropLast()) }
        return formatted
    }
}

private struct ForecastBundle {
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

private struct NormalizationScale {
    let mean: Float
    let std: Float
    let useArcsinh: Bool

    static func from(_ values: [Float], mode: String) -> NormalizationScale {
        let normalizedMode = mode.lowercased()
        let useArcsinh = normalizedMode == "asinh_zscore" || normalizedMode == "asinh"

        let raw = values.filter { !$0.isNaN }
        guard !raw.isEmpty else {
            return NormalizationScale(mean: 0, std: 1, useArcsinh: useArcsinh)
        }
        
        // Calculate mean and std on raw values
        let mean = raw.reduce(0, +) / Float(raw.count)
        let variance = raw.reduce(0) { $0 + pow($1 - mean, 2) } / Float(raw.count)
        let std = max(sqrt(variance), 1e-5) // Using 1e-5 to match reference
        return NormalizationScale(mean: mean, std: std, useArcsinh: useArcsinh)
    }

    func normalize(_ value: Float) -> Float {
        if value.isNaN { return .nan }
        // (x - mean) / std -> then asinh
        let scaled = (value - mean) / std
        return useArcsinh ? asinh(scaled) : scaled
    }

    func denormalize(_ value: Float) -> Float {
        // Inverse: sinh(x) -> then * std + mean
        let deTransformed = useArcsinh ? sinh(value) : value
        return deTransformed * std + mean
    }
}

private struct TimeSeriesDataset {
    let series: [String: [SeriesPoint]]
}

struct SeriesPoint {
    let timestamp: String
    let value: Double
}

struct ForecastPoint: Identifiable {
    let id = UUID()
    let timestamp: String
    let prediction: Double
    let q10: Double
    let q50: Double
    let q90: Double
}

private func parseForecastCSV(_ text: String) -> [ForecastPoint] {
    let lines = text.split(whereSeparator: \.isNewline).map(String.init)
    guard lines.count > 1 else { return [] }
    let header = parseCSVLine(lines[0])
    let idxTimestamp = header.firstIndex(of: "Month") ?? header.firstIndex(of: "timestamp")
    let idxPred = header.firstIndex(of: "predictions")
    let idxQ10 = header.firstIndex(of: "0.1") ?? header.firstIndex(of: "q0_10")
    let idxQ50 = header.firstIndex(of: "0.5") ?? header.firstIndex(of: "q0_50")
    let idxQ90 = header.firstIndex(of: "0.9") ?? header.firstIndex(of: "q0_90")

    var points: [ForecastPoint] = []
    for line in lines.dropFirst() {
        let cols = parseCSVLine(line)
        guard
            let tsIdx = idxTimestamp, tsIdx < cols.count,
            let pIdx = idxPred, pIdx < cols.count,
            let q10Idx = idxQ10, q10Idx < cols.count,
            let q50Idx = idxQ50, q50Idx < cols.count,
            let q90Idx = idxQ90, q90Idx < cols.count
        else { continue }

        let ts = cols[tsIdx]
        let pred = Double(cols[pIdx]) ?? 0
        let q10 = Double(cols[q10Idx]) ?? pred
        let q50 = Double(cols[q50Idx]) ?? pred
        let q90 = Double(cols[q90Idx]) ?? pred
        points.append(ForecastPoint(timestamp: ts, prediction: pred, q10: q10, q50: q50, q90: q90))
    }
    return points
}

private func parseCSV(_ text: String, idColumn: String, timestampColumn: String, targetColumn: String) throws -> TimeSeriesDataset {
    let lines = text.split(whereSeparator: \.isNewline).map(String.init)
    guard let headerLine = lines.first else {
        throw NSError(domain: "Chronos", code: 1002, userInfo: [NSLocalizedDescriptionKey: "CSV is empty."])
    }

    let headers = parseCSVLine(headerLine)
    let idIndex = headers.firstIndex { $0.caseInsensitiveCompare(idColumn) == .orderedSame }
    let timestampIndex = headers.firstIndex { $0.caseInsensitiveCompare(timestampColumn) == .orderedSame }
    let targetIndex = headers.firstIndex { $0.caseInsensitiveCompare(targetColumn) == .orderedSame }

    guard let targetIndex else {
        throw NSError(domain: "Chronos", code: 1003, userInfo: [NSLocalizedDescriptionKey: "Target column not found."])
    }

    var series: [String: [SeriesPoint]] = [:]

    for line in lines.dropFirst() {
        let columns = parseCSVLine(line)
        guard columns.count > targetIndex else { continue }
        let idValue = idIndex.flatMap { $0 < columns.count ? columns[$0] : nil }
        let timestampValue = timestampIndex.flatMap { $0 < columns.count ? columns[$0] : nil } ?? ""
        let seriesId = (idValue?.isEmpty == false) ? idValue! : "series-1"
        let targetValue = columns[targetIndex].trimmingCharacters(in: .whitespacesAndNewlines)

        guard let value = Double(targetValue) else { continue }
        let point = SeriesPoint(timestamp: timestampValue, value: value)
        series[seriesId, default: []].append(point)
    }

    if series.isEmpty {
        throw NSError(domain: "Chronos", code: 1004, userInfo: [NSLocalizedDescriptionKey: "No valid rows found in CSV."])
    }

    return TimeSeriesDataset(series: series)
}

private func parseCSVLine(_ line: String) -> [String] {
    var result: [String] = []
    var current = ""
    var inQuotes = false
    var iterator = line.makeIterator()

    while let char = iterator.next() {
        if char == "\"" {
            if inQuotes {
                if let nextChar = iterator.next() {
                    if nextChar == "\"" {
                        current.append("\"")
                    } else if nextChar == "," {
                        inQuotes = false
                        result.append(current)
                        current = ""
                    } else {
                        inQuotes = false
                        current.append(nextChar)
                    }
                } else {
                    inQuotes = false
                }
            } else {
                inQuotes = true
            }
        } else if char == "," && !inQuotes {
            result.append(current)
            current = ""
        } else {
            current.append(char)
        }
    }

    result.append(current)
    return result.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
}

private extension Collection {
    subscript(safe index: Index) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}

private extension ChronosForecastViewModel {
    static let sampleCSV = ExampleData.airPassengers.csvContent

}

