import torch
import coremltools as ct
from transformers import AutoTokenizer, AutoModelForSeq2SeqLM
import os
import numpy as np

# Define Wrapper to ensure clean signature
class T5Wrapper(torch.nn.Module):
    def __init__(self, model):
        super().__init__()
        self.model = model

    def forward(self, input_ids, attention_mask, decoder_input_ids):
        outputs = self.model(
            input_ids=input_ids,
            attention_mask=attention_mask,
            decoder_input_ids=decoder_input_ids
        )
        # FP16 ROBUSTNESS FIX:
        # T5 logits can exceed 65504 (FP16 max), causing "!!!!" garbage on ANE.
        # clamping to [-1000, 1000] is safe for argmax/softmax and prevents overflow.
        logits = outputs.logits
        return torch.clamp(logits, min=-1000.0, max=1000.0)

def export_model():
    model_name = "vennify/t5-base-grammar-correction"
    print(f"Loading model: {model_name}")
    
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    model = AutoModelForSeq2SeqLM.from_pretrained(model_name)
    model.eval()
    
    wrapper = T5Wrapper(model)
    
    # FIXED LENGTH CONSTANTS
    FIXED_ENC_LEN = 1024
    FIXED_DEC_LEN = 128
    print(f"Exporting with FIXED shapes: Encoder [1, {FIXED_ENC_LEN}], Decoder [1, {FIXED_DEC_LEN}]")
    
    # Create Dummy Inputs with Padding
    # Input: [1, 1024]
    dummy_input_ids = torch.zeros((1, FIXED_ENC_LEN), dtype=torch.long)
    dummy_input_ids[0, :5] = torch.tensor([10, 11, 12, 13, 14])
    
    dummy_mask = torch.zeros((1, FIXED_ENC_LEN), dtype=torch.long)
    dummy_mask[0, :5] = 1
    
    # Decoder: [1, 128] - CRITICAL FOR HISTORY
    dummy_decoder_ids = torch.zeros((1, FIXED_DEC_LEN), dtype=torch.long)
    dummy_decoder_ids[0, 0] = model.config.decoder_start_token_id or 0
    
    traced_model = torch.jit.trace(wrapper, (dummy_input_ids, dummy_mask, dummy_decoder_ids))
    
    # Define Fixed Shapes
    input_ids_shape = ct.Shape(shape=(1, FIXED_ENC_LEN))
    attention_mask_shape = ct.Shape(shape=(1, FIXED_ENC_LEN))
    decoder_input_ids_shape = ct.Shape(shape=(1, FIXED_DEC_LEN))
    
    print("Converting to CoreML with Fixed Shapes...")
    
    mlmodel = ct.convert(
        traced_model,
        inputs=[
            ct.TensorType(name="input_ids", shape=input_ids_shape, dtype=np.int32),
            ct.TensorType(name="attention_mask", shape=attention_mask_shape, dtype=np.int32),
            ct.TensorType(name="decoder_input_ids", shape=decoder_input_ids_shape, dtype=np.int32)
        ],
        outputs=[
            ct.TensorType(name="logits", dtype=np.float32)
        ],
        compute_units=ct.ComputeUnit.ALL,
        compute_precision=ct.precision.FLOAT32, # CRITICAL: Force FP32 to prevent ANE FP16 overflow (NaN)
        minimum_deployment_target=ct.target.iOS16
    )
    
    save_path = "vennify_t5-base-grammar-correction-fixed-1024-128-fp32.mlpackage"
    mlmodel.save(save_path)
    print(f"Success! Model saved to {save_path}")

if __name__ == "__main__":
    export_model()
