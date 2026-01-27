import coremltools as ct

try:
    model_path = "apps/t5_base_grammar_correction/iOS/vennify_t5-base-grammar-correction_fp32.mlpackage"
    print(f"Loading model: {model_path}")
    mlmodel = ct.models.MLModel(model_path)
    
    print("\n--- Inputs ---")
    for i in mlmodel.input_description:
        print(f"Name: {i}")
        # text_model doesn't always expose shape easily in iterator, needed to check full spec
        # But let's try printing the spec description
        
    print("\n--- Spec Description ---")
    print(mlmodel.get_spec().description.input)
    
except Exception as e:
    print(f"Error: {e}")
