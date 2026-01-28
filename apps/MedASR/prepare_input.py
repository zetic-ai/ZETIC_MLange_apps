#!/usr/bin/env python3
"""
Prepare inputs.npy from an image for DA3-SMALL.pt.
Outputs: input/0_input0.npy in shape (1, 1, 3, H, W).
"""
from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
from PIL import Image


def _load_image(path: Path, image_size: int) -> np.ndarray:
    img = Image.open(path).convert("RGB")
    img = img.resize((image_size, image_size), Image.BICUBIC)
    arr = np.asarray(img).astype(np.float32) / 255.0
    arr = np.transpose(arr, (2, 0, 1))  # (C, H, W)
    mean = np.array([0.485, 0.456, 0.406], dtype=np.float32)[:, None, None]
    std = np.array([0.229, 0.224, 0.225], dtype=np.float32)[:, None, None]
    arr = (arr - mean) / std
    return arr


def main():
    parser = argparse.ArgumentParser(description="Create input .npy for DA3-SMALL.pt")
    parser.add_argument("--image", required=True, help="Path to input image")
    parser.add_argument("--image-size", type=int, default=518, help="Resize H=W")
    parser.add_argument(
        "--out-dir",
        default="input",
        help="Output directory (relative to script location)",
    )
    args = parser.parse_args()

    img_path = Path(args.image)
    if not img_path.exists():
        raise FileNotFoundError(f"Image not found: {img_path}")

    x = _load_image(img_path, args.image_size)
    x = x[None, None, ...]  # (1, 1, 3, H, W)

    out_dir = Path(__file__).parent / args.out_dir
    out_dir.mkdir(parents=True, exist_ok=True)
    out_path = out_dir / "0_input0.npy"
    np.save(out_path, x.astype(np.float32))

    print(f"[OK] Saved input: {out_path}")


if __name__ == "__main__":
    main()





