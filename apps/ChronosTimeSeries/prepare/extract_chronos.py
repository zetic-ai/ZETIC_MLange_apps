
import os
import io
import torch
import torch.nn as nn
import numpy as np
import pandas as pd
from chronos import ChronosBoltPipeline
from chronos.chronos_bolt import ChronosBoltModelForForecasting

# -------------------------------------------------------------------------
# CONSTANTS & DATA
# -------------------------------------------------------------------------
PROJECT_NAME = "chronos-bolt-tiny"
STATIC_CONTEXT_LENGTH = 512 # User requested sufficiently long static shape for NPU
CSV_DATA = """date,late_night_snack_count,daily_spend_usd
2025-12-20,1,38.47
2025-12-21,2,48.97
2025-12-22,1,32.13
2025-12-23,1,30.36
2025-12-24,1,12.89
2025-12-25,1,25.65
2025-12-26,1,8.64
2025-12-27,3,40.33
2025-12-28,0,27.38
2025-12-29,0,13.77
2025-12-30,0,19.18
2025-12-31,0,15.64
2026-01-01,1,21.79
2026-01-02,0,6.04
2026-01-03,1,16.5
2026-01-04,0,30.5
2026-01-05,3,26.88
2026-01-06,0,17.41
2026-01-07,3,22.62
2026-01-08,0,15.85
2026-01-09,3,25.26
"""

# -------------------------------------------------------------------------
# MONKEY PATCH
# -------------------------------------------------------------------------
def patched_encode(
    self, context: torch.Tensor, mask: torch.Tensor = None
):
    from typing import Tuple, Optional
    # Original logic copied and modified
    mask = mask.to(context.dtype) if mask is not None else torch.isnan(context).logical_not().to(context.dtype)
    
    # REMOVED: Dynamic context length check
    # We guarantee input fits context_length in prepare_inputs (static 512)
    # This removes TracerWarning and potential If nodes.
    # if context.shape[-1] > self.chronos_config.context_length: ...
    batch_size, _ = context.shape

    # scaling
    context, loc_scale = self.instance_norm(context)

    # the scaling op above is done in 32-bit precision,
    # then the context is moved to model's dtype
    context = context.to(self.dtype)
    mask = mask.to(self.dtype)

    # patching
    patched_context = self.patch(context)
    patched_mask = torch.nan_to_num(self.patch(mask), nan=0.0)
    patched_context = torch.where(patched_mask > 0.0, patched_context, 0.0)
    # concat context and mask along patch dim
    patched_context = torch.cat([patched_context, patched_mask], dim=-1)

    # attention_mask = 1 if at least one item in the patch is observed
    attention_mask = patched_mask.sum(dim=-1) > 0  # (batch_size, patched_seq_length)

    input_embeds = self.input_patch_embedding(patched_context)

    if self.chronos_config.use_reg_token:
        # Append [REG]
        # PATCH: Explicitly use dtype=torch.long for indices
        reg_input_ids = torch.full(
            (batch_size, 1),
            self.config.reg_token_id,
            device=input_embeds.device,
            dtype=torch.long, # <--- FIXED
        )
        reg_embeds = self.shared(reg_input_ids)
        input_embeds = torch.cat([input_embeds, reg_embeds], dim=-2)
        attention_mask = torch.cat(
            [
                attention_mask.to(self.dtype),
                torch.ones_like(reg_input_ids).to(self.dtype),
            ],
            dim=-1,
        )

    encoder_outputs = self.encoder(
        attention_mask=attention_mask,
        inputs_embeds=input_embeds,
    )

    return encoder_outputs[0], loc_scale, input_embeds, attention_mask

def patched_decode(
    self,
    input_embeds,
    attention_mask,
    hidden_states,
    output_attentions=False,
):
    batch_size = input_embeds.shape[0]
    # PATCH: Explicitly use dtype=torch.long for indices
    decoder_input_ids = torch.full(
        (batch_size, 1),
        self.config.decoder_start_token_id,
        device=input_embeds.device,
        dtype=torch.long, # <--- FIXED
    )
    decoder_outputs = self.decoder(
        input_ids=decoder_input_ids,
        encoder_hidden_states=hidden_states,
        encoder_attention_mask=attention_mask,
        output_attentions=output_attentions,
        return_dict=True,
    )

    return decoder_outputs.last_hidden_state

# Apply Patches
print("[Patch] Applying monkeypatch to ChronosBoltModelForForecasting...")
ChronosBoltModelForForecasting.encode = patched_encode
ChronosBoltModelForForecasting.decode = patched_decode


# -------------------------------------------------------------------------
# MODEL WRAPPER
# -------------------------------------------------------------------------
class ChronosExportWrapper(nn.Module):
    def __init__(self, inner_model):
        super().__init__()
        self.inner_model = inner_model

    def forward(self, context):
        # Forward pass returning just the quantile predictions tensor
        # shape: [batch, num_quantiles, pred_len]
        output = self.inner_model(context=context)
        return output.quantile_preds

# -------------------------------------------------------------------------
# STATIC HELPERS (Graph Cleaning)
# -------------------------------------------------------------------------
class StaticPatch(nn.Module):
    def __init__(self, patch_size: int, patch_stride: int):
        super().__init__()
        self.patch_size = patch_size
        self.patch_stride = patch_stride
    
    def forward(self, x: torch.Tensor) -> torch.Tensor:
        # Static version: Assumes no padding needed (pre-validated)
        # Removes: if length % self.patch_size != 0 check
        x = x.unfold(dimension=-1, size=self.patch_size, step=self.patch_stride)
        return x

# -------------------------------------------------------------------------
# HELPER FUNCTIONS
# -------------------------------------------------------------------------
def prepare_inputs(pipeline, csv_content):
    df = pd.read_csv(io.StringIO(csv_content))
    # We use 'daily_spend_usd' as the target time series
    ts_data = torch.tensor(df["daily_spend_usd"].values, dtype=torch.float32)
    
    # 1. Create a static buffer of NaNs
    # Shape: [1, STATIC_CONTEXT_LENGTH]
    context = torch.full((1, STATIC_CONTEXT_LENGTH), float('nan'), dtype=torch.float32)
    
    # 2. Fill the end with actual data
    data_len = len(ts_data)
    if data_len > STATIC_CONTEXT_LENGTH:
        # If data is too long, take the last STATIC_CONTEXT_LENGTH points
        context[0, :] = ts_data[-STATIC_CONTEXT_LENGTH:]
    else:
        # If data fits, place it at the end (left-padding)
        context[0, -data_len:] = ts_data
        
    return context

def export_and_verify():
    # 1. Setup Directories
    base_dir = "/home/yeonseok/workspace/exp/zetic_mentat/model_zoo/chronos-ys"
    model_dir = os.path.join(base_dir, "model")
    input_dir = os.path.join(base_dir, "input")
    
    os.makedirs(model_dir, exist_ok=True)
    os.makedirs(input_dir, exist_ok=True)
    
    print(f"[Info] Loading model {PROJECT_NAME}...")
    pipeline = ChronosBoltPipeline.from_pretrained(
        "amazon/chronos-bolt-tiny",
        device_map="cpu",
        torch_dtype=torch.float32,
    )
    model = pipeline.model
    model.eval()
    
    # DISABLE CACHE to simplify graph
    model.config.use_cache = False
    if hasattr(model, 'decoder'):
        model.decoder.config.use_cache = False
    print("[Info] Disabled use_cache for cleaner export.")
    
    # REPLACE Patch module with StaticPatch
    print("[Patch] Replacing model.patch with StaticPatch...")
    model.patch = StaticPatch(
        patch_size=model.chronos_config.input_patch_size,
        patch_stride=model.chronos_config.input_patch_stride
    )
    
    # 2. Prepare Inputs
    print("[Info] Preparing inputs from CSV...")
    context = prepare_inputs(pipeline, CSV_DATA)
    input_path = os.path.join(input_dir, "context.npy")
    np.save(input_path, context.numpy())
    print(f"[Info] Input saved to {input_path}")
    print(f"[Info] Input Shape: {context.shape}")
    
    # 3. Create Wrapper
    wrapper = ChronosExportWrapper(model)
    wrapper.eval()
    
    # 4. Get Expected Output
    print("[Info] Running original model for verification...")
    with torch.no_grad():
        original_output = wrapper(context) # [1, 9, 12]
        
    print(f"[Info] Output Shape: {original_output.shape}")
    
    # Semantic Verification Info
    # Get last few valid inputs (ignoring NaNs)
    # Reload data for verification context
    df_verify = pd.read_csv(io.StringIO(CSV_DATA))
    valid_inputs = torch.tensor(df_verify["daily_spend_usd"].values, dtype=torch.float32)
    print("\n" + "="*50)
    print("SEMANTIC CHECK")
    print("="*50)
    print(f"Last 5 Input Values: {valid_inputs[-5:].tolist()}")
    
    # Chronos Bolt outputs 9 quantiles. Median is index 4.
    median_forecast = original_output[0, 4, :].tolist()
    print(f"Median Forecast (12 steps): {[round(x, 2) for x in median_forecast]}")
    
    # Check if forecast is in reasonable range (e.g. within min/max of recent history +/- some margin)
    recent_mean = valid_inputs[-10:].mean().item()
    print(f"Recent History Mean: {recent_mean:.2f}")
    
    if abs(median_forecast[0] - recent_mean) < 15.0: # Arbitrary sanity check
        print("[Check] Forecast seems reasonable (near recent mean).")
    else:
        print("[Check] Forecast might be far from mean (volatile?)")
    print("="*50 + "\n")
    
    # 5. Export TorchScript
    ts_path = os.path.join(model_dir, f"{PROJECT_NAME}.pt")
    print(f"[Info] Exporting TorchScript to {ts_path}...")
    try:
        with torch.no_grad():
            traced_model = torch.jit.trace(wrapper, (context,))
            torch.jit.save(traced_model, ts_path)
        print("[Success] TorchScript export successful.")
        
        # Verify TS
        print("[Verify] Verifying TorchScript...")
        loaded_ts = torch.jit.load(ts_path)
        ts_output = loaded_ts(context)
        diff = (ts_output - original_output).abs().max()
        print(f"TS Diff: {diff}")
        if diff == 0:
             print("[Success] TorchScript outputs match exactly.")
        else:
             print(f"[Warning] TorchScript outputs differ! Max diff: {diff}")
             raise ValueError("TorchScript verification failed")
             
    except Exception as e:
        print(f"[Error] TorchScript export/verify failed: {e}")
        import traceback
        traceback.print_exc()

    # 6. Export ExportedProgram (.pt2)
    pt2_path = os.path.join(model_dir, f"{PROJECT_NAME}.pt2")
    print(f"[Info] Exporting ExportedProgram to {pt2_path}...")
    try:
        # torch.export.export
        exported_program = torch.export.export(wrapper, (context,), strict=False)
        torch.export.save(exported_program, pt2_path)
        print("[Success] ExportedProgram successful.")
        
        # Verify PT2
        print("[Verify] Verifying ExportedProgram...")
        loaded_ep = torch.export.load(pt2_path)
        ep_output = loaded_ep.module()(context)
        diff = (ep_output - original_output).abs().max()
        print(f"PT2 Diff: {diff}")
        if diff == 0:
             print("[Success] ExportedProgram outputs match exactly.")
        else:
             print(f"[Warning] ExportedProgram outputs differ! Max diff: {diff}")
             if diff > 1e-6:
                 raise ValueError("ExportedProgram verification failed")

    except Exception as e:
        print(f"[Error] ExportedProgram export/verify failed: {e}")
        import traceback
        traceback.print_exc()

    # 7. Export ONNX
    onnx_path = os.path.join(model_dir, f"{PROJECT_NAME}.onnx")
    print(f"[Info] Exporting ONNX to {onnx_path}...")
    try:
        torch.onnx.export(
            wrapper,
            (context,),
            onnx_path,
            input_names=["context"],
            output_names=["quantile_preds"],
            opset_version=14, # Retry 14 with cleaner graph
            # For NPU (static shape), we generally avoid dynamic axes on seq_len.
            # We keep batch_size dynamic or static? User said "static shape".
            # Usually strict static shape means NO dynamic axes.
            # But let's keep batch_size dynamic just in case, unless explicit static batch 1 is preferred.
            # User said "handle inputs sufficiently long... static shape".
            # Let's fix seq_len (dim 1) but keep batch (dim 0) dynamic strictly for flexibility, 
            # OR completely static for max NPU compatibility. 
            # I will assume fixed batch size 1 is safest for strict NPU "demo app".
            # So NO dynamic_axes.
        )
        print("[Success] ONNX export successful.")
        
        # Post-processing: Check and Infer Shapes
        import onnx
        print("[Info] Post-processing ONNX model...")
        model_onnx = onnx.load(onnx_path)
        print(f"[Info] Final Opset Version: {model_onnx.opset_import[0].version}")
        onnx.checker.check_model(model_onnx)
        model_onnx = onnx.shape_inference.infer_shapes(model_onnx)
        onnx.save(model_onnx, onnx_path)
        print("[Success] ONNX model checked and shapes inferred.")
        
        # Verify ONNX
        import onnxruntime as ort
        print("[Verify] Verifying ONNX...")
        sess = ort.InferenceSession(onnx_path)
        onnx_out = sess.run(None, {"context": context.numpy()})[0]
        onnx_torch = torch.from_numpy(onnx_out)
        
        # Visual Comparison
        print("-" * 30)
        print("VISUAL COMPARISON (First 5 steps, Median)")
        print(f"Original: {original_output[0, 4, :5].tolist()}")
        print(f"ONNX    : {onnx_torch[0, 4, :5].tolist()}")
        print("-" * 30)
        
        diff = (onnx_torch - original_output).abs().max()
        print(f"ONNX Diff: {diff}")
        if diff < 1e-4:
             print(f"[Success] ONNX outputs match (Max diff: {diff}).")
        else:
             print(f"[Warning] ONNX outputs differ significantly! Max diff: {diff}")
             raise ValueError("ONNX verification failed")
             
    except Exception as e:
        print(f"[Error] ONNX export/verify failed: {e}")
        import traceback
        traceback.print_exc()
        
    print("\n[Summary] Export process completed.")

if __name__ == "__main__":
    try:
        export_and_verify()
    except Exception as e:
        print(f"[Fatal Error] {e}")
        import traceback
        traceback.print_exc()