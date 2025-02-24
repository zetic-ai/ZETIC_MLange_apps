import Foundation

struct Message: Identifiable, Equatable {
    let id = UUID()
    var content: String
    let isFromUser: Bool
    let timestamp = Date()
}
