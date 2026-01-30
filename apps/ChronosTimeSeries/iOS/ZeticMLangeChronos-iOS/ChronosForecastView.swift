import SwiftUI
import UniformTypeIdentifiers
import Charts

struct ChronosForecastView: View {
    @StateObject private var viewModel = ChronosForecastViewModel()
    @State private var isImporting = false
    @State private var isExporting = false
    @State private var exportDocument = CSVDocument(text: "")
    @State private var outputMode: OutputMode = .chart
    
    // Table Edit State
    @State private var isEditingCell = false
    @State private var editingValue = ""
    @State private var editingRow: Int?
    @State private var editingCol: Int?
    
    // Zetic Brand Color #34A9A3
    private let zeticTeal = Color(red: 52/255, green: 169/255, blue: 163/255)

    var body: some View {
            ZStack {
            LinearGradient(
                colors: [zeticTeal.opacity(0.8), zeticTeal.opacity(0.6)],
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
            .ignoresSafeArea()

            ScrollView {
                VStack(spacing: 20) {
                    header
                    modelStatusCard
                    inputCard
                    forecastSettingsCard
                    runButton
                    outputCard
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 28)
            }
        }
        .fileImporter(
            isPresented: $isImporting,
            allowedContentTypes: [.commaSeparatedText, .plainText]
        ) { result in
            viewModel.importCSV(result: result)
        }
        .fileExporter(
            isPresented: $isExporting,
            document: exportDocument,
            contentType: .commaSeparatedText,
            defaultFilename: "chronos_forecast"
        ) { result in
            if case let .failure(error) = result {
                viewModel.errorMessage = "Export failed: \(error.localizedDescription)"
            }
        }
        .sheet(isPresented: $viewModel.showSettings) {
            ModelSettingsSheet(viewModel: viewModel)
        }
        .onChange(of: viewModel.outputCSV) { newValue in
            exportDocument = CSVDocument(text: newValue)
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                Image(systemName: "chart.line.uptrend.xyaxis.circle.fill")
                    .font(.system(size: 36))
                    .foregroundStyle(.white)
                VStack(alignment: .leading, spacing: 4) {
                    Text("Chronos Future Forecast")
                        .font(.title.bold())
                        .foregroundStyle(.white)
                    Text("Powered by MLange")
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.85))
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var modelStatusCard: some View {
        SectionCard {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("Model Status")
                        .font(.headline)
                    
                    Spacer()
                    
                    // Status Indicator
                    HStack(spacing: 6) {
                        Circle()
                            .fill(statusColor)
                            .frame(width: 10, height: 10)
                        Text(viewModel.modelStatusText)
                            .font(.subheadline)
                            .foregroundStyle(statusColor)
                    }
                }
                
                // Progress Bar for Downloading
                if viewModel.modelStatusText.contains("Downloading") {
                    ProgressView()
                        .progressViewStyle(.linear)
                        .tint(.orange)
                }
                
                if let error = viewModel.errorMessage {
                    Text(error)
                        .font(.footnote)
                        .foregroundStyle(.red)
                        .padding(.top, 4)
                }
            }
        }
    }
    
    private var statusColor: Color {
        if viewModel.modelStatusText.contains("ready") {
            return .green
        } else if viewModel.modelStatusText.contains("Loading") || viewModel.modelStatusText.contains("Downloading") {
            return .orange
        } else if viewModel.errorMessage != nil || viewModel.modelStatusText.contains("failed") {
            return .red
        } else {
            return .gray
        }
    }

    private var inputCard: some View {
        SectionCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .center, spacing: 12) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Example Name:")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Text(viewModel.selectedExample?.name ?? "User File")
                            .font(.subheadline.bold())
                            .lineLimit(1)
                    }
                    
                    Spacer()
                    
                    Menu {
                        ForEach(ExampleData.allValues) { example in
                            Button(example.name) {
                                viewModel.loadExample(example)
                            }
                        }
                    } label: {
                        Label("Example", systemImage: "book.pages")
                    }
                    .buttonStyle(.bordered)
                    
                    Button("Import") {
                        isImporting = true
                    }
                    .buttonStyle(.bordered)
                }
                
                if !viewModel.historicalSeries.isEmpty {
                    ForecastChart(
                        history: viewModel.historicalSeries,
                        forecast: []
                    )
                    .frame(height: 160)
                    .padding(.bottom, 8)
                    .id("History-" + viewModel.selectedSeriesId)
                }

                Text("CSV should include time series values with columns for ID, timestamp, and target.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                // Table Editor
                // Table Editor
                ScrollView([.horizontal, .vertical]) {
                    VStack(alignment: .leading, spacing: 0) {
                        // Header
                        HStack(spacing: 0) {
                            ForEach(Array(viewModel.tableHeaders.enumerated()), id: \.offset) { _, header in
                                Text(header)
                                    .font(.caption.bold())
                                    .frame(width: 100, height: 32)
                                    .background(Color(.systemGray6))
                                    .overlay(
                                        Rectangle()
                                            .frame(height: 1)
                                            .foregroundColor(Color.gray.opacity(0.3)),
                                        alignment: .bottom
                                    )
                                    .overlay(
                                        Rectangle()
                                            .frame(width: 1)
                                            .foregroundColor(Color.gray.opacity(0.1)),
                                        alignment: .trailing
                                    )
                            }
                        }
                        .background(Color(.systemGray5))
                        
                        // Rows
                        // Use VStack with Text + Tap Gesture to prevent TextField rendering freeze
                        VStack(alignment: .leading, spacing: 0) {
                            ForEach(Array(viewModel.tableRows.enumerated()), id: \.offset) { rowIndex, row in
                                HStack(spacing: 0) {
                                    ForEach(Array(row.enumerated()), id: \.offset) { colIndex, cell in
                                        Text(cell)
                                            .font(.caption)
                                            .frame(width: 100, height: 32, alignment: .leading)
                                            .padding(.horizontal, 4)
                                            .background(rowIndex % 2 == 0 ? Color.white : Color(.systemGray6).opacity(0.5))
                                            .contentShape(Rectangle()) // Make full area tappable
                                            .onTapGesture {
                                                // Trigger Edit
                                                startEditing(rowIndex: rowIndex, colIndex: colIndex, currentValue: cell)
                                            }
                                            .overlay(
                                                Rectangle()
                                                    .frame(width: 1)
                                                    .foregroundColor(Color.gray.opacity(0.1)),
                                                alignment: .trailing
                                            )
                                    }
                                }
                                .overlay(
                                    Rectangle()
                                        .frame(height: 1)
                                        .foregroundColor(Color.gray.opacity(0.1)),
                                    alignment: .bottom
                                )
                            }
                        }
                    }
                    .background(Color.white)
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .shadow(color: .black.opacity(0.05), radius: 2, x: 0, y: 1)
                    .padding(1)
                }
                .frame(height: 220)
                .padding(.top, 4)
                .background(Color(.secondarySystemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .alert("Edit Cell", isPresented: $isEditingCell) {
                    TextField("Value", text: $editingValue)
                        .keyboardType(.asciiCapable) // avoid eager suggestions if possible
                    Button("Save") {
                        if let r = editingRow, let c = editingCol {
                            viewModel.updateTableCell(row: r, col: c, value: editingValue)
                        }
                        isEditingCell = false
                    }
                    Button("Cancel", role: .cancel) {
                        isEditingCell = false
                    }
                } message: {
                    Text("Enter new value for the cell.")
                }
            }
        }
    }

    private var forecastSettingsCard: some View {
        SectionCard {
            VStack(alignment: .leading, spacing: 12) {
                Text("Forecast Settings")
                    .font(.headline)
                HStack(spacing: 12) {
                    LabeledField(title: "Prediction Length", text: $viewModel.predictionLength)
                    LabeledField(title: "Quantiles", text: $viewModel.quantilesText)
                }
                HStack(spacing: 12) {
                    LabeledField(title: "ID Column", text: $viewModel.idColumn)
                    LabeledField(title: "Timestamp Column", text: $viewModel.timestampColumn)
                }
                LabeledField(title: "Target Column", text: $viewModel.targetColumn)

                if !viewModel.availableSeriesIds.isEmpty {
                    Picker("Series", selection: $viewModel.selectedSeriesId) {
                        ForEach(viewModel.availableSeriesIds, id: \.self) { seriesId in
                            Text(seriesId).tag(seriesId)
                        }
                    }
                    .pickerStyle(.menu)
                }
            }
        }
    }

    private var runButton: some View {
        Button {
            viewModel.runForecast()
        } label: {
            HStack {
                if viewModel.isRunning {
                    ProgressView()
                        .progressViewStyle(.circular)
                        .tint(.white)
                }
                Text(viewModel.isRunning ? "Running..." : "Run Forecast")
                    .font(.headline)
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background(zeticTeal)
            .foregroundStyle(.white)
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .disabled(viewModel.isRunning)
    }

    private var outputCard: some View {
        SectionCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("Forecast Output")
                        .font(.headline)
                    Spacer()
                    Picker("Mode", selection: $outputMode) {
                        ForEach(OutputMode.allCases, id: \.self) { mode in
                            Text(mode.title).tag(mode)
                        }
                    }
                    .pickerStyle(.segmented)
                }
                if !viewModel.hasForecast {
                    Text("Run forecast to generate output.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                } else if outputMode == .chart {
                    ForecastChart(
                        history: viewModel.historicalSeries,
                        forecast: viewModel.forecastSeries
                    )

                    .frame(minHeight: 220)
                    .id("Forecast-" + viewModel.outputCSV.hashValue.description)
                } else {
                    HStack {
                        Spacer()
                        Button("Export") {
                            exportDocument = CSVDocument(text: viewModel.outputCSV)
                            isExporting = true
                        }
                        .buttonStyle(.bordered)
                        .disabled(viewModel.outputCSV.isEmpty)
                    }
                    TextEditor(text: $viewModel.outputCSV)
                        .frame(minHeight: 160)
                        .padding(10)
                        .background(Color(.secondarySystemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            }
        }
    }
    private func startEditing(rowIndex: Int, colIndex: Int, currentValue: String) {
        editingRow = rowIndex
        editingCol = colIndex
        editingValue = currentValue
        isEditingCell = true
    }
}

private enum OutputMode: String, CaseIterable {
    case chart
    case table

    var title: String {
        switch self {
        case .chart: return "Chart"
        case .table: return "Table"
        }
    }
}

private struct ForecastChart: View {
    let history: [SeriesPoint]
    let forecast: [ForecastPoint]

    var body: some View {
        if #available(iOS 16.0, *) {
            Chart {
                // History Line (Blue)
                ForEach(history.indices, id: \.self) { idx in
                    LineMark(
                        x: .value("Index", idx),
                        y: .value("Value", history[idx].value)
                    )
                    .foregroundStyle(Color.blue)
                    .lineStyle(.init(lineWidth: 2))
                }
                
                // Vertical Separator
                if !history.isEmpty && !forecast.isEmpty {
                    RuleMark(x: .value("Now", history.count - 1))
                        .lineStyle(StrokeStyle(lineWidth: 1, dash: [5, 5]))
                        .foregroundStyle(Color.gray)
                        .annotation(position: .top, alignment: .leading) {
                            Text("Forecast Start")
                                .font(.caption2.bold())
                                .foregroundColor(.secondary)
                                .padding(.top, 20)
                        }
                }

                // Connector
                if let lastHist = history.last, let firstCast = forecast.first {
                    LineMark(
                        x: .value("Index", history.count - 1),
                        y: .value("Value", lastHist.value)
                    )
                    .foregroundStyle(Color.orange)
                    .lineStyle(.init(lineWidth: 3))
                    
                    LineMark(
                        x: .value("Index", history.count),
                        y: .value("Median", firstCast.q50)
                    )
                    .foregroundStyle(Color.orange)
                    .lineStyle(.init(lineWidth: 3))
                }

                // Forecast
                ForEach(forecast.indices, id: \.self) { idx in
                    let xIndex = history.count + idx
                    
                    AreaMark(
                        x: .value("Index", xIndex),
                        yStart: .value("Q10", forecast[idx].q10),
                        yEnd: .value("Q90", forecast[idx].q90)
                    )
                    .foregroundStyle(Color.orange.opacity(0.3))

                    LineMark(
                        x: .value("Index", xIndex),
                        y: .value("Median", forecast[idx].q50)
                    )
                    .foregroundStyle(Color.orange)
                    .lineStyle(.init(lineWidth: 3))
                    .symbol(.circle) // Add dots to forecast points for emphasis
                    .symbolSize(30)
                }
            }
            .chartXAxis(.hidden)
            .chartYAxis {
                AxisMarks(position: .leading)
            }
            .chartYScale(domain: .automatic(includesZero: false))
        } else {
            Text("Chart requires iOS 16+.")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
    }
}

private struct SectionCard<Content: View>: View {
    let content: Content

    init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            content
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.systemBackground).opacity(0.9))
        .clipShape(RoundedRectangle(cornerRadius: 20))
        .shadow(color: .black.opacity(0.12), radius: 10, x: 0, y: 6)
    }
}

private struct LabeledField: View {
    let title: String
    @Binding var text: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
            TextField(title, text: $text)
                .textFieldStyle(.roundedBorder)
        }
    }
}

private struct ModelSettingsSheet: View {
    @ObservedObject var viewModel: ChronosForecastViewModel

    var body: some View {
        if #available(iOS 16.0, *) {
            NavigationStack {
                Form {
                    Section("Access") {
                        TextField("Token Key", text: $viewModel.tokenKey)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                        Text("Token is only stored in memory for this session.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    
                    Section("Model") {
                        TextField("Model Name", text: $viewModel.modelName)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                        TextField("Version", text: $viewModel.modelVersion)
                            .keyboardType(.numberPad)
                    }

                    Section("Normalization") {
                        TextField("Mode (asinh_zscore | zscore | none)", text: $viewModel.normalizationMode)
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                        Text("Default: asinh_zscore to match Chronos instance normalization.")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }

                }
                .navigationTitle("Model Settings")
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Load") {
                            viewModel.loadModel()
                            viewModel.showSettings = false
                        }
                    }
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Close") {
                            viewModel.showSettings = false
                        }
                    }
                }
            }
        } else {
            // Fallback on earlier versions
        }
    }
}

struct CSVDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.commaSeparatedText, .plainText] }
    var text: String

    init(text: String = "") {
        self.text = text
    }

    init(configuration: ReadConfiguration) throws {
        guard let data = configuration.file.regularFileContents,
              let string = String(data: data, encoding: .utf8) else {
            throw CocoaError(.fileReadCorruptFile)
        }
        text = string
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        let data = Data(text.utf8)
        return .init(regularFileWithContents: data)
    }
}
