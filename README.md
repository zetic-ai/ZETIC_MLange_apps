<div align="center">

# ZETIC.MLange Applications

<a href="https://zetic.ai"><img alt="ZETIC.ai Homepage" src="https://img.shields.io/badge/Website-zetic.ai-brightgreen"></a>
<a href="https://docs.zetic.ai"><img alt="MLange Documentation" src="https://img.shields.io/badge/Document-online-yellow"></a>
<a href="https://mlange.zetic.ai"><img alt="MLange Dashboard" src="https://img.shields.io/badge/MLange Dashboard-web-hotpink"></a>
<a href="https://play.google.com/store/apps/details?id=com.zeticai.zeticapp"><img alt="ZeticApp Play Store" src="https://img.shields.io/badge/ZeticApp-Play Store-darkgreen"></a>
<a href="https://apps.apple.com/app/zeticapp/id6739862746"><img alt="ZeticApp App Store" src="https://img.shields.io/badge/ZeticApp-App Store-lightblue"></a>
<a href="https://discord.com/invite/gVFX6myuMx"><img alt="Discord" src="https://img.shields.io/badge/Discord-Join%20Us-7289da"></a>

<br/>
<br/>

<h1>Infuse Your App with AI. Instantly.</h1>

<h3>Add state-of-the-art AI features to your mobile applications using <strong>ZETIC.MLange</strong>.</h3>

</div>

<br/>

<h2 align="center">What is ZETIC.MLange?</h2>

<p>
  <strong>ZETIC.MLange</strong> is an <strong>Open, NPU-Native</strong> software stack for On-Device AI.<br/>
  While others limit you to a closed garden of proprietary models, ZETIC.MLange is designed for <strong>Universal NPU Utilization</strong>.<br/>
  Bring <strong>ANY</strong> model from HuggingFace, upload it to our <strong>MLange Dashboard</strong>, and you will receive<br/>
  an <strong>NPU-runnable SDK</strong> (including source code & benchmark stats) ready for immediate deployment.
</p>

<p>
  🚀 <strong>Fully Automated NPU Utilization</strong><br/>
  Auto-mapping to specific NPU architectures.
</p>
<p>
  ⚡ <strong>Heterogeneous Hardware Orchestration</strong><br/>
  Seamless management of CPU, GPU, and NPU resources.
</p>
<p>
  🛠️ <strong>Rapid Deployment</strong><br/>
  From HuggingFace model to deployable mobile SDK in hours.
</p>

<br/>
<br/>

<h2 align="center">Explore AI Capabilities</h2>

<p>
This repository showcases <strong>deployable On-Device AI applications</strong> ready for immediate use.<br/>
  Pick a capability below and see it running on your device.<br/>
  <br/>
  <i>checks</i> <strong>MLange Page</strong>: Access the <strong>Generated SDK Source Code</strong> and <strong>NPU Performance Benchmarks</strong>.
</p>

| Feature | Model | Description | MLange Page |
| :---: | :---: | :--- | :---: |
| **Object Detection** | [**YOLOv8 Nano**](apps/YOLOv8) | Real-time object detection and tracking in milliseconds. | [**View**](https://mlange.zetic.ai/p/Ultralytics/YOLOv8n?from=use-cases) |
| **Speech Recognition** | [**Whisper Tiny**](apps/whisper-tiny) | High-accuracy automatic speech recognition (ASR) completely offline. | [**View**](https://mlange.zetic.ai/p/OpenAI/whisper-tiny-decoder?from=use-cases) |
| **Face Detection** | [**MediaPipe BlazeFace**](apps/MediaPipe-Face-Detection) | Ultra-fast face detection optimized for short-range selfie cameras. | [**View**](https://mlange.zetic.ai/p/google/MediaPipe-Face-Detection?from=use-cases) |
| **Face Tracking** | [**MediaPipe Face Landmarker**](apps/MediaPipe-Face-Landmarker) | High-fidelity 468-point face mesh and landmark tracking. | [**View**](https://mlange.zetic.ai/p/google/MediaPipe-Face-Landmark?from=use-cases) |
| **Emotion Analysis** | [**Emo-AffectNet**](apps/FaceEmotionRecognition) | Real-time facial emotion recognition. | [**View**](https://mlange.zetic.ai/p/ElenaRyumina/FaceEmotionRecognition?from=use-cases) |
| **Audio Analysis** | [**YamNet**](apps/YamNet) | Classification of environmental sounds and audio events. | [**View**](https://mlange.zetic.ai/p/google/Sound%20Classification(YAMNET)?from=use-cases) |

<br/>

<h3>Upcoming Models</h3>

We are constantly expanding our library. Here is what's coming next in very soon:

| Use Case | Model | Description |
| :---: | :---: | :--- |
| **Privacy / Anonymization** | [**tanaos-text-anonymizer-v1**](https://huggingface.co/tanaos/tanaos-text-anonymizer-v1) | Automatic rejection of PII (names, dates, locations) for secure data processing. |
| **Object Detection** | [**YOLOv26**](https://docs.ultralytics.com/models/yolo26/) | Next-generation NMS-free object detection optimized for edge devices. |
| **Healthcare / VQA** | [**MedGemma-1.5-4b-it**](https://huggingface.co/google/medgemma-1.5-4b-it) | Multimodal medical question answering and image understanding. |

<br/>

<h3 align="center">Build It Yourself</h3>

<p align="center">
  You can build these applications <strong>right now</strong>.<br/>
  Simply upload your model to the <a href="https://mlange.zetic.ai"><strong>MLange Dashboard</strong></a> and generate your SDK.<br/>
  <br/>
  <strong>Finding it difficult?</strong><br/>
  <a href="https://discord.com/invite/gVFX6myuMx"><strong>Join our Discord</strong></a> and request it! we are here to help.
</p>

<br/>

## Repository Structure

- **`apps/`**: Source code for various On-Device AI applications.
  - Each app contains Android/iOS projects and a `prepare/` folder for model setup.
- **`extension/`**: [ZETIC.MLange Extension Library](https://github.com/zetic-ai/zetic_mlange_ext) submodule.
  - Provides helper extensions and additional functional blocks.

<br/>

---

## Integration Overview

Once you have chosen a feature, integrating it is as simple as adding the ZETIC.MLange dependency and loading the model. This guide shows just how little code is needed.

> [!NOTE]
> You can get your **Personal Key** by signing up at [MLange Dashboard](https://mlange.zetic.ai).

### Android
Add dependency in `build.gradle`:
```gradle
dependencies { implementation 'com.zeticai.mlange:mlange:+' }
```
Run inference:
```kotlin
val model = ZeticMLangeModel(CONTEXT, PERSONAL_KEY, PROJECT_NAME)
val outputs = model.run(inputs)
```

### iOS
Add package via SPM: `https://github.com/zetic-ai/ZeticMLangeiOS.git`

Run inference:
```swift
let model = try ZeticMLangeModel(personalKey: KEY, name: NAME, version: VER)
let outputs = try model.run(inputs)
```

<br/>

---

## How to Contribute

We welcome contributions! If you have built an exciting on-device AI application using ZETIC.MLange, share it with the community.

1. **Generate your SDK**: Use the [MLange Dashboard](https://mlange.zetic.ai) to convert your HuggingFace or custom model into an NPU-optimized SDK.
2. **Build your App**: Implement your application on Android or iOS using the generated SDK.
3. **Submit**: Open a Pull Request with your new application directory under `apps/<Your_Model_Name>/`.

<br/>

## Resources

- **Website**: [https://zetic.ai](https://zetic.ai)
- **MLange Dashboard**: [https://mlange.zetic.ai](https://mlange.zetic.ai)
- **Documentation**: [https://docs.zetic.ai](https://docs.zetic.ai)
