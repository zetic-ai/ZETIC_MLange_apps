import SwiftUI

struct YAMNetView: View {
    private let classes = readCSVFromBundle()
    @StateObject private var yamnet = YAMNet(label: "yamnet")
    
    var body: some View {
        GeometryReader { geometry in
            ZStack {
                if let scores = yamnet.scores {
                    let scoredClasses = scores.map {
                        "\(classes[$0.index]) : \($0.score)"
                    }.joined(separator: "\n")
                    Canvas { context, size in
                        context.draw(Text(scoredClasses), in: CGRect(x: 100, y: 100, width: 2000, height: 2000))
                    }.frame(width: geometry.size.width, height: geometry.size.height)
                        .clipped()
                }
            }
        }
        .ignoresSafeArea()
        .onAppear {
            yamnet.startProcessing()
        }
        .onDisappear {
            yamnet.waitForPendingOperations {
                yamnet.stopProcessing()
            }
        }
    }
    
    private static func readCSVFromBundle() -> [String] {
        guard let fileURL = Bundle.main.url(forResource: "yamnet_class_map", withExtension: "csv") else {
            print("CSV file not found in bundle.")
            return []
        }
        
        do {
            let csvContent = try String(contentsOf: fileURL, encoding: .utf8)
            
            return getLastElementsFromCSV(csvContent)
        } catch {
            print("Error reading CSV file: \(error)")
            return []
        }
    }
    
    private static func getLastElementsFromCSV(_ csvContent: String) -> [String] {
        let lines = csvContent.components(separatedBy: .newlines)
        
        let nonEmptyLines = lines.filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
        
        var lastElements = [String]()
        
        for line in nonEmptyLines {
            let values = line.components(separatedBy: ",")
            
            lastElements.append(values.dropFirst(2).joined(separator: ""))
        }
        
        return Array(lastElements.dropFirst())
    }
    
}
