---
language:
  - en
tags:
  - swipe-typing
  - gesture-recognition
  - keyboard
  - android
  - ctc
  - tcn
  - temporal-convolutional-network
  - on-device
  - mobile
  - ime
license: mit
library_name: custom
pipeline_tag: other
datasets:
  - futo-org/swipe.futo.org
model-index:
  - name: compose-keyboard-swipe-encoder
    results:
      - task:
          type: gesture-recognition
          name: Swipe Word Decoding
        dataset:
          type: futo-org/swipe.futo.org
          name: FUTO Swipe Gesture Dataset
          split: test
        metrics:
          - type: accuracy
            value: 89.07
            name: Top-1 Accuracy (shipped 150K-word dictionary)
          - type: accuracy
            value: 93.98
            name: Top-3 Accuracy (shipped 150K-word dictionary)
          - type: accuracy
            value: 92.00
            name: Top-1 Accuracy (dictionary plus missing test words)
          - type: accuracy
            value: 97.07
            name: Top-3 Accuracy (dictionary plus missing test words)
---

# Compose Keyboard — Swipe Encoder

A **633,697-parameter** (634K) temporal convolutional encoder for swipe/glide typing on Android, trained on ~900,000 real human gesture traces from the [FUTO Swipe Dataset](https://huggingface.co/datasets/futo-org/swipe.futo.org). The encoder follows the [FUTO Swipe paper](https://arxiv.org/abs/2606.25247) (Miller & Kostarevas, 2026), whose encoder is 635K parameters.

This model powers the neural swipe decoder in [Compose Keyboard](https://github.com/xxparthparekhxx/keyboard), an open-source Android IME built with Jetpack Compose.

## Model Description

| Property | Value |
|----------|-------|
| Architecture | Temporal Convolutional Network (TCN) |
| Parameters | 633,697 (634K) |
| Input | 64-point gesture trajectory (resampled uniform in time) |
| Output | 32 CTC emission frames over 26 letters + blank |
| Inference | Pure Kotlin, no dependencies (430 LOC) |
| File Size | 2.5 MB (fp32, BatchNorm folded) |
| Training Data | ~896K real swipes + co-augmentation |
| Top-1 Accuracy | **89.07%** with the shipped 150K-word dictionary (92.00% with missing test words added), FUTO test |
| Top-3 Accuracy | **93.98%** (97.07% with missing test words added) |

### Key Innovation: Layout Agnosticism

The network never learns a parameter per key. Instead, it emits a 2D spatial pattern as **64 cosine coefficients** (8×8 DCT), and the keyboard is read by sampling that pattern at each key's (x, y) position:

```
Φ[k, (u,v)] = cos(π · u · xₖ) · cos(π · v · yₖ)
```

This means the **same weights work for any keyboard layout, screen size, or orientation** — just rebuild the basis matrix.

### Why CTC?

Traditional geometric decoders resample by arc length, which discards timing information. This makes it fundamentally impossible to distinguish words that trace the same geometric path:

- **"putt" vs "put"** — same path, but the finger *dwells* on the repeated T
- **"on" vs "ion"** — I sits on a straight line between O and N

CTC blank semantics solve this: extending a prefix by the character it already ends with is only allowed from the blank-ending mass.

## Files

| File | Size | Description |
|------|------|-------------|
| `swipe_encoder.bin` | 2.5 MB | Trained weights (fp32, BN folded into convolutions) |
| `swipe_words.txt` | 1.7 MB | 150,289-word lexicon with log₂-frequency scores |

## Download & Usage

### For Compose Keyboard Users

Download the weights into the app's assets directory:

```bash
# Using curl
curl -L https://huggingface.co/xxparthparekhxx/compose-keyboard-swipe-encoder/resolve/main/swipe_encoder.bin \
  -o app/src/main/assets/swipe_encoder.bin

curl -L https://huggingface.co/xxparthparekhxx/compose-keyboard-swipe-encoder/resolve/main/swipe_words.txt \
  -o app/src/main/assets/swipe_words.txt
```

```bash
# Using huggingface-cli
pip install huggingface_hub
huggingface-cli download xxparthparekhxx/compose-keyboard-swipe-encoder \
  --local-dir app/src/main/assets/ \
  --include "swipe_encoder.bin" "swipe_words.txt"
```

```python
# Using Python
from huggingface_hub import hf_hub_download

hf_hub_download(
    repo_id="xxparthparekhxx/compose-keyboard-swipe-encoder",
    filename="swipe_encoder.bin",
    local_dir="app/src/main/assets/"
)
```

Then build and install the keyboard — the neural decoder will automatically load the weights on startup. Without the weights file, the keyboard gracefully falls back to the geometric (SHARK²-family) decoder.

### Binary Format

The `.bin` file uses a custom flat binary format:

```
Magic:   "SWEN" (4 bytes)
Version: 1      (int32 LE)
Count:   N      (int32 LE, number of tensors)

Per tensor:
  Name length  (int32 LE)
  Name         (UTF-8 bytes)
  Num dims     (int32 LE)
  Dim sizes    (int32 LE × num_dims)
  Data         (float32 LE × product of dims)
```

BatchNorm is folded into the preceding convolution at export time, so the on-device forward pass has zero normalization layers.

### Lexicon Format

`swipe_words.txt` is a tab-separated text file:

```
# swipe lexicon: 150289 words, score = round(log2(count)*10) clamped 1..255
you	248
the	244
to	240
...
```

Each line: `word<TAB>score`, where score = round(log₂(frequency) × 10), clamped to [1, 255].

## Architecture Details

```python
SwipeEncoder(
  features=TrajectoryFeatures(window=7, order=2),     # Savitzky-Golay → 8 channels
  stem=Conv1d(8, 128, kernel=5, padding=2),
  blocks=[                                            # 5× ConvNeXt-v2 blocks
    TCNBlock(128, dilation=1, expansion=4, kernel=7),  # depthwise → BN → GLU → GRN → SE
    TCNBlock(128, dilation=2, ...),
    TCNBlock(128, dilation=3, ...),
    TCNBlock(128, dilation=5, ...),
    TCNBlock(128, dilation=8, ...),
  ],
  adapter=Conv1d(128, 256, kernel=2, stride=2),       # T=64 → T=32
  coeff=Linear(256, 64),                              # 8×8 DCT coefficients
  gate=Linear(256, 1),                                # intention gate λ
)
```

**Input features** (8 channels): smoothed position (x, y), velocity (vx, vy), acceleration (ax, ay), speed (‖v‖), curvature (dθ/dt) — all derived via a 7-tap Savitzky-Golay filter.

**Co-augmentation** during training: the trajectory and keyboard layout are augmented *jointly* — rotation, flip, scale, shear, translation, and time reversal — so a model that works upside down cannot have learned "E is near the top left".

## Training

Trained for 120 epochs (~105K steps) on 1 GPU in ~82 minutes:

```bash
cd ml
python -m swipe.train --epochs 120 --batch 1024 --lr 1e-3
```

**Loss**: CTC + emission-count penalty (weight=0.05)
**Optimizer**: AdamW (β₁=0.9, β₂=0.999, wd=1e-4)
**Schedule**: Cosine decay from 1e-3 to 2e-5, 5% warmup
**Precision**: bf16 mixed precision with torch.compile

Post-training: beam search scoring constants are tuned on the dev set via Bayesian optimization.

## Results

| Decoder | Lexicon | Top-1 | Top-3 | Top-10 |
|---------|---------|-------|-------|--------|
| **Neural** | Shipped 150K | **89.07%** | **93.98%** | **95.02%** |
| Neural | Shipped 150K + missing test words | 92.00% | 97.07% | 98.16% |
| Geometric (SHARK²) | Shipped 150K | 73.50% | 82.90% | 87.19% |

*Evaluated on all 47,552 test gestures from the FUTO Swipe Dataset (checkpoint step 92,500, beam 100), re-run on 30 Oct 2026 against the shipped 150,289-word dictionary. "+ missing test words" adds every test word the dictionary lacks at a neutral prior (`--extend`), so it measures the decoder with no out-of-vocabulary misses; the first row is what the app does.*

## Citation

If you use this model or the Compose Keyboard project in your research, please cite:

```bibtex
@software{compose_keyboard_2024,
  title={Compose Keyboard: A Neural-Powered Android IME},
  author={Parth Parekh},
  url={https://github.com/xxparthparekhxx/keyboard},
  year={2024}
}

@article{miller_kostarevas_2026,
  title={FUTO Swipe: Layout-Agnostic Neural Swipe Decoding},
  author={David Lee Miller and Aleksandras Kostarevas},
  year={2026},
  eprint={2606.25247},
  archivePrefix={arXiv},
  primaryClass={cs.LG},
  url={https://arxiv.org/abs/2606.25247}
}
```
The encoder architecture follows the FUTO Swipe paper (arXiv:2606.25247): a layout-agnostic TCN that emits a 2D spatial pattern read out via a fixed cosine basis, with CTC blank semantics separating timing-distinct gestures such as "putt" vs "put".

## License

MIT License — see the [main repository](https://github.com/xxparthparekhxx/keyboard) for details.
