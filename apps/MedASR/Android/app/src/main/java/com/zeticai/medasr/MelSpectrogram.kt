package com.zeticai.medasr

import kotlin.math.*

object MelSpectrogram {
    
    // Config: From processor_config.json
    // feature_size=128
    // hop_length=160
    // n_fft=512
    // sampling_rate=16000
    // win_length=400
    private const val SAMPLE_RATE = 16000
    private const val N_FFT = 512
    private const val HOP_LENGTH = 160
    private const val WIN_LENGTH = 400
    private const val N_MELS = 128
    private const val F_MIN = 0.0
    private const val F_MAX = 8000.0 // Nyquist

    // Precomputed Filters
    private val filters: Array<FloatArray> by lazy {
        createMelFilterBank(SAMPLE_RATE, N_FFT, N_MELS, F_MIN, F_MAX)
    }

    // Precomputed Window
    private val window: FloatArray by lazy {
        createHannWindow(WIN_LENGTH)
    }
    
    // Main extraction
    fun extract(
        pcmData: FloatArray // Mono [-1.0, 1.0]
    ): FloatArray {
        // 1. Frame Signal
        // Determine number of frames
        // (Length - Win) / Hop + 1 ?? Or partial frames?
        // Typically center padding is used, but config says "padding_side": "right"
        // Let's assume standard framing. If Length < Win, pad?
        // We will just process valid frames
        
        val numFrames = (pcmData.size - WIN_LENGTH) / HOP_LENGTH + 1
        if (numFrames <= 0) return FloatArray(0)
        
        val totalFeatures = numFrames * N_MELS
        val result = FloatArray(totalFeatures)

        // Pre-allocate FFT input/output buffers
        val fftReal = FloatArray(N_FFT)
        val fftImag = FloatArray(N_FFT)
        // Power Spectrum (Mag ^ 2 / N)
        val powerSpectrum = FloatArray(N_FFT / 2 + 1)
        
        for (i in 0 until numFrames) {
            val offset = i * HOP_LENGTH
            
            // Apply Window
            for (j in 0 until N_FFT) {
                if (j < WIN_LENGTH) {
                    fftReal[j] = pcmData[offset + j] * window[j]
                } else {
                    fftReal[j] = 0f // Zero pad
                }
                fftImag[j] = 0f
            }
            
            // Compute FFT
            FFT.compute(fftReal, fftImag)
            
            // Compute Power Spectrum
            for (k in 0..N_FFT / 2) {
                val re = fftReal[k]
                val im = fftImag[k]
                // Power = (re^2 + im^2)
                // Note: HF/librosa behavior: Power=2.0 (amplitude^2). Scale by 1/N? No, usually raw magnitude squared.
                // Let's stick to raw magnitude squared unless we see normalization.
                powerSpectrum[k] = (re * re + im * im)
            }

            // Apply Mel Filter Bank
            for (m in 0 until N_MELS) {
                var melEnergy = 0.0
                val filter = filters[m]
                // Dot product
                for (k in 0 until filter.size) {
                    melEnergy += powerSpectrum[k] * filter[k]
                }
                
                // Log Scaling: ln(x + 1e-10) typically, or 10*log10.
                // HF "LasrFeatureExtractor" usually does log(mel + epsilon).
                // Matches Python reference min value of -11.51 (ln(1e-5))
                val logMel = ln(max(melEnergy, 1e-5)).toFloat()
                
                result[i * N_MELS + m] = logMel
            }
        }
        
        return result
    }

    // --- Helpers ---

    private fun createHannWindow(size: Int): FloatArray {
        val w = FloatArray(size)
        for (i in 0 until size) {
            w[i] = (0.5 * (1 - cos(2.0 * PI * i / (size - 1)))).toFloat() // Standard Hann
        }
        return w
    }

    private fun createMelFilterBank(sr: Int, nFft: Int, nMels: Int, fMin: Double, fMax: Double): Array<FloatArray> {
        // Mel points
        val mMin = hzToMel(fMin)
        val mMax = hzToMel(fMax)
        
        val mPoints = DoubleArray(nMels + 2)
        val hPoints = DoubleArray(nMels + 2)
        val fPoints = IntArray(nMels + 2) // Bin indices
        
        for (i in 0 until nMels + 2) {
            mPoints[i] = mMin + (mMax - mMin) * i / (nMels + 1)
            hPoints[i] = melToHz(mPoints[i])
            fPoints[i] = floor((nFft + 1) * hPoints[i] / sr).toInt()
        }
        
        val weights = Array(nMels) { FloatArray(nFft / 2 + 1) }
        
        for (m in 0 until nMels) {
            val left = fPoints[m]
            val center = fPoints[m + 1]
            val right = fPoints[m + 2]
            
            for (k in left until center) {
                weights[m][k] = ((k - left).toDouble() / (center - left)).toFloat()
            }
            for (k in center until right) {
                weights[m][k] = ((right - k).toDouble() / (right - center)).toFloat()
            }
        }
        
        return weights
    }
    
    private fun hzToMel(hz: Double): Double = 2595.0 * log10(1.0 + hz / 700.0)
    private fun melToHz(mel: Double): Double = 700.0 * (10.0.pow(mel / 2595.0) - 1.0)
    
    
    // --- Detailed FFT Implementation (Cooley-Tukey Radix-2) ---
    // Since N=512 is Power of 2, standard recursive or iterative FFT works.
    object FFT {
        fun compute(real: FloatArray, imag: FloatArray) {
            val n = real.size
            // Bit Reversal Permutation
            var j = 0
            for (i in 0 until n - 1) {
                if (i < j) {
                    val tr = real[j]; real[j] = real[i]; real[i] = tr
                    val ti = imag[j]; imag[j] = imag[i]; imag[i] = ti
                }
                var k = n / 2
                while (k <= j) {
                    j -= k
                    k /= 2
                }
                j += k
            }
            
            // Butterfly
            var step = 1
            while (step < n) {
                val jump = step * 2
                val deltaAngle = -PI / step
                val sine = sin(deltaAngle * 0.5)
                // Re(e^ix) = cos(x), Im(e^ix) = sin(x)
                // W_N^k logic optimization
                // Typically: w_r = 1, w_i = 0.
                // Using recurrence relation.
                
                var wReal = 1.0
                var wImag = 0.0
                // Precompute trig for loop stability? Or just standard iteration.
                // Standard Iteration:
                val alpha = 2.0 * sin(deltaAngle * 0.5).pow(2)
                val beta = sin(deltaAngle)
                
                for (k in 0 until step) {
                     for (i in k until n step jump) {
                         val tReal = wReal * real[i + step] - wImag * imag[i + step]
                         val tImag = wReal * imag[i + step] + wImag * real[i + step]
                         
                         real[i + step] = (real[i] - tReal).toFloat()
                         imag[i + step] = (imag[i] - tImag).toFloat()
                         real[i] = (real[i] + tReal).toFloat()
                         imag[i] = (imag[i] + tImag).toFloat()
                     }
                     // Update w
                     val wTemp = wReal
                     wReal = wReal - (alpha * wReal + beta * wImag)
                     wImag = wImag - (alpha * wImag - beta * wTemp)
                }
                step = jump
            }
        }
    }
}
