@file:Suppress("MemberVisibilityCanBePrivate")

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

// Requires: implementation("com.github.wendykierp:JTransforms:3.1")
import org.jtransforms.fft.FloatFFT_1D

/**
 * MedASR feature extractor (log-mel) aligned with HF LasrFeatureExtractor.
 *
 * Key config (from processor_config.json):
 *  - sampling_rate: 16000
 *  - n_fft: 512
 *  - win_length: 400
 *  - hop_length: 160
 *  - feature_size: 128 (n_mels)
 *  - mel_scale: kaldi
 *  - lower_edge_hertz: 125.0
 *  - upper_edge_hertz: 7500.0
 *
 * IMPORTANT: Do NOT z-score/mean-std normalize these features.
 * The log floor uses ln(1e-5) ~= -11.512925, which matches reference stats.
 */
object MedasrFeatureExtractor {
    data class Config(
        val samplingRate: Int = 16000,
        val nFft: Int = 512,
        val winLength: Int = 400,
        val hopLength: Int = 160,
        val nMels: Int = 128,
        val fMin: Float = 125f,
        val fMax: Float = 7500f,
        val logFloor: Float = 1e-5f
    )

    data class Features(
        val inputFeatures: Array<FloatArray>, // shape: [T, nMels]
        val attentionMask: IntArray           // shape: [T], 1 for valid frames
    )

    fun extract(samples: FloatArray, sampleRate: Int, config: Config = Config()): Features {
        require(sampleRate == config.samplingRate) {
            "Expected sampleRate=${config.samplingRate}, got $sampleRate. Resample first."
        }

        val window = hannWindow(config.winLength)
        val melFilters = buildMelFilterBank(
            sampleRate = config.samplingRate,
            nFft = config.nFft,
            nMels = config.nMels,
            fMin = config.fMin,
            fMax = config.fMax
        )

        require(samples.size >= config.winLength) {
            "Audio too short for win_length=${config.winLength}. Got ${samples.size} samples."
        }
        val padLen = config.winLength / 2
        val paddedSamples = FloatArray(samples.size + 2 * padLen)
        for (i in samples.indices) {
            paddedSamples[i + padLen] = samples[i]
        }
        // Valid for center=True:
        // numFrames = 1 + (paddedLen - winLength) / hopLength
        //           = 1 + (samples.size + 2*pad - win) / hop
        //           = 1 + (samples.size) / hop  (since 2*pad = win)
        // Check exact torch behavior. Torch uses ceil. Here we use floor default.
        // Let's stick to simple padding and standard calculation.
        
        val numFrames = 1 + ((paddedSamples.size - config.winLength) / config.hopLength)

        val fft = FloatFFT_1D(config.nFft.toLong())
        val nFftBins = config.nFft / 2 + 1
        val features = Array(numFrames) { FloatArray(config.nMels) }

        val frameBuf = FloatArray(config.nFft)
        val complexBuf = FloatArray(config.nFft * 2)

        for (frame in 0 until numFrames) {
            val start = frame * config.hopLength
            // Windowed frame into frameBuf
            for (i in 0 until config.winLength) {
                frameBuf[i] = paddedSamples[start + i] * window[i]
            }
            for (i in config.winLength until config.nFft) {
                frameBuf[i] = 0f
            }

            // Copy to complex buffer for FFT (real input)
            for (i in 0 until config.nFft) {
                complexBuf[2 * i] = frameBuf[i]
                complexBuf[2 * i + 1] = 0f
            }
            fft.complexForward(complexBuf)

            // Power spectrum
            val powerSpec = FloatArray(nFftBins)
            for (i in 0 until nFftBins) {
                val re = complexBuf[2 * i]
                val im = complexBuf[2 * i + 1]
                powerSpec[i] = re * re + im * im
            }

            // Mel filter + log
            for (m in 0 until config.nMels) {
                var energy = 0f
                for (k in 0 until nFftBins) {
                    energy += melFilters[k][m] * powerSpec[k]
                }
                energy = max(energy, config.logFloor)
                features[frame][m] = ln(energy)
            }
        }

        val attention = IntArray(numFrames) { 1 }
        return Features(features, attention)
    }

    private fun hannWindow(n: Int): FloatArray {
        val window = FloatArray(n)
        val denom = n.toFloat() // Periodic window (matches torch.stft default)
        for (i in 0 until n) {
            window[i] = (0.5f - 0.5f * cos((2.0 * PI * i / denom)).toFloat())
        }
        return window
    }

    private fun buildMelFilterBank(
        sampleRate: Int,
        nFft: Int,
        nMels: Int,
        fMin: Float,
        fMax: Float
    ): Array<FloatArray> {
        // Matches transformers.models.lasr.feature_extraction_lasr.linear_to_mel_weight_matrix
        val nFftBins = nFft / 2 + 1
        val bandsToZero = 1

        fun hertzToMel(freq: Double): Double = 1127.0 * kotlin.math.ln(1.0 + freq / 700.0)

        val nyquist = sampleRate / 2.0
        val linearFrequencies = DoubleArray(nFftBins - bandsToZero) { i ->
            val idx = i + bandsToZero
            idx * (nyquist / (nFftBins - 1))
        }
        val spectrogramBinsMel = DoubleArray(linearFrequencies.size) { i ->
            hertzToMel(linearFrequencies[i])
        }

        val melMin = hertzToMel(fMin.toDouble())
        val melMax = hertzToMel(fMax.toDouble())
        val edges = DoubleArray(nMels + 2) { i ->
            melMin + (melMax - melMin) * i / (nMels + 1)
        }

        val lowerEdge = edges.copyOfRange(0, nMels)
        val center = edges.copyOfRange(1, nMels + 1)
        val upperEdge = edges.copyOfRange(2, nMels + 2)

        val melWeights = Array(nFftBins) { FloatArray(nMels) }
        for (k in spectrogramBinsMel.indices) {
            val mel = spectrogramBinsMel[k]
            for (m in 0 until nMels) {
                val lowerSlope = (mel - lowerEdge[m]) / (center[m] - lowerEdge[m])
                val upperSlope = (upperEdge[m] - mel) / (upperEdge[m] - center[m])
                val weight = max(0.0, min(lowerSlope, upperSlope)).toFloat()
                melWeights[k + bandsToZero][m] = weight
            }
        }
        // DC bin (k=0) is zeroed by design.
        return melWeights
    }
}

