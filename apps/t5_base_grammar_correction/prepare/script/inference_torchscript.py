
import torch
import numpy as np
import os
from transformers import AutoTokenizer

# Paths
script_dir = os.path.dirname(os.path.abspath(__file__))
model_path = os.path.join(script_dir, "vennify_t5-base-grammar-correction.pt")
model_id = "vennify/t5-base-grammar-correction"

print(f"Loading tokenizer: {model_id}")
tokenizer = AutoTokenizer.from_pretrained(model_id)

print(f"Loading TorchScript model: {model_path}")
try:
    model = torch.jit.load(model_path)
    model.eval()
    print("✅ Model loaded successfully.")
except Exception as e:
    print(f"❌ Failed to load model: {e}")
    exit(1)

# Input text
text = "grammar: He go to school"
print(f"\nScanning text: '{text}'")

# Encode input
# Max length from export was 1024
max_length = 1024
inputs = tokenizer(
    text, 
    return_tensors="pt", 
    max_length=max_length, 
    padding="max_length", 
    truncation=True
)
input_ids = inputs.input_ids
attention_mask = inputs.attention_mask

print(f"Input shape: {input_ids.shape}")

# Decoder setup
# The user changed export to use a fixed buffer of 128
decoder_len = 128
decoder_input_ids = torch.full((1, decoder_len), 0, dtype=torch.long) # 0 is usually pad for T5? Let's check.
pad_token_id = tokenizer.pad_token_id # 0 for T5
decoder_input_ids.fill_(pad_token_id)

start_token_id = tokenizer.pad_token_id # T5 uses pad as start usually
# Or check config if possible, but hardcoding based on standard T5 or observing previous script
# Previous script logic: start_token = hf_model.config.decoder_start_token_id (or pad)
# We'll assume pad_token_id (0) is correct for start if not specified.
decoder_input_ids[0, 0] = start_token_id

print(f"Starting generation (Fixed Decoder Buffer: {decoder_len})...")
generated_ids = []

# Greedy Decoding Loop
# We fill decoder_input_ids index by index.
# index 0 is Start -> Model predicts token at index 0 (which is the first word)
# index 1 becomes First Word -> Model predicts token at index 1...

for i in range(decoder_len - 1):
    with torch.no_grad():
        # Forward pass
        # Model expects: forward(input_ids, attention_mask, decoder_input_ids)
        # It returns (logits,) tuple
        outputs = model(input_ids, attention_mask, decoder_input_ids)
        logits = outputs[0] # Shape: (1, 128, vocab_size)
    
    # Get the logits for the current step 'i'
    # The output at index 'i' corresponds to the prediction for token 'i+1'
    step_logits = logits[0, i, :]
    predicted_id = torch.argmax(step_logits).item()
    
    # Update decoder input for next step
    decoder_input_ids[0, i + 1] = predicted_id
    generated_ids.append(predicted_id)
    
    # Stop if EOS token
    if predicted_id == tokenizer.eos_token_id:
        print("EOS token reached.")
        break
        
    print(f"Step {i}: {predicted_id} ('{tokenizer.decode([predicted_id])}')")

# Decode result
final_text = tokenizer.decode(generated_ids, skip_special_tokens=True)
print(f"\n\nFinal Result: '{final_text}'")
