package com.zeticai.zeticmlangewhisper_android

import kotlin.math.exp
import kotlin.random.Random

class ProbabilityUtils {
    companion object {
        fun softmax(logits: FloatArray): FloatArray {
            val maxLogit = logits.maxOrNull() ?: 0f
            val expLogits = FloatArray(logits.size) { i ->
                exp((logits[i] - maxLogit).toDouble()).toFloat()
            }
            val sumExp = expLogits.sum()
            return FloatArray(logits.size) { i -> expLogits[i] / sumExp }
        }

        fun sampleFromDistribution(probs: FloatArray): Int {
            val random = Random.Default.nextFloat()
            var cumSum = 0f
            for (i in probs.indices) {
                cumSum += probs[i]
                if (random < cumSum) {
                    return i
                }
            }
            return probs.size - 1
        }

        fun argmax(array: FloatArray): Int {
            var maxIndex = 0
            var maxValue = array[0]
            for (i in 1 until array.size) {
                if (array[i] > maxValue) {
                    maxIndex = i
                    maxValue = array[i]
                }
            }
            return maxIndex
        }
    }
}
