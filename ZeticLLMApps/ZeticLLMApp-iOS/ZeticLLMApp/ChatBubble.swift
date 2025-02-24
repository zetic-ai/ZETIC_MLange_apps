import Foundation
import SwiftUI

struct ChatBubble: View {
    let message: Message
    
    var body: some View {
        HStack {
            if message.isFromUser { Spacer() }
            
            VStack(alignment: message.isFromUser ? .trailing : .leading) {
                Text(message.content)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(message.isFromUser ? Color.blue : Color(.systemGray6))
                    .foregroundColor(message.isFromUser ? .white : .primary)
                    .cornerRadius(16)
                
                Text(message.timestamp, style: .time)
                    .font(.caption2)
                    .foregroundColor(.gray)
                    .padding(.horizontal, 4)
            }
            
            if !message.isFromUser { Spacer() }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
    }
}
