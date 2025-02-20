import SwiftUI

struct ContentView: View {
    @StateObject private var viewModel = BenchmarkViewModel()
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                TextField("Enter Model Key", text: $viewModel.modelKey)
                    .textFieldStyle(RoundedBorderTextFieldStyle())
                    .padding(.horizontal)
                
                Button(action: viewModel.runBenchmark) {
                    Text("Run Benchmark")
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .cornerRadius(10)
                }
                .padding(.horizontal)
                .disabled(viewModel.isLoading)
                
                if viewModel.isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle())
                }
                
                List(viewModel.results, id: \.target) { result in
                    BenchmarkResultRow(result: result)
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Text("ZETIC.MLange Benchmark")
                        .font(.title)
                }
            }
            
            .alert("Error", isPresented: .constant(viewModel.errorMessage != nil)) {
                Button("OK") {
                    viewModel.errorMessage = nil
                }
            } message: {
                if let errorMessage = viewModel.errorMessage {
                    Text(errorMessage)
                }
            }
            .background(Color.clear.contentShape(Rectangle()))
            .onTapGesture {
                hideKeyboard()
            }
        }
    }
}

extension View {
    func hideKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }
}
