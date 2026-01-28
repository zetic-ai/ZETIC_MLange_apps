import coremltools as ct
import numpy as np
import os
from transformers import AutoTokenizer

# Paths
# Adjust path to point to your STATLESS 1024 model
# model_path = "/Users/yeonseok/Documents/exp/ZETIC_MLange_apps/apps/t5_base_grammar_correction/iOS/vennify_t5-base-grammar-correction.mlpackage"
model_path = "/Users/yeonseok/Documents/exp/ZETIC_MLange_apps/apps/t5_base_grammar_correction/iOS/vennify_t5-base-grammar-correction_fp32.mlpackage"
model_id = "vennify/t5-base-grammar-correction"

print(f"Loading tokenizer: {model_id}")
try:
    tokenizer = AutoTokenizer.from_pretrained(model_id)
    
    # DUMP VOCAB for iOS
    import json
    print("Dumping vocabulary to t5_vocab.json...")
    # T5 uses spiece, so generic get_vocab might not imply order.
    # Best way: Iterate 0 to vocab_size
    vocab_size = tokenizer.vocab_size
    vocab_dict = {}
    for i in range(vocab_size):
        # decode single token
        # This is slow but accurate
        try:
           # sp_model.id_to_piece(id) is best if available
           token_str = tokenizer.convert_ids_to_tokens(i)
           vocab_dict[i] = token_str
        except:
           pass
           
    with open("t5_vocab.json", "w") as f:
        json.dump(vocab_dict, f, ensure_ascii=False)
    print("Vocab dump complete.")
    
except Exception as e:
    print(f"Failed to load tokenizer: {e}")
    exit(1)

print(f"Loading CoreML model: {model_path}")
try:
    model = ct.models.MLModel(model_path)
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
    return_tensors="np", # CoreML needs Numpy
    max_length=max_length, 
    padding="max_length", 
    truncation=True
)

# CoreML expects INT32 usually, Transformers gives INT64
input_ids = inputs["input_ids"].astype(np.int32)
attention_mask = inputs["attention_mask"].astype(np.int32)

print(f"Input shape: {input_ids.shape}")

# Decoder setup
# Fixed buffer of 128
decoder_len = 128
decoder_input_ids = np.zeros((1, decoder_len), dtype=np.int32)

pad_token_id = tokenizer.pad_token_id # 0
start_token_id = tokenizer.pad_token_id # T5 uses pad as start usually

# Fill with pad
decoder_input_ids.fill(pad_token_id)
# Set start token
decoder_input_ids[0, 0] = start_token_id

print(f"Starting generation (Fixed Decoder Buffer: {decoder_len})...")
generated_ids = []

# Greedy Decoding Loop (CoreML)
# Aligned with TorchScript logic:
# i goes from 0 to 126
for i in range(decoder_len - 1):
    
    # CoreML Input Dictionary
    input_dict = {
        "input_ids": input_ids,
        "attention_mask": attention_mask,
        "decoder_input_ids": decoder_input_ids
    }
    
    # Forward pass
    output_dict = model.predict(input_dict)
    
    # Get logits
    # Name might vary, usually "logits" or "var_..." check keys if needed
    out_key = list(output_dict.keys())[0]
    logits = output_dict[out_key] # Shape: (1, 128, vocab_size)
    
    # Get the logits for the current step 'i'
    if logits.ndim == 3:
        step_logits = logits[0, i, :]
        predicted_id = int(np.argmax(step_logits))
    else:
        # Fallback if argmaxed output
        predicted_id = int(logits.flatten()[i])

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

print("\n--- GENERATING TOKENS FOR SWIFT DEMO ---")
examples = [
    "I has a apple",
    "He go to school yesterday",
    "She don't likes it",
    "My grammar are bad",
    "I am write a letter"
]

for ex in examples:
    # prompt = "grammar: " + ex
    # The Swift app does "grammar: " prepend. Let's do it here to get exact IDs.
    prompt = "grammar: " + ex
    encoded = tokenizer(prompt, return_tensors="np", padding=False, truncation=True)
    ids = encoded["input_ids"][0].tolist()
    print(f'"{ex}": {ids},')
