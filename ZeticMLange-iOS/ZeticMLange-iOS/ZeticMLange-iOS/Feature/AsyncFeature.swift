import Foundation

class AsyncFeature<Input: AsyncFeatureInput, Output: AsyncFeatureOutput>: ObservableObject {
    private var isClosing = false
    private let operationQueue: DispatchQueue
    private let operationGroup = DispatchGroup()
    private var isRunning = false
    
    private var isProcessing = false
    
    init(label: String) {
        self.operationQueue = DispatchQueue(label: label)
    }
    
    func process(input: Input) -> Output {
        fatalError("Subclasses must implement process(input:)")
    }
    
    func handleOutput(_ output: Output) {
        fatalError("Subclasses must implement handleOutput(_:)")
    }
    
    func run(with input: Input) {
        guard !isClosing else { return }
        
        guard !isRunning else { return }
        
        isRunning = true
        operationGroup.enter()
        
        operationQueue.async { [weak self] in
            guard let self = self, !self.isClosing else {
                self?.isRunning = false
                self?.operationGroup.leave()
                return
            }
            
            let output = self.process(input: input)
            
            DispatchQueue.main.async {
                guard !self.isClosing else {
                    self.isRunning = false
                    self.operationGroup.leave()
                    return
                }
                self.handleOutput(output)
                self.isRunning = false
                self.operationGroup.leave()
            }
        }
    }
    
    func waitForPendingOperations(completion: @escaping () -> Void) {
        isClosing = true
        operationQueue.async { [weak self] in
            self?.operationGroup.wait()
            DispatchQueue.main.async {
                completion()
            }
        }
    }
    
    func close() {
        isClosing = true
    }
}

protocol AsyncFeatureInput {}
protocol AsyncFeatureOutput {}
