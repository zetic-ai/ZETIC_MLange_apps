#!/usr/bin/env python3
"""
Export T5 tokenizer vocabulary to JSON file for iOS app.
This script exports the vocabulary from the T5 grammar correction model
and saves it to the iOS app's View directory.
"""

import json
import os
import sys
from pathlib import Path
from transformers import AutoTokenizer

def export_vocab():
    """Export T5 tokenizer vocabulary to JSON file."""
    
    # Model ID - same as used in the app
    model_id = "vennify/t5-base-grammar-correction"
    
    print(f"Loading tokenizer from: {model_id}")
    try:
        tokenizer = AutoTokenizer.from_pretrained(model_id)
        print(f"✅ Tokenizer loaded successfully.")
        print(f"   Vocab size: {tokenizer.vocab_size}")
    except Exception as e:
        print(f"❌ Failed to load tokenizer: {e}")
        sys.exit(1)
    
    # Export vocabulary
    print("\nExporting vocabulary...")
    vocab_dict = {}
    
    # T5 uses SentencePiece, so we need to iterate through all token IDs
    vocab_size = tokenizer.vocab_size
    for i in range(vocab_size):
        try:
            # Get token string for this ID
            token_str = tokenizer.convert_ids_to_tokens(i)
            vocab_dict[str(i)] = token_str  # Use string key for JSON compatibility
        except Exception as e:
            # Skip invalid token IDs
            print(f"   Warning: Failed to get token for ID {i}: {e}")
            continue
        
        # Progress indicator
        if (i + 1) % 1000 == 0:
            print(f"   Progress: {i + 1}/{vocab_size} tokens exported...")
    
    print(f"✅ Exported {len(vocab_dict)} tokens.")
    
    # Determine output path
    # Get the script directory
    script_dir = Path(__file__).parent
    # Go up to prepare/, then to iOS/View/
    project_root = script_dir.parent.parent
    ios_view_dir = project_root / "iOS" / "T5GrammarCorrection-iOS" / "View"
    
    # Create directory if it doesn't exist
    ios_view_dir.mkdir(parents=True, exist_ok=True)
    
    output_path = ios_view_dir / "t5_vocab.json"
    
    print(f"\nSaving vocabulary to: {output_path}")
    try:
        with open(output_path, "w", encoding="utf-8") as f:
            json.dump(vocab_dict, f, ensure_ascii=False, indent=2)
        print(f"✅ Vocabulary saved successfully!")
        print(f"   File size: {output_path.stat().st_size / 1024 / 1024:.2f} MB")
    except Exception as e:
        print(f"❌ Failed to save vocabulary: {e}")
        sys.exit(1)
    
    # Verify critical tokens
    print("\nVerifying critical tokens...")
    critical_tokens = {
        "grammar": "grammar",
        "pad": "<pad>",
        "eos": "</s>",
        "unk": "<unk>",
    }
    
    # Also check for tokens with space prefix (T5 SentencePiece format)
    test_text = "grammar: He go to school"
    try:
        encoded = tokenizer(test_text, return_tensors="np", padding=False, truncation=True)
        token_ids = encoded["input_ids"][0].tolist()
        print(f"   Test encoding '{test_text}': {token_ids[:10]}...")
        
        # Check if we can find "grammar" token
        for token_id in token_ids[:5]:
            token_str = tokenizer.convert_ids_to_tokens(int(token_id))
            if "grammar" in token_str.lower():
                print(f"   ✅ Found 'grammar' token: ID={token_id}, token='{token_str}'")
                break
    except Exception as e:
        print(f"   Warning: Could not verify encoding: {e}")
    
    # Verify the exported file
    print("\nVerifying exported file...")
    try:
        with open(output_path, "r", encoding="utf-8") as f:
            loaded_vocab = json.load(f)
        
        print(f"   ✅ File verification successful!")
        print(f"   Loaded {len(loaded_vocab)} tokens from file")
        
        # Check a few key tokens
        if "0" in loaded_vocab:
            print(f"   Token ID 0: '{loaded_vocab['0']}'")
        if "1" in loaded_vocab:
            print(f"   Token ID 1: '{loaded_vocab['1']}'")
        if "2" in loaded_vocab:
            print(f"   Token ID 2: '{loaded_vocab['2']}'")
            
    except Exception as e:
        print(f"   ❌ File verification failed: {e}")
        sys.exit(1)
    
    print("\n" + "="*60)
    print("✅ Vocabulary export completed successfully!")
    print("="*60)
    print(f"\nNext steps:")
    print(f"1. Open Xcode project")
    print(f"2. Right-click on 'View' folder in T5GrammarCorrection-iOS")
    print(f"3. Select 'Add Files to T5GrammarCorrection-iOS...'")
    print(f"4. Select the file: {output_path}")
    print(f"5. Make sure 'Copy items if needed' is UNCHECKED")
    print(f"6. Make sure 'T5GrammarCorrection-iOS' target is CHECKED")
    print(f"7. Click 'Add'")
    print(f"\nOr manually add to project.pbxproj if needed.")
    print("="*60)

if __name__ == "__main__":
    export_vocab()
