//
//  ContentView.swift
//  TextAnonymizer
//
//  Main UI for text anonymization
//

import SwiftUI
import UniformTypeIdentifiers

struct ContentView: View {
    @StateObject private var viewModel = AnonymizerViewModel()
    @State private var inputText = ""
    @State private var showingShareSheet = false
    @FocusState private var inputFocused: Bool
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 20) {
                // Model Loading Status
                if !viewModel.isModelLoaded {
                    HStack {
                        ProgressView()
                        Text("Loading model...")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    .padding()
                }
                
                // Input Section
                VStack(alignment: .leading, spacing: 12) {
                    Text("Paste your text here:")
                        .font(.headline)
                        .foregroundColor(.secondary)
                    
                    TextEditor(text: $inputText)
                        .frame(height: 200)
                        .padding(8)
                        .background(Color(.systemGray6))
                        .cornerRadius(12)
                        .focused($inputFocused)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color(.systemGray4), lineWidth: 1)
                        )
                }
                .padding(.horizontal)
                
                // Anonymize Button
                Button(action: {
                    viewModel.anonymizeText(inputText)
                }) {
                    HStack {
                        if viewModel.isProcessing {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        }
                        Text(viewModel.isProcessing ? "Anonymizing..." : "Anonymize Text")
                            .font(.headline)
                            .fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(inputText.isEmpty || viewModel.isProcessing || !viewModel.isModelLoaded ? Color.gray : Color.blue)
                    .foregroundColor(.white)
                    .cornerRadius(12)
                }
                .disabled(inputText.isEmpty || viewModel.isProcessing || !viewModel.isModelLoaded)
                .padding(.horizontal)
                
                // Output Section
                if !viewModel.anonymizedText.isEmpty {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Anonymized Text:")
                            .font(.headline)
                            .foregroundColor(.secondary)
                        
                        ScrollView {
                            Text(viewModel.anonymizedText)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding()
                                .background(Color(.systemGray6))
                                .cornerRadius(12)
                        }
                        .frame(height: 200)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color(.systemGray4), lineWidth: 1)
                        )
                        
                        // Copy and Share Buttons
                        HStack(spacing: 12) {
                            Button(action: {
                                UIPasteboard.general.string = viewModel.anonymizedText
                            }) {
                                HStack {
                                    Image(systemName: "doc.on.doc")
                                    Text("Copy")
                                }
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(Color.blue)
                                .foregroundColor(.white)
                                .cornerRadius(10)
                            }
                            
                            Button(action: {
                                showingShareSheet = true
                            }) {
                                HStack {
                                    Image(systemName: "square.and.arrow.up")
                                    Text("Share")
                                }
                                .frame(maxWidth: .infinity)
                                .padding()
                                .background(Color.green)
                                .foregroundColor(.white)
                                .cornerRadius(10)
                            }
                        }
                    }
                    .padding(.horizontal)
                }
                
                Spacer(minLength: 24)
            }
            .padding(.vertical)
            }
            .navigationTitle("Text Anonymizer")
            .scrollDismissesKeyboard(.interactively)
            .toolbar {
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Done") {
                        inputFocused = false
                    }
                }
            }
            .sheet(isPresented: $showingShareSheet) {
                ShareSheet(items: [viewModel.anonymizedText])
            }
            .alert("Error", isPresented: $viewModel.showingError) {
                Button("OK") {
                    viewModel.showingError = false
                }
            } message: {
                Text(viewModel.errorMessage)
            }
        }
    }
}

struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]
    
    func makeUIViewController(context: Context) -> UIActivityViewController {
        let controller = UIActivityViewController(
            activityItems: items,
            applicationActivities: nil
        )
        return controller
    }
    
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

