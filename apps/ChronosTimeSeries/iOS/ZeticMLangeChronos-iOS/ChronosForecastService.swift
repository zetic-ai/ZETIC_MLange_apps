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
    @Published var tokenKey = "YOUR_MLANGE_KEY"
    @Published var modelName = "Team_ZETIC/Chronos-balt-tiny"
    @Published var modelVersion = "5"
    // Token Key default updated (User provided explicit key)
    
    @Published var normalizationMode = "mean_scale"
    @Published var showSettings = false
    @Published var isRunning = false
    @Published var errorMessage: String?
    @Published var modelStatusText = "Model not loaded"
    @Published var hasForecast = false
    @Published var selectedExample: ExampleDataset?
    
    // Table Editor State
    @Published var tableHeaders: [String] = []
    @Published var tableRows: [[String]] = []

    private let predictor = ChronosPredictor()
    private var dataset: TimeSeriesDataset?

    var historicalSeries: [SeriesPoint] {
        dataset?.series[selectedSeriesId] ?? []
    }

    var forecastSeries: [ForecastPoint] {
        // Simple parse from CSV text for visualization
        // In a real app we might store objects directly
        parseForecastCSV(outputCSV)
    }

    init() {
        // Hardcoded Config as requested
        tokenKey = "YOUR_MLANGE_KEY"
        modelName = "Team_ZETIC/Chronos-balt-tiny"
        modelVersion = "5"
        
        loadExample(ExampleData.hourlyDemand)
        loadModel()
    }

    func loadExample(_ example: ExampleDataset) {
        selectedExample = example
        csvText = example.csvContent
        idColumn = example.idColumn
        timestampColumn = example.timestampColumn
        targetColumn = example.targetColumn
        predictionLength = example.defaultPredictionLength
        quantilesText = example.defaultQuantiles
        outputCSV = ""
        hasForecast = false
        parseCSVIfReady()
        parseTableFromCSV() 
    }

    func loadModel() {
        errorMessage = nil
        modelStatusText = "Loading model..."

        guard !tokenKey.isEmpty else {
            modelStatusText = "Missing access token"
            errorMessage = "Set a valid token."
            return
        }

        Task {
            do {
                try await predictor.loadModel(token: tokenKey, name: modelName, version: Int(modelVersion) ?? 5) { progress in
                    DispatchQueue.main.async {
                        self.modelStatusText = "Downloading... \(Int(progress * 100))%"
                    }
                }
                DispatchQueue.main.async {
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
            selectedExample = nil // Clear example selection on custom import
            csvText = text
            parseCSVIfReady()
            parseTableFromCSV()
        } catch {
            errorMessage = "CSV import failed: \(error.localizedDescription)"
        }
    }

    func onSeriesSelected(_ seriesId: String) {
        selectedSeriesId = seriesId
        outputCSV = ""
        hasForecast = false
    }

    func runForecast() {
        errorMessage = nil
        outputCSV = ""
        hasForecast = false
        parseCSVIfReady()
        guard let dataset else { errorMessage = "Please import a CSV file first."; return }
        guard let series = dataset.series[selectedSeriesId], !series.isEmpty else { errorMessage = "No data."; return }
        guard let horizon = Int(predictionLength.trimmingCharacters(in: .whitespacesAndNewlines)), horizon > 0 else {
            errorMessage = "Invalid horizon."; return
        }
        
        let quantiles = parseQuantiles(quantilesText)
        let values = series.map { Float($0.value) }
        
        isRunning = true
        
        Task {
            do {
                // Run Inference via Predictor
                let result = try predictor.runInference(values: values, horizon: horizon, quantiles: quantiles)
                
                // Build CSV & Update UI
                let timestamps = self.buildFutureTimestamps(lastTimestamp: series.last?.timestamp ?? "", horizon: horizon)
                let csv = self.buildOutputCSV(
                     seriesId: self.selectedSeriesId,
                     targetName: self.targetColumn,
                     timestamps: timestamps,
                     forecast: result.forecast,
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
                     self.errorMessage = "Error: \(error.localizedDescription)"
                     self.isRunning = false
                }
            }
        }
    }
    
    // Exact logic from prepare_inputs in extract_chronos.py
    // Removed old tensor helpers and prepareInputs (moved to Predictor)



    // New Parse Logic for [1, 9, OutputConfig]
    // Removed parseForecastBolt (moved to Predictor)

    // Removed old patch/denorm logic

    
    // Softmax + Expected Value
    // Removed decodeFloatArray/makeFloatTensor (moved to Predictor)

    func updateTableCell(row: Int, col: Int, value: String) {
        guard row < tableRows.count, col < tableRows[row].count else { return }
        tableRows[row][col] = value
        
        // Reconstruct CSV
        let headerLine = tableHeaders.joined(separator: ",")
        let dataLines = tableRows.map { $0.joined(separator: ",") }.joined(separator: "\n")
        let newCSV = headerLine + "\n" + dataLines
        
        // Update csvText safely without triggering a full table re-parse loop if we are careful
        // But here we want to trigger model update potentially (parseCSVIfReady), so updating csvText is correct.
        csvText = newCSV
        // We probably don't need to re-parse table from CSV since we just edited table.
        // But parseCSVIfReady is called on didSet.
    }

    func parseTableFromCSV() {
        let lines = csvText.split(whereSeparator: \.isNewline).map(String.init)
        guard let first = lines.first else {
            tableHeaders = []
            tableRows = []
            return
        }
        tableHeaders = parseCSVLine(first)
        tableRows = lines.dropFirst().map { parseCSVLine($0) }
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

struct TimeSeriesDataset {
    let series: [String: [SeriesPoint]]
}

private func parseForecastCSV(_ text: String) -> [ForecastPoint] {
    let lines = text.split(whereSeparator: \.isNewline).map(String.init)
    guard lines.count > 1 else { return [] }
    let header = parseCSVLine(lines[0])
    
    // Helper to find index of a column that looks like a quantile
    func findQuantileIdx(_ target: String) -> Int? {
        // Try exact match "0.5", "0.50"
        if let idx = header.firstIndex(where: { $0 == target || $0 == target + "0" }) { return idx }
        // Try "q0_50"
        let qTarget = "q" + target.replacingOccurrences(of: ".", with: "_")
        if let idx = header.firstIndex(where: { $0.hasPrefix(qTarget) }) { return idx }
        return nil
    }

    let idxTimestamp = header.firstIndex(of: "Month") ?? header.firstIndex(of: "timestamp")
    let idxPred = header.firstIndex(of: "predictions")
    
    // Dynamic fallback
    let quantileIndices = header.enumerated().filter { idx, name in
        Float(name) != nil
    }.map { $0.offset }
    
    let sortedQIndices = quantileIndices.sorted { 
        (Float(header[$0]) ?? 0) < (Float(header[$1]) ?? 0)
    }
    
    var idxQ10: Int? = findQuantileIdx("0.1")
    var idxQ50: Int? = findQuantileIdx("0.5")
    var idxQ90: Int? = findQuantileIdx("0.9")
    
    if idxQ10 == nil && !sortedQIndices.isEmpty { idxQ10 = sortedQIndices.first }
    if idxQ90 == nil && !sortedQIndices.isEmpty { idxQ90 = sortedQIndices.last }
    if idxQ50 == nil && !sortedQIndices.isEmpty { 
        idxQ50 = sortedQIndices[sortedQIndices.count / 2]
    }

    var points: [ForecastPoint] = []
    for line in lines.dropFirst() {
        let cols = parseCSVLine(line)
        guard let tsIdx = idxTimestamp, tsIdx < cols.count else { continue }
        
        let ts = cols[tsIdx]
        
        let predVal: Double
        if let pIdx = idxPred, pIdx < cols.count, let v = Double(cols[pIdx]) {
            predVal = v
        } else if let mid = idxQ50, mid < cols.count, let v = Double(cols[mid]) {
            predVal = v
        } else {
            predVal = 0
        }
        
        let q10 = (idxQ10 != nil && idxQ10! < cols.count) ? (Double(cols[idxQ10!]) ?? predVal) : predVal
        let q50 = (idxQ50 != nil && idxQ50! < cols.count) ? (Double(cols[idxQ50!]) ?? predVal) : predVal
        let q90 = (idxQ90 != nil && idxQ90! < cols.count) ? (Double(cols[idxQ90!]) ?? predVal) : predVal
        
        points.append(ForecastPoint(timestamp: ts, prediction: predVal, q10: q10, q50: q50, q90: q90))
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
        throw NSError(domain: "Chronos", code: 1004, userInfo: [NSLocalizedDescriptionKey: "No valid rows found."])
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

extension Collection {
    subscript(safe index: Index) -> Element? {
        indices.contains(index) ? self[index] : nil
    }
}


