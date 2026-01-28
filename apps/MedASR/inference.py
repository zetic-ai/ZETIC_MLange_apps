
import os
import torch
import numpy as np
import huggingface_hub
from transformers import AutoProcessor, Wav2Vec2Processor

# --- Auth ---
token = os.environ.get("HF_TOKEN")
if token:
    huggingface_hub.login(token=token)

# --- Paths ---
script_dir = os.path.dirname(os.path.abspath(__file__))
model_path = os.path.join(script_dir, "model/medasr.pt")
audio_path = os.path.join(script_dir, "test_audio.wav")
input_features_path = os.path.join(script_dir, "input/0_input0.npy")
attention_mask_path = os.path.join(script_dir, "input/1_input1.npy")
model_id = "google/medasr"
lm_filename = "lm_6.kenlm"
lm_path = os.path.join(script_dir, "model", lm_filename)

# --- 1. Load Tokenizer for Decoding ---
print(f"Loading tokenizer for decoding: {model_id}")
processor = None
tokenizer = None
try:
    from transformers import AutoTokenizer
    tokenizer = AutoTokenizer.from_pretrained(model_id, trust_remote_code=True)
    print("✅ AutoTokenizer loaded.")
except Exception as e:
    print(f"⚠️ AutoTokenizer failed: {e}")
    # Try Wav2Vec2CTCTokenizer specific fallback
    try:
        from transformers import Wav2Vec2CTCTokenizer
        # Sometimes vocab_file is explicitly needed.
        # But let's try just loading from pretrained.
        tokenizer = Wav2Vec2CTCTokenizer.from_pretrained(model_id, trust_remote_code=True)
        print("✅ Wav2Vec2CTCTokenizer loaded.")
    except Exception as e2:
         print(f"❌ Failed to load tokenizer: {e2}")
         
    # Fallback: Try loading raw tokenizer.json
    if tokenizer is None:
        try:
            print("Trying PreTrainedTokenizerFast from tokenizer.json...")
            from transformers import PreTrainedTokenizerFast
            tokenizer_file = huggingface_hub.hf_hub_download(repo_id=model_id, filename="tokenizer.json")
            tokenizer = PreTrainedTokenizerFast(tokenizer_file=tokenizer_file)
            print("✅ PreTrainedTokenizerFast loaded.")
        except Exception as e3:
             print(f"❌ Failed to load PreTrainedTokenizerFast: {e3}")

if tokenizer is None:
    print("Warning: No tokenizer available. Output will be raw IDs.")

# --- 2. Load Model ---
print(f"Loading TorchScript model: {model_path}")
if not os.path.exists(model_path):
    print(f"❌ Model file not found: {model_path}")
    exit(1)

try:
    model = torch.jit.load(model_path)
    model.eval()
    print("✅ Model loaded successfully.")
except Exception as e:
    print(f"❌ Failed to load model: {e}")
    exit(1)

# --- 3. Load Input (from audio.wav) ---
print(f"Loading audio: {audio_path}")
input_features = None
attention_mask = None
input_features_np = None
attention_mask_np = None

def _load_audio(path):
    try:
        import soundfile as sf
        audio, sr = sf.read(path)
        return audio, sr
    except Exception:
        try:
            import librosa
            audio, sr = librosa.load(path, sr=None)
            return audio, sr
        except Exception as e:
            raise RuntimeError(f"Failed to load audio with soundfile/librosa: {e}")

try:
    audio, sampling_rate = _load_audio(audio_path)
    print(f"Audio loaded. shape={getattr(audio, 'shape', None)}, sr={sampling_rate}")
    processor = AutoProcessor.from_pretrained(model_id)
    processed = processor(
        audio,
        sampling_rate=sampling_rate,
        return_tensors="pt",
        padding=True,
    )
    input_features = processed.input_features
    attention_mask = getattr(processed, "attention_mask", None)
    print(f"Input features shape: {input_features.shape}")
    if attention_mask is not None:
        print(f"Attention mask shape: {attention_mask.shape}")

    input_features_np = input_features.detach().cpu().numpy().astype(np.float32)
    if attention_mask is not None:
        attention_mask_np = attention_mask.detach().cpu().numpy()

    # Persist regenerated inputs for traceability/debugging.
    os.makedirs(os.path.dirname(input_features_path), exist_ok=True)
    np.save(input_features_path, input_features_np)
    print(f"[OK] Saved input_features: {input_features_path}")
    if attention_mask_np is not None:
        np.save(attention_mask_path, attention_mask_np.astype(np.bool_))
        print(f"[OK] Saved attention_mask: {attention_mask_path}")
except Exception as e:
    print(f"❌ Failed to load/process audio: {e}")
    print("Falling back to precomputed input_features/attention_mask npy files.")
    try:
        input_features_np = np.load(input_features_path)
        input_features = torch.from_numpy(input_features_np)
        print(f"Input features shape: {input_features.shape}")
        if os.path.exists(attention_mask_path):
            attention_mask_np = np.load(attention_mask_path)
            attention_mask = torch.from_numpy(attention_mask_np)
            print(f"Attention mask shape: {attention_mask.shape}")
        else:
            attention_mask = None
    except Exception as e2:
        print(f"❌ Failed to load fallback input: {e2}")
        exit(1)

# --- 4. Models Inference ---
print("Running TorchScript inference...")
with torch.no_grad():
    if attention_mask is not None:
        ts_logits = model(input_features, attention_mask)
    else:
        ts_logits = model(input_features)

print("Running HF model inference...")
try:
    from transformers import AutoModelForCTC
    hf_model = AutoModelForCTC.from_pretrained(model_id)
    hf_model.eval()
    with torch.no_grad():
        if attention_mask is not None:
            hf_logits = hf_model(
                input_features=input_features, attention_mask=attention_mask
            ).logits
        else:
            hf_logits = hf_model(input_features=input_features).logits
except Exception as e:
    print(f"❌ Failed to run HF model: {e}")
    exit(1)

# --- 4c. Optional LM-based decoding (KenLM/pyctcdecode) ---
decoder = None
try:
    from pyctcdecode import build_ctcdecoder

    if not os.path.exists(lm_path):
        try:
            lm_path = huggingface_hub.hf_hub_download(
                repo_id=model_id, filename=lm_filename
            )
        except Exception as e:
            print(f"⚠️ Unable to download LM file: {e}")

    vocab = processor.tokenizer.get_vocab()
    vocab_list = [tok for tok, _ in sorted(vocab.items(), key=lambda x: x[1])]
    if os.path.exists(lm_path):
        decoder = build_ctcdecoder(vocab_list, kenlm_model_path=lm_path)
        print(f"✅ KenLM decoder ready: {lm_path}")
    else:
        decoder = build_ctcdecoder(vocab_list)
        print("⚠️ KenLM file not found; using decoder without LM.")
except Exception as e:
    print(f"⚠️ Failed to initialize KenLM decoder: {e}")

# --- 4b. ONNX Inference ---
print("Running ONNX model inference...")
onnx_path = os.path.join(script_dir, "model/medasr.onnx")
onnx_logits = None
try:
    import onnxruntime as ort

    sess = ort.InferenceSession(onnx_path, providers=["CPUExecutionProvider"])
    ort_inputs = {"input_features": input_features_np.astype(np.float32)}
    if attention_mask_np is not None:
        ort_inputs["attention_mask"] = attention_mask_np.astype(np.bool_)
    onnx_logits = sess.run(None, ort_inputs)[0]
except Exception as e:
    print(f"⚠️ Failed to run ONNX model: {e}")

# Logits shape: [N, T, C] (Batch, Time, Classes)
predicted_ids = torch.argmax(ts_logits, dim=-1)
hf_predicted_ids = torch.argmax(hf_logits, dim=-1)
onnx_predicted_ids = None
if onnx_logits is not None:
    onnx_predicted_ids = torch.from_numpy(onnx_logits).argmax(dim=-1)

# --- 5. Decode ---
print("Decoding outputs...")
def _decode(ids):
    if processor is not None:
        return processor.batch_decode(ids, skip_special_tokens=True, group_tokens=True)[0]
    if tokenizer is not None:
        return tokenizer.batch_decode(ids)[0]
    return None

def _decode_with_lm(logits_np):
    if decoder is None or logits_np is None:
        return None
    try:
        return decoder.decode(logits_np[0])
    except Exception as e:
        print(f"⚠️ KenLM decode failed: {e}")
        return None

def _ctc_collapse_decode(ids):
    if processor is None or ids is None:
        return None
    try:
        ids_list = ids[0].tolist() if hasattr(ids, "tolist") else list(ids[0])
        collapsed = []
        prev = None
        for token_id in ids_list:
            if token_id != prev:
                collapsed.append(token_id)
            prev = token_id
        pad_id = processor.tokenizer.pad_token_id
        collapsed = [i for i in collapsed if i != pad_id]
        tokens = processor.tokenizer.convert_ids_to_tokens(collapsed)
        return processor.tokenizer.convert_tokens_to_string(tokens)
    except Exception as e:
        print(f"⚠️ CTC collapse decode failed: {e}")
        return None

try:
    ts_text = _decode(predicted_ids)
    hf_text = _decode(hf_predicted_ids)
    onnx_text = _decode(onnx_predicted_ids) if onnx_predicted_ids is not None else None

    ts_lm_text = _decode_with_lm(ts_logits.detach().cpu().numpy())
    hf_lm_text = _decode_with_lm(hf_logits.detach().cpu().numpy())
    onnx_lm_text = _decode_with_lm(onnx_logits) if onnx_logits is not None else None

    ts_ctc_text = _ctc_collapse_decode(predicted_ids)
    hf_ctc_text = _ctc_collapse_decode(hf_predicted_ids)
    onnx_ctc_text = _ctc_collapse_decode(onnx_predicted_ids) if onnx_predicted_ids is not None else None

    if ts_text is not None:
        print(f"\nTorchScript Result: '{ts_text}'")
        print(f"HF Result: '{hf_text}'")
        if onnx_text is not None:
            print(f"ONNX Result: '{onnx_text}'")
        if ts_ctc_text is not None:
            print(f"\nTorchScript CTC Collapsed: '{ts_ctc_text}'")
            print(f"HF CTC Collapsed: '{hf_ctc_text}'")
            if onnx_ctc_text is not None:
                print(f"ONNX CTC Collapsed: '{onnx_ctc_text}'")
        if ts_lm_text is not None:
            print(f"\nTorchScript LM Result: '{ts_lm_text}'")
            print(f"HF LM Result: '{hf_lm_text}'")
            if onnx_lm_text is not None:
                print(f"ONNX LM Result: '{onnx_lm_text}'")
    else:
        print("No processor/tokenizer loaded, skipping decoding.")
        print(f"TorchScript IDs shape: {predicted_ids.shape}")
        print(f"HF IDs shape: {hf_predicted_ids.shape}")
        if onnx_predicted_ids is not None:
            print(f"ONNX IDs shape: {onnx_predicted_ids.shape}")
        print(f"TS first 10 IDs: {predicted_ids[0, :10]}")
        print(f"HF first 10 IDs: {hf_predicted_ids[0, :10]}")
        if onnx_predicted_ids is not None:
            print(f"ONNX first 10 IDs: {onnx_predicted_ids[0, :10]}")
except Exception as e:
    print(f"❌ Decoding failed: {e}")
    # Fallback to just printing shape/ids if decoding fails
    print(f"TorchScript IDs shape: {predicted_ids.shape}")
    print(f"HF IDs shape: {hf_predicted_ids.shape}")
    if onnx_predicted_ids is not None:
        print(f"ONNX IDs shape: {onnx_predicted_ids.shape}")
    print(f"TS first 10 IDs: {predicted_ids[0, :10]}")
    print(f"HF first 10 IDs: {hf_predicted_ids[0, :10]}")
    if onnx_predicted_ids is not None:
        print(f"ONNX first 10 IDs: {onnx_predicted_ids[0, :10]}")

# --- 6. Compare ---
print("Comparing TorchScript vs HF logits...")
try:
    diff = (ts_logits - hf_logits).abs()
    print(f"Max abs diff: {diff.max().item():.6f}")
    print(f"Mean abs diff: {diff.mean().item():.6f}")
except Exception as e:
    print(f"⚠️ Failed to compare logits: {e}")

if onnx_logits is not None:
    print("Comparing TorchScript vs ONNX logits...")
    try:
        ts_np = ts_logits.detach().cpu().numpy()
        diff_onnx = np.abs(ts_np - onnx_logits)
        print(f"Max abs diff: {diff_onnx.max():.6f}")
        print(f"Mean abs diff: {diff_onnx.mean():.6f}")
    except Exception as e:
        print(f"⚠️ Failed to compare ONNX logits: {e}")
