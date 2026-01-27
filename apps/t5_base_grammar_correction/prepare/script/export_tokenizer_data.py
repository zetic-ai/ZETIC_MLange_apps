#!/usr/bin/env python3
"""
Export T5 tokenizer data for iOS app.
This script exports:
1. Enhanced vocabulary with token frequencies and special tokens info
2. Test cases for validation
3. Tokenizer configuration
"""

import json
import os
from pathlib import Path
from transformers import AutoTokenizer

def export_tokenizer_data():
    """Export all tokenizer data needed for iOS app."""
    
    model_id = "vennify/t5-base-grammar-correction"
    
    print(f"Loading tokenizer from: {model_id}")
    try:
        tokenizer = AutoTokenizer.from_pretrained(model_id)
        print(f"✅ Tokenizer loaded successfully.")
        print(f"   Vocab size: {tokenizer.vocab_size}")
        print(f"   Pad token ID: {tokenizer.pad_token_id}")
        print(f"   EOS token ID: {tokenizer.eos_token_id}")
        print(f"   UNK token ID: {tokenizer.unk_token_id}")
    except Exception as e:
        print(f"❌ Failed to load tokenizer: {e}")
        return False
    
    # Get script directory
    script_dir = Path(__file__).parent
    project_root = script_dir.parent.parent
    ios_view_dir = project_root / "iOS" / "T5GrammarCorrection-iOS" / "View"
    ios_view_dir.mkdir(parents=True, exist_ok=True)
    
    # 1. Export enhanced vocabulary with metadata
    print("\n1. Exporting enhanced vocabulary...")
    vocab_data = {
        "vocab_size": tokenizer.vocab_size,
        "pad_token_id": int(tokenizer.pad_token_id),
        "eos_token_id": int(tokenizer.eos_token_id),
        "unk_token_id": int(tokenizer.unk_token_id),
        "vocab": {}
    }
    
    vocab_size = tokenizer.vocab_size
    for i in range(vocab_size):
        try:
            token_str = tokenizer.convert_ids_to_tokens(i)
            vocab_data["vocab"][str(i)] = token_str
        except:
            vocab_data["vocab"][str(i)] = f"<unknown_{i}>"
        
        if (i + 1) % 5000 == 0:
            print(f"   Progress: {i + 1}/{vocab_size} tokens...")
    
    vocab_file = ios_view_dir / "t5_vocab.json"
    with open(vocab_file, "w", encoding="utf-8") as f:
        json.dump(vocab_data, f, ensure_ascii=False, indent=2)
    print(f"✅ Vocabulary saved to: {vocab_file}")
    print(f"   File size: {vocab_file.stat().st_size / 1024 / 1024:.2f} MB")
    
    # 2. Export test cases for validation
    print("\n2. Exporting test cases...")
    test_cases = [
        "grammar: He go to school yesterday",
        "grammar: I has a apple",
        "grammar: She don't likes it",
        "grammar: My grammar are bad",
        "grammar: I am write a letter",
        "grammar:",
        "grammar",
        "He",
        "go",
        "school",
        "yesterday",
        ":",
        " ",
        "▁",
    ]
    
    test_results = {}
    for text in test_cases:
        try:
            encoded = tokenizer(text, add_special_tokens=False)
            # Handle both tensor and list cases
            if hasattr(encoded["input_ids"], "tolist"):
                ids = encoded["input_ids"].tolist()
            else:
                ids = list(encoded["input_ids"])
            tokens = [tokenizer.convert_ids_to_tokens(int(tid)) for tid in ids]
            test_results[text] = {
                "ids": ids,
                "tokens": tokens
            }
        except Exception as e:
            test_results[text] = {"error": str(e)}
    
    test_file = ios_view_dir / "tokenizer_test_cases.json"
    with open(test_file, "w", encoding="utf-8") as f:
        json.dump(test_results, f, indent=2, ensure_ascii=False)
    print(f"✅ Test cases saved to: {test_file}")
    
    # 3. Export tokenizer config
    print("\n3. Exporting tokenizer config...")
    config = {
        "vocab_size": tokenizer.vocab_size,
        "model_max_length": tokenizer.model_max_length,
        "pad_token": tokenizer.pad_token,
        "pad_token_id": int(tokenizer.pad_token_id),
        "eos_token": tokenizer.eos_token,
        "eos_token_id": int(tokenizer.eos_token_id),
        "unk_token": tokenizer.unk_token,
        "unk_token_id": int(tokenizer.unk_token_id),
        "bos_token": str(tokenizer.bos_token) if tokenizer.bos_token else None,
        "bos_token_id": int(tokenizer.bos_token_id) if tokenizer.bos_token_id else None,
    }
    
    config_file = ios_view_dir / "tokenizer_config.json"
    with open(config_file, "w", encoding="utf-8") as f:
        json.dump(config, f, indent=2, ensure_ascii=False)
    print(f"✅ Config saved to: {config_file}")
    
    # 4. Export common token patterns for optimization
    print("\n4. Exporting common token patterns...")
    common_patterns = {
        "prefix_tokens": {
            "grammar": None,
            "▁grammar": None,
        },
        "punctuation": {
            ":": None,
            ".": None,
            ",": None,
            "!": None,
            "?": None,
        },
        "common_words": {
            "He": None,
            "▁He": None,
            "go": None,
            "▁go": None,
            "to": None,
            "▁to": None,
            "school": None,
            "▁school": None,
        }
    }
    
    # Fill in the token IDs
    for category, tokens in common_patterns.items():
        for token, _ in tokens.items():
            try:
                encoded = tokenizer(token, add_special_tokens=False)
                if encoded["input_ids"]:
                    common_patterns[category][token] = int(encoded["input_ids"][0])
            except:
                pass
    
    patterns_file = ios_view_dir / "tokenizer_patterns.json"
    with open(patterns_file, "w", encoding="utf-8") as f:
        json.dump(common_patterns, f, indent=2, ensure_ascii=False)
    print(f"✅ Patterns saved to: {patterns_file}")
    
    # 5. Verify critical tokens
    print("\n5. Verifying critical tokens...")
    critical_checks = {
        "grammar": "grammar",
        "▁grammar": "▁grammar",
        ":": ":",
        "▁He": "▁He",
        "▁go": "▁go",
    }
    
    print("   Critical token verification:")
    for name, token_str in critical_checks.items():
        try:
            encoded = tokenizer(token_str, add_special_tokens=False)
            if encoded["input_ids"]:
                token_id = int(encoded["input_ids"][0])
                decoded = tokenizer.convert_ids_to_tokens(token_id)
                match = decoded == token_str or decoded.replace("▁", "") == token_str.replace("▁", "")
                status = "✅" if match else "⚠️"
                print(f"   {status} {name}: ID={token_id}, token={repr(decoded)}")
            else:
                print(f"   ❌ {name}: No token ID found")
        except Exception as e:
            print(f"   ❌ {name}: Error - {e}")
    
    print("\n" + "="*60)
    print("✅ Tokenizer data export completed!")
    print("="*60)
    print(f"\nExported files:")
    print(f"  1. {vocab_file.name} - Full vocabulary")
    print(f"  2. {test_file.name} - Test cases for validation")
    print(f"  3. {config_file.name} - Tokenizer configuration")
    print(f"  4. {patterns_file.name} - Common token patterns")
    print("\nNext: Add these files to Xcode project Bundle Resources")
    print("="*60)
    
    return True

if __name__ == "__main__":
    export_tokenizer_data()
