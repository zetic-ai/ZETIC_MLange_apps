import os
import math
import numpy as np
import torch
import torch.nn as nn

# ---------------------------------------------------------
# CONSTANTS (Matches Real Model Architecture)
# ---------------------------------------------------------
INPUT_DIM = 48         
HIDDEN_SIZE = 768      
FFN_DIM = 3072         
OUTPUT_BINS = 336      
NUM_LAYERS = 12        
SEQ_LENGTH = 512       

class PatchEmbedding(nn.Module):
    def __init__(self, in_dim, hidden_dim, out_dim):
        super().__init__()
        self.hidden_layer = nn.Linear(in_dim, hidden_dim)
        self.output_layer = nn.Linear(hidden_dim, out_dim)
        self.residual_layer = nn.Linear(in_dim, out_dim)
        self.act = nn.GELU()

    def forward(self, x):
        res = self.residual_layer(x)
        out = self.hidden_layer(x)
        out = self.act(out)
        out = self.output_layer(out)
        return res + out

class SimpleEncoderLayer(nn.Module):
    def __init__(self, hidden_size, ffn_dim, num_heads):
        super().__init__()
        if hidden_size % num_heads != 0:
            raise ValueError("hidden_size must be divisible by num_heads")
        self.num_heads = num_heads
        self.head_dim = hidden_size // num_heads
        self.scale = self.head_dim ** -0.5
        self.q_proj = nn.Linear(hidden_size, hidden_size)
        self.k_proj = nn.Linear(hidden_size, hidden_size)
        self.v_proj = nn.Linear(hidden_size, hidden_size)
        self.out_proj = nn.Linear(hidden_size, hidden_size)
        self.ffn = nn.Sequential(
            nn.Linear(hidden_size, ffn_dim),
            nn.GELU(),
            nn.Linear(ffn_dim, hidden_size),
        )
        self.norm1 = nn.LayerNorm(hidden_size)
        self.norm2 = nn.LayerNorm(hidden_size)

    def forward(self, x):
        bsz, seq_len, hidden = x.shape
        q = self.q_proj(x)
        k = self.k_proj(x)
        v = self.v_proj(x)
        q = q.view(bsz, seq_len, self.num_heads, self.head_dim).transpose(1, 2)
        k = k.view(bsz, seq_len, self.num_heads, self.head_dim).transpose(1, 2)
        v = v.view(bsz, seq_len, self.num_heads, self.head_dim).transpose(1, 2)
        attn_scores = torch.matmul(q, k.transpose(-2, -1)) * self.scale
        attn_probs = torch.softmax(attn_scores, dim=-1)
        attn_out = torch.matmul(attn_probs, v)
        attn_out = attn_out.transpose(1, 2).contiguous().view(bsz, seq_len, hidden)
        x = self.norm1(x + self.out_proj(attn_out))
        x = self.norm2(x + self.ffn(x))
        return x

class SimpleTransformerEncoder(nn.Module):
    def __init__(self, hidden_size, ffn_dim, num_heads, num_layers):
        super().__init__()
        self.layers = nn.ModuleList(
            [SimpleEncoderLayer(hidden_size, ffn_dim, num_heads) for _ in range(num_layers)]
        )

    def forward(self, x):
        for layer in self.layers:
            x = layer(x)
        return x

class MockChronosFull(nn.Module):
    def __init__(self):
        super().__init__()
        self.input_patch_embedding = PatchEmbedding(INPUT_DIM, FFN_DIM, HIDDEN_SIZE)
        
        # Encoder (Full 12 Layers) using ONNX-friendly ops
        self.encoder = SimpleTransformerEncoder(
            HIDDEN_SIZE,
            FFN_DIM,
            num_heads=12,
            num_layers=NUM_LAYERS,
        )
        
        self.output_patch_embedding = nn.Sequential(
            nn.Linear(HIDDEN_SIZE, FFN_DIM),
            nn.GELU(),
            nn.Linear(FFN_DIM, OUTPUT_BINS)
        )

    def forward(self, patch_input, attention_mask):
        # 1. Embed
        x = self.input_patch_embedding(patch_input)
        
        # 2. APPLY MASK
        # We explicitly use attention_mask so it stays in the traced graph.
        # mask is [Batch, Seq]. x is [Batch, Seq, Hidden].
        # We unsqueeze to [Batch, Seq, 1], cast to Float, and multiply.
        mask_float = attention_mask.unsqueeze(-1).to(x.dtype)
        x = x * mask_float
        
        # 3. Encoder
        x = self.encoder(x)
        
        # 4. Project
        logits = self.output_patch_embedding(x)
        return logits

def main():
    # Setup Directories
    project_name = "chronos-2"
    base_dir = os.path.join("model_zoo", project_name)
    model_dir = os.path.join(base_dir, "model")
    inputs_dir = os.path.join(base_dir, "inputs")
    
    os.makedirs(model_dir, exist_ok=True)
    os.makedirs(inputs_dir, exist_ok=True)

    # Initialize Model
    print("Initializing Model...")
    # Disable Transformer fastpath to avoid fused ops that ONNX export can't handle
    try:
        torch.backends.mha.set_fastpath_enabled(False)
    except AttributeError:
        pass
    torch.manual_seed(42)
    model = MockChronosFull().to(dtype=torch.float32)
    model.eval()

    # Generate Inputs
    print("Generating inputs...")
    patch_input = torch.randn(1, SEQ_LENGTH, INPUT_DIM, dtype=torch.float32)
    attention_mask = torch.ones(1, SEQ_LENGTH, dtype=torch.float32)

    input_path = os.path.join(inputs_dir, "patch_input.npy")
    mask_path = os.path.join(inputs_dir, "attention_mask.npy")
    
    np.save(input_path, patch_input.cpu().numpy().astype(np.float32))
    np.save(mask_path, attention_mask.cpu().numpy().astype(np.float32))
    print("Inputs saved.")

    # Trace and save TorchScript (.pt)
    pt_path = os.path.join(model_dir, f"{project_name}.pt")
    print(f"Tracing to {pt_path}...")
    try:
        with torch.no_grad():
            traced_model = torch.jit.trace(
                model,
                (patch_input, attention_mask),
                strict=False,
                check_trace=False
            )
        torch.jit.save(traced_model, pt_path)
        print("Trace successful.")
    except Exception as e:
        print(f"[ERROR] Failed to trace model: {e}")
        import traceback
        traceback.print_exc()
        exit(1)

    print("\n" + "="*50)
    print("TEST COMMAND")
    print("="*50)
    print(f"./unit_test/test_bash/test_qnn_converter.sh \\")
    print(f"  {pt_path} \\")
    # Note: Order must match input_names list above
    print(f"  {input_path},{mask_path} \\")
    print(f"  {base_dir}/output_results_full")
    print("="*50)

if __name__ == "__main__":
    main()