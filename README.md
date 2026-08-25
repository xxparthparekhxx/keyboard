<div align="center">

# ⌨️ Compose Keyboard

### A Modern, Neural-Powered Android Keyboard Built Entirely with Jetpack Compose

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack_Compose-BOM_2024.06-4285F4?logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Material_3-Dynamic_Color-E8710A?logo=materialdesign&logoColor=white)](https://m3.material.io)
[![API](https://img.shields.io/badge/API-24%2B-brightgreen)](https://developer.android.com/about/versions/nougat)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Compose Keyboard** is a high-performance Android IME featuring a **custom-trained neural swipe decoder** — a 634K-parameter temporal convolutional network trained on ~900,000 real human swipe gestures, running entirely on-device in hand-written Kotlin with **zero inference dependencies**.


[Features](#-features) · [Neural Engine](#-neural-swipe-engine) · [Architecture](#-architecture) · [Getting Started](#-getting-started) · [Model Weights](#-model-weights-hugging-face) · [Training](#-training-the-model) · [Contributing](#-contributing)

</div>

---

## ✨ Features

### 🧠 Neural Glide / Swipe Typing
- **Custom-trained TCN encoder** — 634K parameters, trained from scratch on the [FUTO Swipe Dataset](https://huggingface.co/datasets/futo-org/swipe.futo.org) (~900,000 real human swipes)
- **Layout-agnostic by design** — the network emits spatial patterns via a 2D cosine basis, not per-key scores. The keyboard geometry is read at runtime, so the same weights serve any layout, screen size, or orientation with zero retraining
- **CTC + trie-constrained beam search** — resolves doubled letters (*putt* vs *put*) from gesture **timing**, a distinction that is geometrically undecidable
- **On-device, zero-dependency inference** — the entire forward pass is hand-written Kotlin (~430 lines), with no TFLite, ONNX, or any inference library
- **92% top-1 accuracy** on the FUTO test set (97.7% top-3) — with automatic fallback to the geometric (SHARK²-family) decoder if the model asset is unavailable
- **Dynamic word learning** — learns from your typing and selections, saved persistently

### 🎨 Theming & Customization


- **8 built-in themes**: Material Dark, Material Light, Pitch Black AMOLED, Dynamic Material You (Dark/Light), Nordic Frost, Sunset Glow, Cyber Neon
- **Custom Theme Editor**: Full HSV color picker with hex input, 20 preset swatches, and live interactive mini-keyboard preview
- Customize 5 primary elements: Background, Key Surfaces, Text Color, Accent Keys, Action Keys
- Smooth animated theme transitions

### 📋 Clipboard Manager
- Automatic clipboard history capture across all apps
- Up to 50 stored clips with timestamps and search
- Pin important clips to prevent expiration
- Quick paste directly from the keyboard header

### 😊 Emoji Picker
- Categorized emoji grid (Smileys, Gestures, Hearts, Animals, Food, Objects)
- Smooth scrolling with quick category navigation

### ⚡ Performance
- **Zero-recomposition** swipe trail rendering — operates purely in Compose's draw phase (`drawWithContent`) via `trailVersion`, never triggering recomposition or relayout
- **Pre-allocated buffers** in all hot loops — the neural decoder, geometric scorer, and beam search allocate nothing during a gesture
- **Batched `InputConnection` operations** — all edits wrapped in `beginBatchEdit()` / `endBatchEdit()`
- **~2.5 MB model** with BatchNorm folded into convolutions at export time — no normalization layers at inference

### 🔧 More
- Full QWERTY with long-press special character popups
- Shift, Caps Lock, and number/symbol layer switching
- Spacebar drag for cursor navigation
- Continuous backspace with hold-to-delete
- Smart auto-capitalization after sentence-ending punctuation
- Toggleable dedicated number row & adjustable keyboard height
- Companion App with setup wizard and interactive typing sandbox

---

## 🧠 Neural Swipe Engine



The swipe decoder is the core technical contribution of this project. Unlike traditional geometric decoders that compare gesture shapes against ideal polylines, this system **learns to read user intention** directly from the raw touch trajectory — including its timing.

### Architecture Overview

```
Touch Input (variable length, variable Hz)
    │
    ▼
┌─────────────────────────────┐
│  60 Hz Resample → 64 points │   Normalizes across 60/90/120/240 Hz panels
│  (uniform in TIME, not arc) │   Preserves dwell/hesitation signal
└─────────────────────────────┘
    │
    ▼
┌─────────────────────────────┐
│  Savitzky-Golay Features    │   7-tap quadratic: pos, vel, acc, speed, curvature
│  (8 channels × 64 steps)   │   Differentiates without amplifying touch noise
└─────────────────────────────┘
    │
    ▼
┌─────────────────────────────┐
│  TCN Backbone               │   5 ConvNeXt-v2 blocks (dilations 1,2,3,5,8)
│  (128-dim, 7-wide depthwise)│   GRN + Squeeze-Excite + GLU MLP
│  634K parameters            │   BatchNorm folded at export → zero norm layers
└─────────────────────────────┘
    │
    ▼
┌─────────────────────────────┐
│  Spectral Spatial Head      │   Emits 64 cosine coefficients per timestep
│  + Intention Gate (λ)       │   Layout read via fixed basis Φ(x,y)
│  → 32 output steps          │   ← THIS is what makes it layout-agnostic
└─────────────────────────────┘
    │
    ▼
┌─────────────────────────────┐
│  Trie-Constrained CTC Beam │   Blank semantics separate "putt" from "put"
│  Search (width=50)          │   Length-aware pruning prevents short-word bias
│  150K word lexicon          │   Tuned scoring: γ, λ_freq, β_len
└─────────────────────────────┘
    │
    ▼
  Top-K Candidates → Suggestion Bar
```

### Why Layout-Agnostic?

The key insight: the network never learns a parameter per key. Instead, it emits a 2D spatial *pattern* as 64 cosine coefficients (an 8×8 DCT), and the active keyboard is sampled from that pattern:

```
Φ[k, (u,v)] = cos(π · u · xₖ) · cos(π · v · yₖ)
```

Swapping layouts — or even resizing the keyboard with the height slider — just rebuilds this fixed basis matrix. The network never needs to be retrained.

### Why CTC + Timing?

The geometric decoder resamples by **arc length**, which deliberately discards speed information. This makes shape matching speed-invariant, but also makes it *fundamentally impossible* to distinguish words that trace the same path:

- **"putt" vs "put"** — same geometric path, but the finger *dwells* on the second T
- **"on" vs "ion"** — I sits on a straight line between O and N

CTC blank semantics solve this: extending a prefix by the character it already ends with is only allowed from the blank-ending mass. The model must emit a blank between repeated letters, which it will only do if the finger actually lingered.

### Results

| Decoder | Lexicon | Top-1 | Top-3 | Top-10 |
|---------|---------|-------|-------|--------|
| **Neural (this project)** | App 35K + eval | **92.09%** | **97.69%** | **98.59%** |
| Neural | App 35K | 85.78% | 91.05% | 91.88% |
| Geometric (SHARK²-family) | App 35K | ~73% | — | — |

*Evaluated on 47,552 test gestures from the FUTO Swipe Dataset.*

---

## 📁 Architecture

```
keyboard/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/composekeyboard/
│   │   │   ├── MainActivity.kt                      # Setup Wizard, Test Sandbox & Settings
│   │   │   ├── data/
│   │   │   │   ├── ClipboardHistoryManager.kt       # Persistent local clipboard manager
│   │   │   │   ├── KeyboardData.kt                  # Layout matrices & emoji datasets
│   │   │   │   ├── KeyboardPreferences.kt           # Preference management & StateFlow
│   │   │   │   └── SwipeDictionary.kt               # Word dictionary & vocabulary learning
│   │   │   ├── input/swipe/
│   │   │   │   ├── SwipeController.kt               # Coordinates gestures, decoder & trail
│   │   │   │   ├── SwipeDecoder.kt                  # Geometric decoder (fallback path)
│   │   │   │   ├── SwipeGestureDetector.kt          # Pointer input gesture detector
│   │   │   │   ├── SwipeKeyGeometry.kt              # Key bounding boxes & centers
│   │   │   │   ├── SwipeKeyMap.kt                   # Frozen key geometry for the decode thread
│   │   │   │   ├── SwipeTrace.kt                    # Raw swipe path & resampling
│   │   │   │   └── nn/
│   │   │   │       ├── SwipeNet.kt                  # TCN forward pass, pure Kotlin (430 LOC)
│   │   │   │       ├── SwipeBeam.kt                 # Lexicon trie + CTC beam search
│   │   │   │       └── SwipeNeuralDecoder.kt        # Preprocessing, layout basis & orchestration
│   │   │   ├── service/
│   │   │   │   └── ComposeInputMethodService.kt     # Android InputMethodService + Compose bridge
│   │   │   ├── theme/
│   │   │   │   ├── Color.kt                         # Theme color palettes
│   │   │   │   └── Theme.kt                         # Keyboard theme provider & custom colors
│   │   │   └── ui/
│   │   │       ├── keyboard/
│   │   │       │   ├── ClipboardView.kt             # In-keyboard Clipboard History panel
│   │   │       │   ├── EmojiPicker.kt               # Categorized Emoji grid
│   │   │       │   ├── KeyboardHeader.kt            # Action toolbar (Emoji, Clipboard, Themes, Settings)
│   │   │       │   ├── KeyboardKey.kt               # Key component with press effects & haptics
│   │   │       │   ├── KeyboardScreen.kt            # Main keyboard composable layout
│   │   │       │   ├── QuickSettingsView.kt         # In-keyboard quick settings panel
│   │   │       │   ├── SuggestionBar.kt             # Swipe candidate predictions strip
│   │   │       │   ├── SwipeTrail.kt                # GPU-rendered swipe gesture trail
│   │   │       │   └── ThemePicker.kt               # Visual theme picker sheet
│   │   │       └── theme/
│   │   │           ├── ColorPicker.kt               # HSV Color Picker Dialog with presets
│   │   │           └── CustomThemeEditor.kt         # Live interactive custom theme editor
│   │   ├── assets/
│   │   │   ├── swipe_words.txt                      # 150,289 words with log-frequency scores
│   │   │   └── swipe_encoder.bin                    # Trained encoder weights (2.5 MB, fp32)
│   │   ├── res/
│   │   │   ├── xml/method.xml                       # Input method configuration
│   │   │   └── values/strings.xml                   # String resources
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── ml/                                              # Model training pipeline (not shipped)
│   ├── swipe/
│   │   ├── model.py                                 # SwipeEncoder architecture (PyTorch)
│   │   ├── train.py                                 # CTC + emission-count training loop
│   │   ├── augment.py                               # Co-augmentation (trajectory + layout)
│   │   ├── synth.py                                 # Physics-based synthetic gesture generation
│   │   ├── beam.py                                  # Reference trie-constrained beam search
│   │   ├── preprocess.py                            # FUTO dataset → packed arrays
│   │   ├── layout.py                                # Keyboard layouts in normalized space
│   │   └── ...
│   └── tools/
│       ├── export_weights.py                        # PyTorch → swipe_encoder.bin (BN folding)
│       ├── tune_scoring.py                          # Bayesian beam pruning & scoring tuning
│       └── eval_*.py                                # Accuracy evaluation harnesses
├── gradle/
│   └── libs.versions.toml                           # Gradle Version Catalog
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🚀 Getting Started

### Prerequisites

| Requirement | Version |
|-------------|---------|
| Android Studio | Hedgehog (2023.1.1) or newer |
| JDK | 17 |
| Android SDK | API 35 (compileSdk) |
| Min Android Version | 7.0 Nougat (API 24) |
| Kotlin | 2.0.0 |

### Build & Install

```bash
# Clone the repository
git clone https://github.com/xxparthparekhxx/keyboard.git
cd keyboard

# Build debug APK
./gradlew assembleDebug

# Install on connected device/emulator
adb install -r app/build/outputs/apk/debug/composekeyboard-debug-universal.apk
```

### Setup on Device

1. **Open the app** → The Setup Wizard will guide you through activation
2. **Enable** "Compose Keyboard" in **System Settings → Languages & Input → On-screen keyboard**
3. **Select** "Compose Keyboard" as your default input method
4. Start typing! Swipe across letters to use gesture typing

### ADB Quick Setup

```bash
# Enable the keyboard
adb shell ime enable com.example.composekeyboard/.service.ComposeInputMethodService

# Set as active keyboard
adb shell ime set com.example.composekeyboard/.service.ComposeInputMethodService

# Launch the companion app
adb shell am start -n com.example.composekeyboard/.MainActivity

# View logs
adb logcat -s ComposeKeyboard:V SwipeNeural:V AndroidRuntime:E
```

---

## 🤗 Model Weights (Hugging Face)

The trained neural swipe encoder weights are hosted on Hugging Face for easy download:

### 📦 [`xxparthparekhxx/compose-keyboard-swipe-encoder`](https://huggingface.co/xxparthparekhxx/compose-keyboard-swipe-encoder)

| File | Size | Description |
|------|------|-------------|
| `swipe_encoder.bin` | 2.5 MB | Trained encoder weights (fp32, BN folded) |
| `swipe_words.txt` | 1.7 MB | 150,289-word lexicon with log-frequency scores |

### Download the Model

#### Option 1: Direct Download (Recommended)

```bash
# Download the encoder weights
curl -L https://huggingface.co/xxparthparekhxx/compose-keyboard-swipe-encoder/resolve/main/swipe_encoder.bin \
  -o app/src/main/assets/swipe_encoder.bin

# Download the lexicon (if needed)
curl -L https://huggingface.co/xxparthparekhxx/compose-keyboard-swipe-encoder/resolve/main/swipe_words.txt \
  -o app/src/main/assets/swipe_words.txt
```

#### Option 2: Using `hf` CLI

```bash
pip install huggingface_hub

# Download both files at once
hf download xxparthparekhxx/compose-keyboard-swipe-encoder \
  --local-dir app/src/main/assets/ \
  --include "swipe_encoder.bin" "swipe_words.txt"
```

#### Option 3: Python API

```python
from huggingface_hub import hf_hub_download

# Download encoder weights
hf_hub_download(
    repo_id="xxparthparekhxx/compose-keyboard-swipe-encoder",
    filename="swipe_encoder.bin",
    local_dir="app/src/main/assets/"
)
```

### Upload Your Own Trained Model

If you retrain the model (see [Training](#-training-the-model)), you can upload your weights:

```bash
pip install huggingface_hub
hf auth login

# Create the repo (first time only)
hf repo create compose-keyboard-swipe-encoder --type model

# Upload the exported weights
hf upload xxparthparekhxx/compose-keyboard-swipe-encoder \
  app/src/main/assets/swipe_encoder.bin swipe_encoder.bin

# Upload the lexicon
hf upload xxparthparekhxx/compose-keyboard-swipe-encoder \
  app/src/main/assets/swipe_words.txt swipe_words.txt
```

### Binary Format Specification

The `swipe_encoder.bin` file uses a custom flat binary format parsed directly into memory:

```
Header:
  [4 bytes] Magic: "SWEN" (Swipe ENcoder)
  [4 bytes] Version: 1 (little-endian int32)
  [4 bytes] Tensor count (little-endian int32)

Per tensor:
  [4 bytes] Name length (int32)
  [N bytes] Name (UTF-8)
  [4 bytes] Number of dimensions (int32)
  [D×4 bytes] Dimension sizes (int32 each)
  [∏dims × 4 bytes] Data (float32, little-endian)
```

BatchNorm layers are **folded into the preceding convolution** at export time — the on-device forward pass has no normalization layers at all.

---

## 🏋️ Training the Model

### Prerequisites

```bash
cd ml
python -m venv .venv
source .venv/bin/activate
pip install torch numpy orjson huggingface_hub
```

### 1. Download the FUTO Swipe Dataset

The model is trained on the [FUTO Swipe Dataset](https://huggingface.co/datasets/futo-org/swipe.futo.org), a corpus of ~900,000 real human swipe gestures:

```bash
python tools_download.py
# Downloads to ml/data/hf/ (~2 GB)
```

### 2. Preprocess

Pack the raw JSONL data into training-ready NumPy arrays:

```bash
python -m swipe.preprocess
# Output: ml/data/packed/{train,dev,test}/
# ~896K training swipes, resampled to 64 points uniform in time
```

### 3. Train

```bash
python -m swipe.train --epochs 120 --batch 1024 --lr 1e-3
# ~82 minutes on a modern GPU
# Best greedy word accuracy: ~64.8% (lexicon-free CTC)
# Checkpoints saved to ml/runs/encoder/
```

The training loop uses:
- **CTC loss** with an emission-count penalty to prevent under-emission
- **Co-augmentation** — trajectory and keyboard layout are augmented *jointly* (rotation, flip, scale, shear, translation, time reversal) to force the model to read intention from the gesture, not memorize key positions
- **Cosine LR schedule** with 5% warmup
- **Mixed precision** (bf16) with `torch.compile`

### 4. Tune Beam Search Scoring

```bash
python tools/tune_scoring.py --ckpt runs/encoder/best.pt --n 12000 --prune-trials 25 --score-trials 2500
# Optimizes γ_prune, β_prune, γ_score, λ_freq, β_len on the dev set
```

### 5. Evaluate

```bash
# With extended lexicon (eval words added to dictionary)
python tools/eval_neural.py --split test --extend --beam 100
# → top-1 92.09%  top-3 97.69%  top-10 98.59%

# Without extended lexicon (app's actual 35K dictionary)
python tools/eval_neural.py --split test --beam 100
# → top-1 85.78%  top-3 91.05%  top-10 91.88%
```

### 6. Export Weights

```bash
python tools/export_weights.py --ckpt runs/encoder/best.pt --out ../app/src/main/assets/swipe_encoder.bin
# Folds BatchNorm, writes flat binary, generates reference vectors
```

### 7. Upload to Hugging Face

```bash
hf upload xxparthparekhxx/compose-keyboard-swipe-encoder \
  ../app/src/main/assets/swipe_encoder.bin swipe_encoder.bin
```

---

## 🛠️ Tech Stack

| Component | Technology | Version |
|-----------|------------|---------|
| Language | Kotlin | 2.0.0 |
| UI Framework | Jetpack Compose | BOM 2024.06.00 |
| Design System | Material 3 | Latest |
| Build System | Gradle + AGP | 8.7 / 8.4.2 |
| ML Training | PyTorch | Latest |
| Dataset | FUTO Swipe Corpus | ~900K gestures |
| Min SDK | Android 7.0 | API 24 |
| Target SDK | Android 15 | API 35 |
| JVM | OpenJDK | 17 |

---

## 🧪 How It Works (Deep Dive)

### Synthetic Gesture Generation

The training pipeline includes a physics-based gesture synthesizer ([`ml/swipe/synth.py`](ml/swipe/synth.py)) that generates realistic training data:

1. **2/3 Power Law** — finger speed falls as curvature rises (v ∝ κ⁻¹ᐟ³)
2. **Doubled Letter Dwells** — the finger lingers on repeated keys, creating the only signal that separates "putt" from "put"
3. **Corner Cutting** — a moving finger rounds corners toward the inside of the turn
4. **Structured Spatial Error** — per-gesture systematic bias (hand position) on top of per-point tremor, scaling with speed
5. **Centripetal Catmull-Rom** interpolation prevents cusps on close control points

### On-Device Inference

The Kotlin inference engine ([`SwipeNet.kt`](app/src/main/java/com/example/composekeyboard/input/swipe/nn/SwipeNet.kt)) is a line-by-line transcription of the PyTorch model with performance optimizations:

- **8-wide accumulator unrolling** in the linear projection (~90% of compute) to fill ARM issue slots
- **Pre-allocated scratch buffers** — zero allocations during a gesture decode
- **Single-threaded by design** — threading was tested and reverted; on big.LITTLE phones, work landing on efficiency cores made the join 1.5–5× *slower*

### Compose IME Integration

[`ComposeInputMethodService.kt`](app/src/main/java/com/example/composekeyboard/service/ComposeInputMethodService.kt) bridges Android's `InputMethodService` with Jetpack Compose by implementing `LifecycleOwner`, `ViewModelStoreOwner`, and `SavedStateRegistryOwner` — allowing full Compose rendering inside a system service window.

---

## 📄 License

This project is open source under the **MIT License**. See [LICENSE](LICENSE) for details.

---

## 🙏 Acknowledgments

- **[FUTO](https://futo.org)** — for the open [Swipe Gesture Dataset](https://huggingface.co/datasets/futo-org/swipe.futo.org) that made training possible
- **[ConvNeXt V2](https://arxiv.org/abs/2301.00808)** — Global Response Normalization (GRN) inspiration
- **[SHARK²](https://dl.acm.org/doi/10.1145/1866029.1866043)** — foundational geometric swipe decoding research

---

<div align="center">

**Built with ❤️ in Kotlin & Compose**

*If you find this useful, consider ⭐ starring the repo!*

</div>
