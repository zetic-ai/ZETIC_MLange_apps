import coremltools as ct
import numpy as np
import os

def run_verification():
    model_path = "apps/t5_base_grammar_correction/iOS/vennify_t5-base-grammar-correction_stateless1024.mlpackage"
    print(f"Loading model: {model_path}")
    
    try:
        model = ct.models.MLModel(model_path)
    except Exception as e:
        print(f"Failed to load model: {e}")
        return

    print("Model loaded.")
    
    # --- FULL GENERATION LOOP ---
    from transformers import AutoTokenizer
    
    try:
        tokenizer = AutoTokenizer.from_pretrained("vennify/t5-base-grammar-correction")
    except:
        print("Transformers/Tokenizer not found or failed to load. Cannot verify text output without it.")
        return

    text = "He go to school yesterday"
    print(f"\n--- Generating for: '{text}' ---")
    
    # 1. Prepare Encoder Input (Fixed 1024)
    input_text = "grammar: " + text
    enc = tokenizer(input_text, return_tensors="np")
    original_input_ids = enc["input_ids"]
    
    input_len = 1024
    input_ids = np.zeros((1, input_len), dtype=np.int32)
    current_len = original_input_ids.shape[1]
    input_ids[0, :current_len] = original_input_ids
    
    attention_mask = np.zeros((1, input_len), dtype=np.int32)
    attention_mask[0, :current_len] = 1 # 1 for valid, 0 for pad
    
    # DEBUG: Verify Input
    print(f"Input IDs (first 20): {input_ids[0, :20]}")
    decoded_input = tokenizer.decode(input_ids[0], skip_special_tokens=True)
    print(f"Decoded Input Check: '{decoded_input}'")
    
    # 2. Decoding Loop
    decoder_start_token = tokenizer.pad_token_id # 0
    eos_token = tokenizer.eos_token_id # 1
    
    # FIXED BUFFER LENGTH (History)
    fixed_decoder_len = 128 
    
    # Initialize Buffer: [Start, Pad, Pad, ...]
    decoder_ids = np.zeros((1, fixed_decoder_len), dtype=np.int32)
    decoder_ids[0, 0] = decoder_start_token
    
    generated_tokens = []
    
    print(f"Starting generation (Fixed Buffer {fixed_decoder_len})...")
    
    # Greedy Decoding Loop matching user's logic
    for step in range(fixed_decoder_len - 1):
        
        # 1. Forward Pass with FULL BUFFER
        inputs = {
            "input_ids": input_ids,
            "attention_mask": attention_mask,
            "decoder_input_ids": decoder_ids
        }
        
        outputs = model.predict(inputs)
        
        # 2. Get Logits
        out_key = list(outputs.keys())[0]
        logits = outputs[out_key] # Expected: [1, 128, Vocab]
        
        # 3. Read Logits at CURRENT STEP 'step'
        # The output at index 'step' is the prediction for token 'step+1'
        if logits.ndim == 3:
            step_logits = logits[0, step, :]
            next_token = int(np.argmax(step_logits))
        else:
             print(f"Unexpected logits shape: {logits.shape}")
             break
        
        if next_token == eos_token:
            print("EOS Reached")
            break
            
        generated_tokens.append(next_token)
        print(".", end="", flush=True)
        
        # 4. Update Buffer IN-PLACE for next step
        decoder_ids[0, step + 1] = next_token
            
    print("\n")
    decoded_text = tokenizer.decode(generated_tokens, skip_special_tokens=True)
    print(f"Result: '{decoded_text}'")
    
    expected = "He went to school yesterday."
    if decoded_text.strip() == expected:
        print("✅ SUCCESS! Output matches expected correction.")
    else:
        print(f"⚠️ Result differs from expected '{expected}'. Check model weights.")

if __name__ == "__main__":
    run_verification()
