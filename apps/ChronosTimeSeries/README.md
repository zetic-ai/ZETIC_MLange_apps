# Chronos T5 Tiny: Time Series Forecasting

<div align="center">

**Probabilistic Time Series Forecasting with T5 Architecture**

[![MLange](https://img.shields.io/badge/Powered%20by-MLange-orange.svg)](https://mlange.zetic.ai)
[![iOS](https://img.shields.io/badge/Platform-iOS-blue.svg)](iOS/)
[![Android](https://img.shields.io/badge/Platform-Android-green.svg)](Android/)

</div>

> [!TIP]
> **View on MLange Dashboard**: [Team_ZETIC/Chronos-T5-Tiny](https://mlange.zetic.ai/p/jathin-zetic/chronos-2?from=use-cases) - Contains generated source code & benchmark reports.

## 📱 Screenshots

<div align="center" style="display: flex; justify-content: center; gap: 40px;">
  <div align="center">
    <!-- Placeholders for now, user can add actual paths later -->
    <!-- <img src="../../res/screenshots/chronos_ios.png" width="200" alt="iOS Screenshot"> -->
    <div style="margin-top: 10px;">iPhone 15 Pro</div>
  </div>
  <div align="center">
    <!-- <img src="../../res/screenshots/chronos_android.png" width="200" alt="Android Screenshot"> -->
    <div style="margin-top: 10px;">Google Pixel 9 Pro</div>
  </div>
</div>

## 🚀 Quick Start

Get up and running in minutes:

1. **Get your MLange API Key** (free): [Sign up here](https://mlange.zetic.ai)
2. **Configure API Key**:
   ```bash
   # From repository root
   ./adapt_mlange_key.sh
   ```
3. **Run the App**:
   - **iOS**: Open `iOS/` in Xcode
   - **Android**: Open `Android/` in Android Studio
   - Build and run on a device or simulator

## 📚 Resources

- **MLange Dashboard**: [View Model & Reports](https://mlange.zetic.ai/p/jathin-zetic/chronos-2?from=use-cases)
- **Documentation**: [MLange Docs](https://docs.zetic.ai)

## 📋 Model Details

- **Model**: Chronos T5 Tiny
- **Task**: Time Series Forecasting
- **MLange Project**: [jathin-zetic/chronos-2](https://mlange.zetic.ai/p/jathin-zetic/chronos-2?from=use-cases)
- **Base Model**: [amazon/chronos-t5-tiny](https://github.com/amazon-science/chronos-forecasting)
- **Key Features**:
  - Probabilistic forecasting (quantiles)
  - Zero-shot performance on unseen time series
  - Handles missing values (NaN) gracefully
  - NPU-accelerated inference via MLange

This application showcases the **Chronos T5 Tiny** model using **MLange**. Chronos is a family of pretrained time series forecasting models based on language model architectures. It supports on-device forecasting with interactive visualization of prediction intervals.

## 📁 Directory Structure

```
ChronosTimeSeries/
├── prepare/      # Model & input preparation scripts
├── iOS/          # iOS implementation with MLange SDK
└── Android/      # Android implementation with MLange SDK
```
