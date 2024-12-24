import Foundation
import SwiftUI

struct FeatureItemView: View {
    @ObservedObject var featureItem: FeatureItem
    
    var body: some View {
        
        HStack {
            VStack(alignment: .leading) {
                Text(featureItem.name)
                Text(modelStatusToString(featureItem.modelStatus)).font(.subheadline).foregroundColor(.gray)
            }
        }
    }
    
    func modelStatusToString(_ status: ModelStatus) -> String {
        switch status {
        case .notReady:
            return "Model is not ready to use"
        case .fetching:
            return "Fetching model files from the server..."
        case .ready:
            return "Model is ready to use"
        }
    }
}
