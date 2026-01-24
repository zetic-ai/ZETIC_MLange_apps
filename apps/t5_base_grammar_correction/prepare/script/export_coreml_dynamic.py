import torch
import coremltools as ct
from transformers import AutoTokenizer, AutoModelForSeq2SeqLM
import os

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
        return outputs.logits

def export_model():
    model_name = "vennify/t5-base-grammar-correction"
    print(f"Loading model: {model_name}")
    
    tokenizer = AutoTokenizer.from_pretrained(model_name)
    model = AutoModelForSeq2SeqLM.from_pretrained(model_name)
    model.eval()
    
    wrapper = T5Wrapper(model)
    
    # Trace with dummy static inputs first to get the graph
    # Start with small seq len
    dummy_input_ids = torch.randint(0, 32000, (1, 10)).long()
    dummy_mask = torch.ones((1, 10)).long()
    dummy_decoder_ids = torch.randint(0, 32000, (1, 5)).long() # Length 5 to show it's not 1
    
    traced_model = torch.jit.trace(wrapper, (dummy_input_ids, dummy_mask, dummy_decoder_ids))
    
    # Define Dynamic Shapes
    # Range: 1 to 128
    input_ids_shape = ct.Shape(shape=(1, ct.RangeDim(1, 128, default=11)))
    attention_mask_shape = ct.Shape(shape=(1, ct.RangeDim(1, 128, default=11)))
    decoder_input_ids_shape = ct.Shape(shape=(1, ct.RangeDim(1, 128, default=5))) # Dynamic decoder!
    
    print("Converting to CoreML with Dynamic Shapes...")
    
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
        minimum_deployment_target=ct.target.iOS16 # Ensure wide support
    )
    
    save_path = "vennify_t5-base-grammar-correction-dynamic.mlpackage"
    mlmodel.save(save_path)
    print(f"Success! Model saved to {save_path}")
    print("Please use this model in your iOS app.")

if __name__ == "__main__":
    import numpy as np
    export_model()
