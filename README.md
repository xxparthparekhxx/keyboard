# Compose Keyboard

A modern, fast, and feature-rich Android Soft Keyboard (IME) built entirely with **Android Jetpack Compose**, **Kotlin 2.0**, and **Material 3**.

---

## ✨ Features

- **Glide / Swipe Typing (Gesture Typing)**:
  - **Neural swipe decoder** — a 634K-parameter temporal convolutional encoder trained from scratch on ~900,000 real human swipes, running entirely on-device in hand-written Kotlin with **no inference library or added dependency**.
  - Layout-agnostic by construction: the keyboard is fed to the model at runtime as key coordinates read through a fixed cosine basis, so the same weights serve any key geometry — including resizing, the height slider and landscape — with no retraining.
  - CTC emissions decoded by a trie-constrained beam search over the lexicon, which resolves doubled letters (*putt* vs *put*) from gesture **timing** — a distinction that is geometrically undecidable.
  - Falls back automatically to the previous geometric (SHARK²-family) decoder if the model asset is unavailable.
  - Smooth dynamic gesture trail animation rendered on the GPU with no per-frame recomposition.
  - Interactive **Suggestion Strip** displaying top candidate predictions and dynamic preview.
  - One-tap suggestion replacement that automatically learns and personalizes user vocabulary.
  - Seamless whole-word gesture backspace and auto-spacing.
  - Toggleable in Quick Settings and Companion App.
- **Custom Theme Creator & Color Picker**:
  - Full HSV Color Picker dialog (Hue spectrum slider, Saturation, Brightness, Hex `#RRGGBB`, and 20 preset swatches).
  - Customize all 5 primary elements: Keyboard Background, Key Surfaces, Letter/Text Color, Accent Keys (Shift/Backspace/?123), and Action Keys (Enter/Send).
  - Live interactive mini-keyboard preview while picking colors.
  - Custom themes save automatically to device storage and apply immediately.
- **In-Keyboard Theme Switcher**:
  - Tap the **Palette** icon in the keyboard header to open the theme selector grid showing real-time color dots, theme titles, and active checkmarks.
  - Supports 8 built-in themes plus your custom created theme (*Material You Dynamic*, *Dark Slate*, *Clean Light*, *Pitch Black AMOLED*, *Nordic Frost*, *Sunset Glow*, *Cyber Neon*, *Custom Theme*).
- **Local Clipboard History**:
  - Automatically captures copied text in the background across apps.
  - Tap the **Clipboard** icon to view, pin favorite items, delete clips, clear history, or tap any clip to instantly paste it.
  - Stored persistently across device reboots in local JSON storage.
- **Quick Settings Sheet & Launcher**:
  - In-keyboard quick toggles for Glide Typing, Haptic Vibration, Audio Click Sounds, Dedicated Number Row, and Auto-Capitalization.
  - One-tap button to open the full companion settings app.
- **QWERTY Layout & Gestures**:
  - Lowercase, Uppercase (Shift), and Caps Lock (double-tap Shift).
  - Drag left/right across the spacebar to smoothly navigate the text cursor.
  - Continuous backspace (press & hold for rapid deletion).
  - Categorized emoji keyboard (Smileys, Gestures, Hearts, Animals, Food, Objects).
- **Companion App & Setup Wizard**:
  - Real-time IME activation & default selection verification.
  - Interactive typing sandbox with swipe test area.

---

## 📁 Project Structure

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
│   │   │   │       ├── SwipeNet.kt                  # TCN encoder forward pass, pure Kotlin
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
│   │   │   ├── swipe_words.txt                      # Word lexicon with log-frequency weights
│   │   │   └── swipe_encoder.bin                    # Trained encoder weights (2.5 MB, fp32)
│   │   ├── res/
│   │   │   ├── xml/method.xml                       # Input method configuration
│   │   │   └── values/strings.xml                   # String resources
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── ml/                                              # Model training (not shipped in the APK)
│   ├── swipe/
│   │   ├── model.py                                 # Encoder architecture
│   │   ├── train.py                                 # CTC training loop
│   │   ├── augment.py                               # Joint trajectory + layout augmentation
│   │   ├── beam.py                                  # Reference beam search
│   │   ├── baseline.py                              # Geometric decoder, for comparison
│   │   ├── preprocess.py                            # Dataset packing
│   │   └── layout.py                                # Keyboard layouts in normalized space
│   └── tools/
│       ├── export_weights.py                        # Torch -> swipe_encoder.bin
│       ├── tune_scoring.py                          # Beam pruning & scoring constants
│       └── eval_*.py                                # Accuracy harnesses
├── gradle/
│   └── libs.versions.toml                           # Gradle Version Catalog
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🚀 Building and Running

### Build Debug APK
```bash
./gradlew assembleDebug
```

### Install via ADB
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
