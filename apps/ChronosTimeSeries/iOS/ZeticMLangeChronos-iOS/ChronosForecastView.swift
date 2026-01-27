import SwiftUI
import UniformTypeIdentifiers
import Charts

struct ChronosForecastView: View {
    @StateObject private var viewModel = ChronosForecastViewModel()
    @State private var isImporting = false
    @State private var isExporting = false
    @State private var exportDocument = CSVDocument(text: "")
    @State private var outputMode: OutputMode = .chart
    
    var body: some View {
            ZStack {
            LinearGradient(
                colors: [Color(.systemIndigo).opacity(0.7), Color(.systemTeal).opacity(0.7)],
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
                    Text("Chronos Time Series")
                        .font(.title.bold())
                        .foregroundStyle(.white)
                    Text("Forecast future values on-device with Zetic MLange")
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.85))
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var modelStatusCard: some View {
        SectionCard {
            HStack {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Model")
                        .font(.headline)
                    Text(viewModel.modelStatusText)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                Button("Settings") {
                    viewModel.showSettings = true
                }
                .buttonStyle(.borderedProminent)
                .tint(.indigo)
            }
            if let error = viewModel.errorMessage {
                Text(error)
                    .font(.footnote)
                    .foregroundStyle(.red)
            }
        }
    }

    private var inputCard: some View {
        SectionCard {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("Input CSV")
                        .font(.headline)
                    Spacer()
                    Button("Import") {
                        isImporting = true
                    }
                    .buttonStyle(.bordered)
                    
                    Menu {
                        ForEach(ExampleData.allValues) { example in
                            Button(example.name) {
                                viewModel.loadExample(example)
                            }
                        }
                    } label: {
                        Image(systemName: "book.pages")
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
                TextEditor(text: $viewModel.csvText)
                    .frame(minHeight: 140)
                    .padding(10)
                    .background(Color(.secondarySystemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
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
            .background(Color(.systemIndigo))
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
                        history: [],
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
                ForEach(history.indices, id: \.self) { idx in
                    LineMark(
                        x: .value("Index", idx),
                        y: .value("Value", history[idx].value)
                    )
                    .foregroundStyle(Color.blue)
                    .lineStyle(.init(lineWidth: 2))
                }
                ForEach(forecast.indices, id: \.self) { idx in
                    let xIndex = idx
                    AreaMark(
                        x: .value("Index", xIndex),
                        yStart: .value("Q10", forecast[idx].q10),
                        yEnd: .value("Q90", forecast[idx].q90)
                    )
                    .foregroundStyle(Color.orange.opacity(0.2))

                    LineMark(
                        x: .value("Index", xIndex),
                        y: .value("Median", forecast[idx].q50)
                    )
                    .foregroundStyle(Color.orange)
                    .lineStyle(.init(lineWidth: 2, dash: [4, 3]))
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
