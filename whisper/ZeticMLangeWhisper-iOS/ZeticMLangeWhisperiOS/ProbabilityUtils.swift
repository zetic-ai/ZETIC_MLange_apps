import Foundation

class ProbabilityUtils {
    static func softmax(_ logits: [Float]) -> [Float] {
        guard let maxLogit = logits.max() else { return [] }

        let expLogits = logits.map { Float(exp(Double($0 - maxLogit))) }
        let sumExp = expLogits.reduce(0, +)

        return expLogits.map { $0 / sumExp }
    }

    static func sampleFromDistribution(_ probs: [Float]) -> Int {
        let random = Float.random(in: 0 ..< 1)
        var cumSum: Float = 0

        for (index, prob) in probs.enumerated() {
            cumSum += prob
            if random < cumSum {
                return index
            }
        }

        return probs.count - 1
    }

    static func argmax(_ array: [Float]) -> Int {
        guard !array.isEmpty else { return 0 }

        var maxIndex = 0
        var maxValue = array[0]

        for (index, value) in array.enumerated() {
            if value > maxValue {
                maxIndex = index
                maxValue = value
            }
        }

        return maxIndex
    }
}
