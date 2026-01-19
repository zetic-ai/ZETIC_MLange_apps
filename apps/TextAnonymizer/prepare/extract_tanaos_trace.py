#!/usr/bin/env python3
"""
Script to trace tanaos-text-anonymizer-v1 to TorchScript format.

This script:
1. Sets up the directory structure: model_zoo/tanaos-text-anonymizer-v1/{model, inputs}
2. Loads the model from Hugging Face
3. Creates a wrapper (Token Classification)
4. Traces it to TorchScript format (.pt)
5. Saves sequential input token IDs as .npy files
"""

import os
import numpy as np
import torch
import torch.nn as nn
from transformers import AutoModelForTokenClassification, AutoTokenizer

def main():
    # 1. Setup Directories
    project_name = "tanaos-text-anonymizer-v1"
    
    # Create structure relative to current working directory
    base_dir = os.path.join("model_zoo", project_name)
    model_dir = os.path.join(base_dir, "model")
    inputs_dir = os.path.join(base_dir, "inputs")
    
    os.makedirs(model_dir, exist_ok=True)
    os.makedirs(inputs_dir, exist_ok=True)
    
    print(f"[INFO] Base directory: {base_dir}")
    print(f"[INFO] Model directory: {model_dir}")
    print(f"[INFO] Inputs directory: {inputs_dir}")

    # 2. Load Model and Tokenizer
    model_id = "tanaos/tanaos-text-anonymizer-v1"
    print(f"\n[INFO] Loading model: {model_id}...")

    try:
        # Load Tokenizer
        tokenizer = AutoTokenizer.from_pretrained(model_id)
        
        # Load Model (Token Classification for NER)
        # We use float32 for the most stable tracing
        model = AutoModelForTokenClassification.from_pretrained(
            model_id,
            torch_dtype=torch.float32  
        )
        print(f"[INFO] ✓ Model loaded: {type(model).__name__}")
        print(f"[INFO] Config: vocab={model.config.vocab_size}, labels={model.config.num_labels}")

    except Exception as e:
        print(f"[ERROR] Failed to load model. Error: {e}")
        exit(1)

    # 3. Model Wrapper
    class TanaosModelWrapper(nn.Module):
        """
        Wraps the Token Classification model to return raw logits.
        Required because TorchScript works with Tensors, not dicts.
        """
        def __init__(self, model):
            super().__init__()
            self.model = model

        def forward(self, input_ids, attention_mask):
            """
            Args:
                input_ids: [batch, seq_len]
                attention_mask: [batch, seq_len]
            Returns:
                logits: [batch, seq_len, num_labels]
            """
            # return_dict=True allows us to access .logits safely
            outputs = self.model(input_ids=input_ids, attention_mask=attention_mask, return_dict=True)
            return outputs.logits

    # 4. Prepare Sequential Inputs
    print(f"\n[INFO] Preparing sequential input tokens...")
    # Sample text containing PII (Name, Location)
    sample_text = "My name is Sarah Connor and I live in Los Angeles."

    inputs = tokenizer(
        sample_text,
        return_tensors="pt",
        padding="max_length", 
        max_length=128,       # Fixed length is preferred for static graph compilation
        truncation=True
    )

    input_ids = inputs["input_ids"]
    attention_mask = inputs["attention_mask"]
    seq_len = input_ids.shape[1]

    print(f"[INFO] Input shapes: ids={input_ids.shape}, mask={attention_mask.shape}")

    # Save the sequential input_ids and attention_mask
    input_ids_path = os.path.join(inputs_dir, "input_ids.npy")
    attention_mask_path = os.path.join(inputs_dir, "attention_mask.npy")
    
    # Save as Int64 (Standard for PyTorch)
    np.save(input_ids_path, input_ids.cpu().numpy().astype(np.int64))
    np.save(attention_mask_path, attention_mask.cpu().numpy().astype(np.int64))
    print(f"[INFO] ✓ Inputs saved to {inputs_dir}")

    # 5. Trace and Save (.pt)
    print(f"\n[INFO] Tracing model to TorchScript...")
    wrapped_model = TanaosModelWrapper(model).eval()

    try:
        with torch.no_grad():
            # Trace the model
            traced_model = torch.jit.trace(
                wrapped_model,
                (input_ids, attention_mask),
                strict=False,
                check_trace=False
            )
        
        # Save the traced model
        pt_filename = f"{project_name}.pt"
        pt_path = os.path.join(model_dir, pt_filename)
        
        torch.jit.save(traced_model, pt_path)
        print(f"[INFO] ✓ TorchScript model saved to {pt_path}")
        
        # Verify reload
        print(f"[INFO] Verifying reload...")
        test_model = torch.jit.load(pt_path)
        test_output = test_model(input_ids, attention_mask)
        print(f"[INFO] ✓ Inference successful. Output shape: {test_output.shape}")
        

        # 6. Print Test Command
        print("\n" + "="*60)
        print("READY TO RUN TEST")
        print("="*60)
        print("Copy and run this command:")
        print(f"./unit_test/test_bash/test_qnn_converter.sh \\")
        print(f"  {pt_path} \\")
        print(f"  {input_ids_path},{attention_mask_path} \\")
        print(f"  {base_dir}/output_results")
        print("="*60)

    except Exception as e:
        print(f"[ERROR] Failed to trace model: {e}")
        import traceback
        traceback.print_exc()
        exit(1)

if __name__ == "__main__":
    main()