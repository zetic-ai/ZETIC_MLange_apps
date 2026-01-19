# Text Anonymizer - Android

Android application for text anonymization using Zetic MLange SDK. This app mirrors the iOS Text Anonymizer app functionality and UI.

## Setup

1. **Open the project in Android Studio**
   - Open Android Studio
   - Select "Open an Existing Project"
   - Navigate to the `android/` directory

2. **Update your credentials**
   - Open `app/src/main/java/com/zeticai/textanonymizer/Constants.kt`
   - Replace `YOUR_PERSONAL_ACCESS_TOKEN` with your ZeticAI personal access token
   - Get your token from: ZeticAI personal settings
   - The model name is already set to `jathin-zetic/tanaos-text-anonymizer`

3. **Sync Gradle**
   - Android Studio should automatically sync Gradle
   - If not, click "Sync Now" or go to File > Sync Project with Gradle Files

4. **Build and run**
   - Connect an Android device or start an emulator (API 24+)
   - Click Run or press Shift+F10

## Features

- ✅ Text anonymization using Zetic MLange SDK
- ✅ Model loading with progress indicator
- ✅ Real-time anonymization processing
- ✅ Copy anonymized text to clipboard
- ✅ Share anonymized text via Android share sheet
- ✅ Material Design 3 UI matching iOS app behavior
- ✅ Error handling with user-friendly messages
- ✅ Dark mode support

## Project Structure

```
android/
├── app/
│   ├── src/main/
│   │   ├── java/com/zeticai/textanonymizer/
│   │   │   ├── MainActivity.kt          # Main UI activity
│   │   │   ├── AnonymizerViewModel.kt   # ViewModel for model operations
│   │   │   └── Constants.kt             # Configuration (update your token here!)
│   │   ├── res/
│   │   │   ├── layout/                  # UI layouts
│   │   │   ├── values/                  # Strings, colors, themes
│   │   │   └── drawable/                # Drawable resources
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts                 # App-level build config
├── build.gradle.kts                     # Project-level build config
├── settings.gradle.kts
└── gradle.properties
```

## Requirements

- **Android Studio**: Hedgehog (2023.1.1) or later
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Kotlin**: 1.9.20
- **Gradle**: 8.2

## Dependencies

- Zetic MLange SDK: `com.zeticai.mlange:mlange:+`
- AndroidX libraries (Core, AppCompat, Material, Lifecycle)
- Kotlin Coroutines

## Troubleshooting

### Model Loading Issues
- Verify your personal access token is correct
- Check internet connection (required for initial model download)
- Check logcat for detailed error messages

### Build Issues
- Ensure Android SDK is properly configured
- Sync Gradle files
- Clean and rebuild: Build > Clean Project, then Build > Rebuild Project

### Runtime Issues
- Check that device/emulator has API 24+
- Verify internet permissions in AndroidManifest.xml
- Check logcat for runtime errors

## Notes

- The app uses the same model (`jathin-zetic/tanaos-text-anonymizer`) as the iOS version
- Model loading happens asynchronously to avoid blocking the UI
- Text processing also runs on a background thread
- The UI closely matches the iOS app's behavior and appearance

