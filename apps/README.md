# ZETIC.MLange Application Structure

Each model directory in this `apps/` folder follows a standardized structure to ensure consistency and ease of use.

## Common Directory Layout

Every model directory (e.g., `apps/<ModelName>/`) contains the following subdirectories:

```
.
├── prepare/      # Python scripts to download/export the model and prepare sample inputs.
├── Android/      # Native Android application source code.
├── iOS/          # Native iOS application source code.
├── Flutter/      # Flutter plugin/application code (Coming Soon).
└── ReactNative/  # React Native module/application code (Coming Soon).
```

## Prepare Directory
The `prepare/` directory is the starting point. It typically contains:
- `prepare_model.py`: A script to download the model from HuggingFace, converting it if necessary, and preparing sample input data for testing.

## Platform Directories
- **Android**: Gradle-based Android project.
- **iOS**: Xcode project or Swift Package.
- **Flutter / ReactNative**: Cross-platform implementations.
