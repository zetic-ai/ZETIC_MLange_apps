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

### Model Preparation Guide
Before using a model with ZETIC.MLange, you need to prepare the model file and sample inputs.
For full details, refer to the [official documentation](https://docs.zetic.ai/prepare-model).

#### 1. Supported Formats
ZETIC.MLange supports the following model formats:
- **PyTorch Exported Program (`.pt2`)** (Recommended)
- **ONNX (`.onnx`)**
- **TorchScript (`.pt`)** (Deprecated)

#### 2. Saving Model & Inputs
You must save your model in one of the supported formats and your sample inputs as NumPy arrays (`.npy`).

**Example: PyTorch Exported Program (.pt2)**
```python
import torch
import numpy as np

# (1) Export the model
torch_model = ...
dummy_input = torch.randn(...)
exported_program = torch.export.export(torch_model, (dummy_input,))
torch.export.save(exported_program, "model.pt2")

# (2) Save sample inputs
np.save("input.npy", dummy_input.detach().numpy())
```

**Example: ONNX (.onnx)**
```python
import torch
import numpy as np

# (1) Export to ONNX
torch.onnx.export(model, dummy_input, "model.onnx", ...)

# (2) Save sample inputs
np.save("input.npy", dummy_input.detach().numpy())
```

#### 3. Verify Inputs
ZETIC.MLange compiles models into a static hardware graph. **Input Order** and **Input Shapes** must be consistent and fixed. Always verify your `.onnx` or `.pt2` model structure using tools like [Netron](https://netron.app/).

## Platform Directories
- **Android**: Gradle-based Android project.
- **iOS**: Xcode project or Swift Package.
- **Flutter / ReactNative**: Cross-platform implementations.
