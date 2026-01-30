import coremltools as ct
import numpy as np
from transformers import T5Tokenizer

model_path = "apps/t5_base_grammar_correction/iOS/vennify_t5-base-grammar-correction.mlpackage"
print(f"Loading model from {model_path}...")
try:
    mlmodel = ct.models.MLModel(model_path)
except Exception as e:
    print(f"Failed to load model: {e}")
    exit(1)

text = "He go to school yesterday"
prompt = "grammar: " + text

# --- Test Case 1: Dummy Tokenizer (iOS Logic) ---
print("\n--- Test Case 1: Dummy Tokenizer (iOS Logic) ---")
# 1. Tokenize (ASCII)
input_ids = [ord(c) for c in prompt]
print(f"Original ASCII IDs: {input_ids}")

# 2. Variable Length Logic (iOS App)
# iOS code truncates to 11.
fixed_len = 11
if len(input_ids) > fixed_len:
    input_ids = input_ids[:fixed_len]
else:
    input_ids = input_ids + [0] * (fixed_len - len(input_ids))

print(f"Truncated/Padded IDs (11): {input_ids}")

# Attention Mask
attention_mask = [1] * len(input_ids)
# Note: iOS code pads mask with 0s if input was padded, or truncates.
# For this input ("grammar: He..."), it was truncated, so mask is all 1s.

# Prepare Inputs
input_ids_np = np.array(input_ids, dtype=np.int32).reshape(1, fixed_len)
attention_mask_np = np.array(attention_mask, dtype=np.int32).reshape(1, fixed_len)

# Decode Loop
decoder_input_ids = [0] # Start token
max_len = 20

print("Generating...")
for step in range(max_len):
    # Prepare Decoder Input (Last token only)
    last_token = decoder_input_ids[-1]
    decoder_input_np = np.array([[last_token]], dtype=np.int32)
    
    inputs = {
        "input_ids": input_ids_np,
        "attention_mask": attention_mask_np,
        "decoder_input_ids": decoder_input_np
    }
    
    # Run Prediction
    try:
        output = mlmodel.predict(inputs)
    except Exception as e:
        print(f"Prediction failed: {e}")
        break
        
    logits = list(output.values())[0] # Values are usually dictionary, get the first one (logits)
    # Shape might be flattened or [1, 1, 32128]
    
    # Argmax
    # Assuming logits are large array, we want the argmax of the *last* step (which is the only step)
    next_token = np.argmax(logits)
    
    print(f"Step {step}: User Input Last Token {last_token} -> Predicted {next_token}")
    
    if next_token == 1: # EOS
        print("EOS reached")
        break
    
    decoder_input_ids.append(next_token)

print(f"Final Sequence (Dummy): {decoder_input_ids}")


# --- Test Case 2: Real T5 Tokenizer ---
print("\n--- Test Case 2: Real T5 Tokenizer ---")
try:
    tokenizer = T5Tokenizer.from_pretrained("vennify/t5-base-grammar-correction")
    
    real_input_ids = tokenizer.encode(prompt)
    print(f"Real T5 IDs: {real_input_ids}")
    
    # Truncate strictly to 11 to match model constraint
    if len(real_input_ids) > fixed_len:
        real_input_ids = real_input_ids[:fixed_len]
    else:
        real_input_ids = real_input_ids + [0] * (fixed_len - len(real_input_ids))
        
    print(f"Truncated Real IDs: {real_input_ids}")
    
    input_ids_np = np.array(real_input_ids, dtype=np.int32).reshape(1, fixed_len)
    attention_mask_np = np.array([1]*fixed_len, dtype=np.int32).reshape(1, fixed_len)
    
    decoder_input_ids = [0]
    
    for step in range(max_len):
        last_token = decoder_input_ids[-1]
        decoder_input_np = np.array([[last_token]], dtype=np.int32)
        
        inputs = {
            "input_ids": input_ids_np,
            "attention_mask": attention_mask_np,
            "decoder_input_ids": decoder_input_np
        }
        
        output = mlmodel.predict(inputs)
        logits = list(output.values())[0]
        next_token = np.argmax(logits)
        
        print(f"Step {step}: Predicted {next_token}")
        if next_token == 1:
            break
        decoder_input_ids.append(next_token)
        
    print(f"Final Sequence (Real): {decoder_input_ids}")
    print(f"Decoded: {tokenizer.decode(decoder_input_ids)}")

except Exception as e:
    print(f"Skipping Test 2: {e}")
