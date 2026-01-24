
import torch
import numpy as np
import os
from transformers import AutoTokenizer, AutoModelForSeq2SeqLM

# --- 1. Set paths ---
script_dir = os.path.dirname(os.path.abspath(__file__))
# "model is above export script model" -> ../model
# "input is same level in input" -> ../input
model_dir = os.path.join(script_dir, "../model")
input_dir = os.path.join(script_dir, "../input")
os.makedirs(model_dir, exist_ok=True)
os.makedirs(input_dir, exist_ok=True)
print(f"Models will be saved to '{model_dir}', input files to '{input_dir}'.")

# --- 2. Define Wrapper ---
class ModelWrapper(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, input_ids, attention_mask, decoder_input_ids):
        # Return logits only
        outputs = self.model(
            input_ids=input_ids,
            attention_mask=attention_mask,
            decoder_input_ids=decoder_input_ids
        )
        return (outputs.logits,)

# --- 3. Load Model ---
model_name = "vennify/t5-base-grammar-correction"
print(f"Loading model: '{model_name}'...")

tokenizer = AutoTokenizer.from_pretrained(model_name)
hf_model = AutoModelForSeq2SeqLM.from_pretrained(model_name)

wrapped_model = ModelWrapper(hf_model)
wrapped_model.eval()
print("Model loaded and wrapped successfully.")

# --- 4. Prepare Inputs ---
# T5 uses relative positional embeddings, so it can handle longer sequences than n_positions (512).
# User request: Max input length 1024
max_length = 1024
text = "grammar: This sentences has has bads grammar." * 100 # Make it long enough
print(f"Preprocessing text to max_length={max_length}...")

inputs = tokenizer(
    text, 
    return_tensors="pt", 
    max_length=max_length, 
    padding="max_length", 
    truncation=True
)
input_ids = inputs.input_ids
attention_mask = inputs.attention_mask

# T5 uses pad_token_id as decoder_start_token_id if not explicitly set
start_token = hf_model.config.decoder_start_token_id
if start_token is None:
    start_token = hf_model.config.pad_token_id

# --- CRITICAL FIX FOR COREML / TORCHSCRIPT ---
# The model is STATELESS. If we export with [1, 1], it can only see the last token.
# It instantly forgets history, leading to loops ("School School School").
# We MUST export with a Fixed History Buffer (e.g. 128) so it can see previous tokens.
decoder_length = 128
decoder_input_ids = torch.full((1, decoder_length), 0, dtype=torch.long) # Fill with Pad (0)
decoder_input_ids[0, 0] = start_token # Set first token to start

print(f"\nCreated dummy inputs (Max Length {max_length}, Decoder Fixed {decoder_length}):")
print(f" - input_ids shape: {input_ids.shape}")
print(f" - attention_mask shape: {attention_mask.shape}")
print(f" - decoder_input_ids shape: {decoder_input_ids.shape}")

# --- 5. Save Inputs ---
# (0_input0.npy, 1_input1.npy...) style as requested, but keeping meaningful names for clarity
# while satisfying the numbering requirement.
np.save(os.path.join(input_dir, "0_input_ids.npy"), input_ids.numpy())
np.save(os.path.join(input_dir, "1_attention_mask.npy"), attention_mask.numpy())
np.save(os.path.join(input_dir, "2_decoder_input_ids.npy"), decoder_input_ids.numpy())
print(f"Input files have been saved to '{input_dir}'.")

dummy_inputs = (input_ids, attention_mask, decoder_input_ids)
safe_name = model_name.replace("/", "_")

# --- 6. Export ---

# (1) TorchScript (.torchscript)
ts_path = os.path.join(model_dir, f"{safe_name}.torchscript")
print(f"\nExporting to TorchScript ({ts_path})...")
try:
    traced_model = torch.jit.trace(wrapped_model, dummy_inputs)
    traced_model.save(ts_path)
    print("✅ TorchScript export success.")
except Exception as e:
    print(f"❌ TorchScript export failed: {e}")

# (2) Exported Program (.pt2)
pt2_path = os.path.join(model_dir, f"{safe_name}.pt2")
print(f"\nExporting to Exported Program ({pt2_path})...")
try:
    if hasattr(torch, "export"):
        ep = torch.export.export(wrapped_model, dummy_inputs)
        torch.export.save(ep, pt2_path)
        print("✅ Exported Program export success.")
    else:
        print("❌ torch.export not available (requires PyTorch 2.x).")
except Exception as e:
    print(f"❌ Exported Program export failed: {e}")

# (3) ONNX (.onnx)
onnx_path = os.path.join(model_dir, f"{safe_name}.onnx")
print(f"\nExporting to ONNX ({onnx_path})...")
try:
    import inspect
    sig = inspect.signature(torch.onnx.export)
    export_kwargs = {}
    if "dynamo" in sig.parameters:
        export_kwargs["dynamo"] = False

    torch.onnx.export(
        wrapped_model,
        dummy_inputs,
        onnx_path,
        input_names=["input_ids", "attention_mask", "decoder_input_ids"],
        output_names=["logits"],
        opset_version=18,
        dynamic_axes={
            "input_ids": {0: "batch", 1: "seq"},
            "attention_mask": {0: "batch", 1: "seq"},
            "decoder_input_ids": {0: "batch", 1: "dec_seq"},
            "logits": {0: "batch", 1: "dec_seq"}
        },
        **export_kwargs
    )
    print("✅ ONNX export success.")
except Exception as e:
    print(f"❌ ONNX export failed: {e}")
