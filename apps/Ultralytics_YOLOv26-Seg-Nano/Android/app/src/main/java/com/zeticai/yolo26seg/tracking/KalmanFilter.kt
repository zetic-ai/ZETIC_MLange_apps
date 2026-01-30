package com.zeticai.yolo26seg.tracking

import kotlin.math.pow

class KalmanFilter {
    private val nDimensions = 4
    private val dt = 1.0f

    // F: Transition matrix (8x8)
    // Maps [x, y, a, h, vx, vy, va, vh] -> [x+vx, y+vy, a+va, h+vh, vx, vy, va, vh]
    private fun createMotionMatrix(): Array<FloatArray> {
        val matrix = Array(2 * nDimensions) { FloatArray(2 * nDimensions) }
        for (i in 0 until 2 * nDimensions) {
            matrix[i][i] = 1.0f
            if (i < nDimensions) {
                matrix[i][i + nDimensions] = dt
            }
        }
        return matrix
    }
    
    // H: Measurement matrix (4x8)
    // Maps [x, y, a, h, vx, vy, va, vh] -> [x, y, a, h]
    private fun createUpdateMatrix(): Array<FloatArray> {
        val matrix = Array(nDimensions) { FloatArray(2 * nDimensions) }
        for (i in 0 until nDimensions) {
            matrix[i][i] = 1.0f
        }
        return matrix
    }

    // Standard deviation weights for covariance
    private val stdWeightPosition = 1.0f / 20
    private val stdWeightVelocity = 1.0f / 160

    private val motionMatrix = createMotionMatrix() // F
    private val updateMatrix = createUpdateMatrix() // H

    fun initiate(measurement: FloatArray): Pair<FloatArray, Array<FloatArray>> {
        val mean = FloatArray(8)
        for (i in 0 until 4) mean[i] = measurement[i]
        // Velocity initialized to 0

        val std = floatArrayOf(
            2 * stdWeightPosition * measurement[3],
            2 * stdWeightPosition * measurement[3],
            1e-2f,
            2 * stdWeightPosition * measurement[3],
            10 * stdWeightVelocity * measurement[3],
            10 * stdWeightVelocity * measurement[3],
            1e-5f,
            10 * stdWeightVelocity * measurement[3]
        )

        val covariance = Array(8) { FloatArray(8) }
        for (i in 0 until 8) {
            covariance[i][i] = std[i].pow(2)
        }
        return Pair(mean, covariance)
    }

    fun predict(mean: FloatArray, covariance: Array<FloatArray>): Pair<FloatArray, Array<FloatArray>> {
        // x' = Fx
        val newMean = matrixMultiplyVector(motionMatrix, mean)
        
        // P' = FPF^T + Q
        val stdPos = stdWeightPosition * mean[3]
        val stdVel = stdWeightVelocity * mean[3]
        
        val motionCov = floatArrayOf(stdPos, stdPos, 1e-2f, stdPos, stdVel, stdVel, 1e-5f, stdVel)
        val q = Array(8) { FloatArray(8) }
        for (i in 0 until 8) q[i][i] = motionCov[i].pow(2)

        // FPF^T
        val fP = matrixMultiply(motionMatrix, covariance)
        val fPfT = matrixMultiply(fP, transpose(motionMatrix))
        
        val newCovariance = matrixAdd(fPfT, q)
        return Pair(newMean, newCovariance)
    }

    fun project(mean: FloatArray, covariance: Array<FloatArray>): Pair<FloatArray, Array<FloatArray>> {
        // Hx
        val projectedMean = matrixMultiplyVector(updateMatrix, mean)
        
        // HPH^T + R
        val std = floatArrayOf(
            stdWeightPosition * mean[3],
            stdWeightPosition * mean[3],
            1e-1f,
            stdWeightPosition * mean[3]
        )
        val r = Array(4) { FloatArray(4) }
        for (i in 0 until 4) r[i][i] = std[i].pow(2)

        val hP = matrixMultiply(updateMatrix, covariance)
        val hPhT = matrixMultiply(hP, transpose(updateMatrix))
        
        val projectedCov = matrixAdd(hPhT, r)
        return Pair(projectedMean, projectedCov)
    }

    fun update(mean: FloatArray, covariance: Array<FloatArray>, measurement: FloatArray): Pair<FloatArray, Array<FloatArray>> {
        val (projectedMean, projectedCov) = project(mean, covariance)

        // K = PH^T * S^-1 (where S = projectedCov)
        // Here we use solving linear equations or simplified Cholesky. 
        // For simplicity in Kotlin without external math lib, we implement basic matrix inverse for 4x4 or solve.
        // Actually, let's implement a simple Gaussian elimination or reuse 'projectedCov' inverse.
        // Or better: Kalman Gain K = P * H.T * inv(S)
        
        // 1. Chol decomposition of projectedCov (S)
        val b = matrixSubtractVector(measurement, projectedMean) // y = z - Hx
        
        // Kalman Gain Calculation
        val hT = transpose(updateMatrix)
        val pHT = matrixMultiply(covariance, hT) // 8x4
        val sInv = invert4x4(projectedCov) // 4x4
        val k = matrixMultiply(pHT, sInv) // 8x4

        // x_new = x + Ky
        val ky = matrixMultiplyVector(k, b)
        val newMean = matrixAddVector(mean, ky)
        
        // P_new = P - KHP (or P - KS K^T ?) -> P - KHP is simpler
        // K * H -> 8x8
        val kh = matrixMultiply(k, updateMatrix)
        val khp = matrixMultiply(kh, covariance)
        val newCovariance = matrixSubtract(covariance, khp)

        return Pair(newMean, newCovariance)
    }

    // --- Matrix Utils ---
    private fun matrixMultiplyVector(a: Array<FloatArray>, b: FloatArray): FloatArray {
        val rows = a.size
        val cols = a[0].size
        val result = FloatArray(rows)
        for (i in 0 until rows) {
            var sum = 0f
            for (j in 0 until cols) sum += a[i][j] * b[j]
            result[i] = sum
        }
        return result
    }

    private fun matrixMultiply(a: Array<FloatArray>, b: Array<FloatArray>): Array<FloatArray> {
        val rowsA = a.size
        val colsA = a[0].size
        val colsB = b[0].size
        val result = Array(rowsA) { FloatArray(colsB) }
        for (i in 0 until rowsA) {
            for (j in 0 until colsB) {
                var sum = 0f
                for (k in 0 until colsA) sum += a[i][k] * b[k][j]
                result[i][j] = sum
            }
        }
        return result
    }
    
    private fun matrixAdd(a: Array<FloatArray>, b: Array<FloatArray>): Array<FloatArray> {
        val n = a.size
        val m = a[0].size
        val res = Array(n) { FloatArray(m) }
        for (i in 0 until n) for (j in 0 until m) res[i][j] = a[i][j] + b[i][j]
        return res
    }
    
    private fun matrixSubtract(a: Array<FloatArray>, b: Array<FloatArray>): Array<FloatArray> {
        val n = a.size
        val m = a[0].size
        val res = Array(n) { FloatArray(m) }
        for (i in 0 until n) for (j in 0 until m) res[i][j] = a[i][j] - b[i][j]
        return res
    }

    private fun matrixAddVector(a: FloatArray, b: FloatArray): FloatArray {
        val res = FloatArray(a.size)
        for (i in a.indices) res[i] = a[i] + b[i]
        return res
    }

    private fun matrixSubtractVector(a: FloatArray, b: FloatArray): FloatArray {
        val res = FloatArray(a.size)
        for (i in a.indices) res[i] = a[i] - b[i]
        return res
    }

    private fun transpose(a: Array<FloatArray>): Array<FloatArray> {
        val rows = a.size
        val cols = a[0].size
        val res = Array(cols) { FloatArray(rows) }
        for (i in 0 until rows) for (j in 0 until cols) res[j][i] = a[i][j]
        return res
    }

    // Simplified Inverse for 4x4 specific to Kalman S matrix (Symmetric Positive Definite)
    // Using Cholesky decomposition or Gaussian elimination approach
    private fun invert4x4(m: Array<FloatArray>): Array<FloatArray> {
        val n = 4
        val aug = Array(n) { FloatArray(2 * n) }
        for (i in 0 until n) {
            for (j in 0 until n) aug[i][j] = m[i][j]
            aug[i][i + n] = 1.0f
        }
        
        for (i in 0 until n) {
            var pivot = aug[i][i]
            for (j in 0 until 2 * n) aug[i][j] /= pivot
            for (k in 0 until n) {
                if (k != i) {
                    val factor = aug[k][i]
                    for (j in 0 until 2 * n) aug[k][j] -= factor * aug[i][j]
                }
            }
        }
        
        val res = Array(n) { FloatArray(n) }
        for (i in 0 until n) for (j in 0 until n) res[i][j] = aug[i][j + n]
        return res
    }
}
