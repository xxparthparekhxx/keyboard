# AGENTS.md — Agent & Developer Guide for Compose Keyboard

This document provides AI coding agents and developers with essential context, architectural principles, development workflows, and coding conventions for the **Compose Keyboard** project.

---

## 1. Project Overview

**Compose Keyboard** is a modern, high-performance Android Soft Keyboard (Input Method Editor / IME) built entirely with **Android Jetpack Compose**, **Kotlin 2.0**, and **Material 3**.

### Technical Specifications
- **Application ID / Namespace**: `io.github.xxparthparekhxx.composekeyboard`
- **Minimum SDK**: 24 (Android 7.0 Nougat)
- **Target SDK**: 36 (Android 16) · **Compile SDK**: 37 (required by current AndroidX)
- **Kotlin Version**: 2.4.20 (AGP built-in Kotlin + Kotlin Compose Compiler Plugin; there is no `kotlin-android` plugin)
- **Android Gradle Plugin (AGP)**: 9.4.1
- **Gradle Version**: 9.7.1
- **JVM Target / Compatibility**: Java 17

---

## 2. Repository Structure

```
keyboard/
├── app/
│   ├── build.gradle.kts                             # Module build config & dependencies
│   ├── proguard-rules.pro                           # ProGuard / R8 rules
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml                  # Service declaration & permissions
│       │   ├── assets/
│       │   │   ├── swipe_words.txt                  # 150,289-word lexicon with log-frequency scores
│       │   │   ├── swipe_encoder.bin                # Trained encoder weights (2.5 MB, fp32)
│       │   │   └── emoji-test.txt                   # Unicode emoji data for the catalog
│       │   ├── java/io/github/xxparthparekhxx/composekeyboard/
│       │   │   ├── MainActivity.kt                  # Companion app host (setup, themes, clipboard, settings)
│       │   │   ├── data/
│       │   │   │   ├── Capitalization.kt            # Cursor-derived auto-shift from InputConnection
│       │   │   │   ├── ClipboardHistoryManager.kt   # Persistent JSON-backed clipboard storage
│       │   │   │   ├── EmojiCatalog.kt              # Emoji dataset parsed from emoji-test.txt
│       │   │   │   ├── EmojiSuggestions.kt          # Emoji predictions for typed words
│       │   │   │   ├── FieldInputKind.kt            # InputType class/variation → keyboard layout
│       │   │   │   ├── GraphemeClusters.kt          # User-visible character segmentation for delete
│       │   │   │   ├── InlineChipSpec.kt            # dp bounds of the inline autofill chip (service + chip composable)
│       │   │   │   ├── KeyboardData.kt              # Key layouts, symbols, emoji matrices, KeyType
│       │   │   │   ├── KeyboardPreferences.kt       # SharedPreferences + StateFlow settings
│       │   │   │   ├── OtpCodes.kt                  # One-time-passcode field & clipboard detection
│       │   │   │   ├── RecentEmojiManager.kt        # Recently-used emoji tracking
│       │   │   │   ├── SelectionTracker.kt          # Predicts where our own edits leave the caret (see §3A)
│       │   │   │   └── SwipeDictionary.kt           # Bucketed word lexicon & user-learning engine
│       │   │   ├── input/swipe/
│       │   │   │   ├── SwipeConstants.kt            # Centralized swipe tolerances & scoring weights (key-width units)
│       │   │   │   ├── SwipeController.kt           # Gesture state coordinator & decode dispatcher
│       │   │   │   ├── SwipeDecoder.kt              # Geometric fallback decoder (SHARK²-family)
│       │   │   │   ├── SwipeGestureDetector.kt      # PointerInputScope touch event recognizer
│       │   │   │   ├── SwipeKeyGeometry.kt          # Key bounding box tracker in window coords
│       │   │   │   ├── SwipeKeyMap.kt               # Immutable snapshot of key center coordinates
│       │   │   │   ├── SwipeTrace.kt                # Timestamped polyline buffer & arc-length sampler
│       │   │   │   └── nn/
│       │   │   │       ├── SwipeNet.kt              # TCN forward pass, pure Kotlin, zero dependencies
│       │   │   │       ├── SwipeBeam.kt             # Lexicon trie + CTC beam search
│       │   │   │       └── SwipeNeuralDecoder.kt    # Time-uniform resampling, layout basis & orchestration
│       │   │   ├── input/voice/                     # Whisper Tiny on-device dictation (download-on-first-use)
│       │   │   │   ├── VoiceInputController.kt      # Recording/transcription state machine
│       │   │   │   ├── VoiceRecorder.kt             # Audio capture to WAV
│       │   │   │   ├── WavPcm.kt                    # WAV encode/decode helpers
│       │   │   │   ├── WhisperAbi.kt                # arm64-v8a support gate
│       │   │   │   ├── WhisperCppEngine.kt          # JNI bridge to libwhisper.so
│       │   │   │   └── WhisperModelStore.kt         # Pinned, hash-verified model downloader
│       │   │   ├── service/
│       │   │   │   └── ComposeInputMethodService.kt # InputMethodService + Compose lifecycle bridge
│       │   │   ├── theme/
│       │   │   │   ├── Color.kt                     # Theme color palette presets
│       │   │   │   └── Theme.kt                     # CompositionLocal theme provider
│       │   │   └── ui/
│       │   │       ├── app/                         # Companion app screens (Home, Themes, Clipboard, Settings)
│       │   │   │   ├── AppearanceScreen.kt          # Appearance detail screen (theme presets, custom theme)
│       │   │   │   ├── ClipboardScreen.kt           # Clipboard history detail screen
│       │   │   │   ├── CompanionApp.kt              # Bottom-nav host & detail-screen scaffolding
│       │   │   │   ├── CompanionComponents.kt       # Shared companion components (groups, switches, setup steps)
│       │   │   │   ├── HomeScreen.kt                # Setup wizard + typing playground
│       │   │   │   ├── ImeStatus.kt                 # IME enabled/active checks via Settings.Secure
│       │   │   │   ├── SettingsScreen.kt            # Settings detail screen (toggles, sizes, voice entry)
│       │   │   │   └── VoiceSetupScreen.kt          # Voice setup (mic permission, model download)
│       │   │       ├── keyboard/
│       │   │   │   ├── ClipboardView.kt             # In-keyboard clipboard history panel
│       │   │   │   ├── EmojiPicker.kt               # Categorized emoji grid panel
│       │   │   │   ├── KeyboardHeader.kt            # Action toolbar (Emoji, Clipboard, Themes, Settings)
│       │   │   │   ├── KeyboardKey.kt               # Single key composable with press feedback & popup
│       │   │   │   ├── KeyboardScreen.kt            # Root keyboard composable & layout orchestration
│       │   │   │   ├── QuickSettingsView.kt         # In-keyboard quick toggles sheet
│       │   │   │   ├── SuggestionBar.kt             # Candidate prediction strip & dynamic preview
│       │   │   │   ├── SwipeTrail.kt                # GPU-rendered gesture trail (draw phase)
│       │   │   │   ├── ThemePicker.kt               # Visual theme picker sheet
│       │   │   │   └── VoiceInputView.kt            # In-keyboard voice dictation panel
│       │   │       └── theme/
│       │   │           ├── ColorPicker.kt           # HSV color picker dialog with hex/swatches
│       │   │           └── CustomThemeEditor.kt     # Live mini-keyboard preview custom theme editor
│       │   └── res/
│       │       ├── drawable/                        # Launcher icon layers (background, foreground, monochrome)
│       │       ├── mipmap-anydpi-v26/               # Adaptive launcher icons (API 26+)
│       │       ├── xml/method.xml                   # IME subtype & configuration
│       │       ├── xml/locales_config.xml           # Supported locales (en default, es)
│       │       ├── xml/backup_rules.xml             # Pre-Android-12 backup exclusions
│       │       ├── xml/data_extraction_rules.xml    # Android 12+ backup/migration exclusions
│       │       ├── values/strings.xml               # All user-visible text (English)
│       │       ├── values/themes.xml                # Companion-app window theme (transparent bars)
│       │       └── values-es/strings.xml            # Spanish translation
│       ├── test/                                    # JVM unit tests (JUnit + Robolectric)
│       │   ├── resources/swipe_reference.bin        # Torch parity fixture (see §5.5)
│       │   └── java/io/github/xxparthparekhxx/composekeyboard/
│       └── androidTest/                             # On-device Compose UI tests (emulator)
├── ml/                                              # Model training pipeline (not shipped)
│   ├── MODEL_CARD.md                                # Hugging Face model card for the encoder
│   ├── requirements.txt / pyproject.toml            # Pinned Python environment + pytest config
│   ├── run_after_training.sh                        # Post-training tune + eval driver
│   ├── show_progress.py                             # Live training/pipeline progress dashboard
│   ├── tests/                                       # CPU-only pytest suite (no dataset/GPU needed)
│   ├── swipe/                                       # Encoder, training, beam search, preprocessing
│   ├── tools/                                       # Lexicon build, evals, scoring tune, weight export
│   │   ├── build_lexicon.py                         # Builds the shipped word list (log-frequency scores)
│   │   ├── eval_app_layout.py                       # Checks encoder transfer to this app's key layout
│   │   ├── eval_baseline.py                         # Geometric baseline eval on the FUTO test split
│   │   ├── eval_neural.py                           # Neural encoder + beam eval on the FUTO test split
│   │   ├── export_weights.py                        # PyTorch → swipe_encoder.bin (+ parity fixture)
│   │   └── tune_scoring.py                          # Two-stage tune of pruning/scoring constants
│   ├── tools_download.py                            # Downloads the FUTO dataset (~2 GB) from Hugging Face
│   └── upload_to_hf.sh                              # Uploads model weights & lexicon to Hugging Face
├── .github/
│   ├── workflows/ci.yml                             # unit + python + release + instrumented jobs
│   ├── dependabot.yml                               # Weekly Gradle/Actions, monthly pip bumps
│   └── CODEOWNERS
├── tools/
│   └── build_dict.py                                # Lexicon preprocessor & contraction handler
├── .editorconfig                                  # Shared editor & code-style settings
├── .gitignore                                     # Keeps keystores, ML artifacts & build output out of git
├── CHANGELOG.md                                   # Keep-a-Changelog project history
├── CODE_OF_CONDUCT.md                             # Contributor conduct policy
├── CONTRIBUTING.md                                # Contribution workflow
├── LICENSE                                        # MIT license
├── SECURITY.md                                    # Vulnerability disclosure policy
├── docs/
│   └── images/                                    # Screenshots referenced by README.md
├── gradle.properties                              # Gradle JVM args & project settings
├── gradle/                                        # Version catalog (libs.versions.toml) & Gradle wrapper
├── gradlew                                        # Gradle wrapper launcher (Unix)
├── gradlew.bat                                    # Gradle wrapper launcher (Windows)
├── keystore.properties.example                    # Release signing config template (keystore.properties is gitignored)
├── build.gradle.kts                                 # Root build configuration
├── settings.gradle.kts                              # Project & repository settings
├── README.md                                        # User-facing project documentation
└── AGENTS.md                                        # Agent & developer guidelines (this file)
```

---

## 3. Core Architecture & Subsystems

### A. InputMethodService & Compose Integration
- Located in `ComposeInputMethodService.kt`.
- Implements `LifecycleOwner`, `ViewModelStoreOwner`, and `SavedStateRegistryOwner` to support Compose within an Android Service window.
- Renders `ComposeView` with `ViewCompositionStrategy.DisposeOnLifecycleDestroyed`.
- `onCreateInputView()` builds the `ComposeView` and attaches the service as `LifecycleOwner`, `ViewModelStoreOwner` and `SavedStateRegistryOwner` to both the view and the window `decorView` (`setViewTree*Owner`) — a Service window has no ambient owner, so the Compose view tree must be wired to one explicitly.
- `onStartInputView()` resets the gesture and typed-word state, seeds `SelectionTracker` from `EditorInfo.initialSel*`, captures the current clipboard, and derives the per-field state: IME action (masked from `imeOptions`, `IME_FLAG_NO_ENTER_ACTION` forces newline), OTP detection via `OtpCodes`, the incognito flag, `Capitalization` allowance, `FieldInputKind`, and a clipboard pre-fill of the OTP code when the field is an OTP field.
- `onFinishInputView()` and `onWindowHidden()` both cancel any in-flight voice recording, flush the in-progress typed word to the dictionary, and persist learned words immediately. They also call `leaveInputViewLifecycle()`, the counterpart of `enterInputViewLifecycle()` in `onStartInputView()`: the Compose lifecycle is stepped to RESUMED while the input view is visible and back to STOPPED when it is not.
- Uses `currentInputConnection` to perform batched text commits (`beginBatchEdit()`, `commitText()`, `deleteSurroundingText()`, `endBatchEdit()`).
- `SelectionTracker` (`data/`) predicts where each of our own edits leaves the caret, so `onUpdateSelection` can tell their echoes (including belated ones during a burst) from external caret moves. It replaced an edit counter that drifted in editors that never report selection changes (Termux, some web views).
- Opts out of fullscreen extract mode (`onEvaluateFullscreenMode() = false`); the Compose UI has no extract view, so landscape renders the keyboard itself.
- Inline suggestions are advertised in `method.xml` (`supportsInlineSuggestions`, API 30+) and implemented via `onCreateInlineSuggestionsRequest` / `onInlineSuggestionsResponse`. This is what drives the Gboard-style OTP paste chip. Keep the attribute and the two overrides in lockstep: removing either one disables autofill chips, and advertising the attribute without the overrides means chips never appear. The `InlinePresentationSpec` is built from the dp bounds in `data.InlineChipSpec` converted to px, and the `InlineSuggestionChip` composable (`SuggestionBar.kt`) must render within them — `InlineSuggestion.inflate()` rejects any size outside the spec, which is why the bounds live in a shared object rather than in the chip.

### B. Glide / Swipe Typing Engine (`input/swipe/`, `input/swipe/nn/`)
Two decode paths share the gesture; the neural path is preferred, the geometric path is the fallback (and always drives the live preview):

1. **Gesture Detection (`SwipeGestureDetector.kt`)**: Intercepts drag gestures across letter keys, distinguishing fast swipes from intentional key presses.
2. **Geometry Tracking (`SwipeKeyGeometry.kt`, `SwipeKeyMap.kt`)**: Tracks on-screen letter key centers and bounding boxes. Registered via `.trackLetterKey()` modifier.
3. **Neural preprocessing (`SwipeNeuralDecoder.kt`)**: Resamples the timestamped trace to **64 points uniform in *time*** (via a 60 Hz intermediate, mirroring `ml/swipe/preprocess.py`), normalized into the keyboard's unit box. Time spacing preserves the dwell/hesitation signal that separates "putt" from "put". Must mirror training exactly — the failure mode is silent (worse predictions, no crash).
4. **Neural inference (`SwipeNet.kt`)**: Hand-transcribed TCN forward pass over pre-allocated buffers; emits cosine coefficients + intention gate, read out against the layout basis (`basisFor`). Guarded end-to-end by the torch parity test (see §5.5).
5. **Beam search (`SwipeBeam.kt`)**: Trie-constrained CTC search (width 50). `bumpScore` pushes a reused word's new frequency into the live beam O(1); full trie rebuilds (`updateBeam`) happen only when the lexicon gains a word (~70 MB, debounced, synchronized).
6. **Geometric fallback (`SwipeDecoder.kt`)**: Corridor walk + pivot coverage + shape silhouette + lexicon prior, strict pass then relaxed pass. Covered by `SwipeDecoderTest` on plain JUnit.
7. **GPU Trail Rendering (`SwipeTrail.kt`)**: Operates strictly inside Compose's draw phase (`drawWithContent`) without triggering recompositions or relayouts.

### C. Voice Input (`input/voice/`)
- **Model**: Whisper Tiny English (~77 MB), kept out of the APK and fetched on first use.
- **Integrity**: `WhisperModelStore` pins an immutable Hugging Face commit (never `resolve/main`) and verifies SHA-256 before replacing any file; a `.sha256` sidecar avoids re-hashing on every open. To update the model, pin a new commit *and* refresh `EXPECTED_SHA256` / `EXPECTED_BYTES` together.
- **UX**: callers must surface the ~77 MB size, warn on metered connections (`isActiveNetworkMetered`), and offer cancel (`cancelDownload`) — see `VoiceInputView`.
- **ABI gate**: `WhisperAbi` — the AAR ships arm64-v8a only; never touch `WhisperCppEngine` on other ABIs. This is also why the app ships a single universal APK instead of ABI splits.

### D. Data Persistence & State Management
- **`KeyboardPreferences.kt`**: Thread-safe `SharedPreferences` wrapper exposing reactive `StateFlow<KeyboardSettings>`. Supports themes, custom HSV palettes, haptics, audio, number row, and scale multiplier.
- **`ClipboardHistoryManager.kt`**: Captures clipboard copies across apps, storing up to 50 items in `clipboard_history.json` with item pinning. Excluded from cloud/device-transfer backup (may contain secrets).
- **`SwipeDictionary.kt`**: Memory-efficient bucketed word storage indexed by starting character. Dynamically learns new typed/selected words and saves to `swipe_user_words.txt`. Learning is gated on password fields and `IME_FLAG_NO_PERSONALIZED_LEARNING`. `learn()` returns the new score for in-lexicon words so callers can `bumpScore` immediately — never discard the return value (see `ComposeInputMethodService.learnWord`).

### E. Theming & UI Hierarchy
- **Preset Themes**: Material Dark/Light, Pitch Black AMOLED, Dynamic Material You (Dark/Light), Nordic Frost, Sunset Glow, Cyber Neon.
- **Custom Theme Creator**: HSV spectrum slider, hex input, saturation/brightness canvas, and live interactive preview.
- **Header & Suggestion Strip**: The `SuggestionBar` dynamically replaces `KeyboardHeader` during active swipe or when suggestions are available, preventing keyboard height jumps.
- **Companion App**: Bottom-nav host in `ui/app` with Home (setup + typing playground), Themes, Clipboard, and Settings. App chrome uses `CompanionTheme` so it does not inherit the keyboard palette. Keep the two-level hierarchy: tabs show short grouped lists (`SettingsGroup` + `SettingsSwitchItem` / `SettingsNavItem`), and anything with more than one control (keyboard size, voice setup, custom theme) is a `CompanionPage` detail screen that hides the bottom bar and handles back. Don't inline sliders or multi-step setup into a tab.
- **Localization**: All user-visible text lives in `res/values/strings.xml` (Spanish in `values-es/`); UI code uses `stringResource()` / `pluralStringResource()` (Compose) or `getString()` (services/controllers). Never hardcode UI text. Locales are declared in `res/xml/locales_config.xml` (referenced from the manifest), and `supportsRtl="true"` is meaningful because layouts are resource-driven.

### F. Field Types & IME Actions
- **`FieldInputKind`**: Maps every `InputType.TYPE_MASK_CLASS` (text, number, phone, datetime) and the text/number variations Android defines. Email and URL get dedicated QWERTY bottom rows (`@` / `.com`, `/` / `.`); phone gets a dialer (`*`, `#`, `+`); datetime gets `/` and `:`; password and PIN disable swipe and suggestions. Terminals (`TYPE_NULL` class, e.g. Termux) map to `TERMINAL`: no suggestions, no swipe, no learning, and deletes are sent as `KEYCODE_DEL` because the editor exposes no text.
- **ASCII-capable subtype**: `res/xml/method.xml` sets `android:isAsciiCapable="true"` so the system can offer this keyboard on ASCII, email, URI, and password fields. Language subtypes are still English (US) only.
- **Enter key**: `EditorInfo.IME_MASK_ACTION` is forwarded to the Enter key (search, send, done, go, next, previous). The setup playground has chips for field types and those actions.

---

## 4. Build & Development Workflows

### Gradle Commands

```bash
# Run JVM unit tests (JUnit + Robolectric)
./gradlew testDebugUnitTest

# ML pipeline tests (CPU-only, no dataset/GPU)
cd ml && python -m pytest tests/

# Build debug APK
./gradlew assembleDebug

# Build signed release APK (needs keystore.properties; CI mints a throwaway one)
./gradlew assembleRelease

# On-device Compose UI tests (needs an emulator; see CI `instrumented` job)
./gradlew connectedCheck

# Clean build artifacts
./gradlew clean

# Run Android lint
./gradlew lintDebug
```

### ADB Testing & Deployment

```bash
# Install debug APK to connected device/emulator
adb install -r app/build/outputs/apk/debug/composekeyboard-debug.apk

# Launch Setup Wizard / Companion App
adb shell am start -n io.github.xxparthparekhxx.composekeyboard/.MainActivity

# Open Android Input Method Settings
adb shell am start -a android.settings.INPUT_METHOD_SETTINGS

# Enable keyboard via ADB (requires adb root or developer settings)
adb shell ime enable io.github.xxparthparekhxx.composekeyboard/.service.ComposeInputMethodService

# Select as active keyboard
adb shell ime set io.github.xxparthparekhxx.composekeyboard/.service.ComposeInputMethodService

# View keyboard logs
adb logcat -s ComposeKeyboard:V AndroidRuntime:E
```

### Dictionary Generation Tool

```bash
# Rebuild swipe lexicon from source word frequencies
cd tools
python3 build_dict.py
# Output generated at tools/out/swipe_words.txt
```

### ML Pipeline

```bash
cd ml
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt   # CPU torch note inside for GPU users
python tools_download.py          # FUTO dataset (~2 GB) — training only
python -m swipe.preprocess
python -m swipe.train --epochs 120 --batch 1024 --lr 1e-3
./run_after_training.sh --procs "$(nproc)"   # tune + eval (pass --pid to wait)
python tools/export_weights.py --ckpt runs/encoder/best.pt \
  --out ../app/src/main/assets/swipe_encoder.bin
# ^ also refreshes app/src/test/resources/swipe_reference.bin — commit it too
```

---

## 5. Guidelines & Conventions for AI Agents

### 1. Performance & Zero-Recomposition Rules
- **Never trigger per-frame recomposition during touch movements**: The swipe trail uses `trailVersion` read only during the Compose draw phase (`drawWithContent`). Do not move trail state into standard `@Composable` parameters.
- **Offload Heavy Computation**: Keep `SwipeDecoder.decode()`, `SwipeNeuralDecoder.decode()`, and dictionary I/O on `Dispatchers.Default` and `Dispatchers.IO`. UI interactions and `InputConnection` operations must remain on `Dispatchers.Main.immediate`.
- **Minimize Garbage Collection in Hot Loops**: `SwipeDecoder` reuses pre-allocated `FloatArray` buffers (`idealX`, `idealY`) and utilizes a fixed-size `TopWords` array rather than allocating lists on every candidate word. `SwipeNet`/`SwipeBeam` allocate nothing during a gesture.

### 2. InputConnection & Editing Conventions
- Always wrap compound edits (e.g. deleting a word and inserting replacement) in `beginBatchEdit()` and `endBatchEdit()`.
- Report every internal edit to `selection` (`onCommitText(len)` / `onDeleteBefore(len)`), and send key events through `sendKeyEdit()` so the tracker knows their outcome is unpredictable. An unreported edit makes its echo look like a user caret move and clears candidate state.
- Maintain single-word backspace logic: pressing backspace immediately following a swipe removes the whole committed word and preceding auto-space.
- Route all dictionary learning through `learnWord()` so the beam gets its immediate `bumpScore`; never call `swipeDictionary.learn()` and drop the return value.

### 3. Layout & Geometry Updates
- If adding keys to alphanumeric layouts (`KeyboardLayouts.qwertyRow*`), ensure letter keys have `.trackLetterKey(key, geometry)` attached.
- When adding new special keys, extend `KeyType` sealed class and handle both click dispatching in `KeyboardScreen.kt` and rendering in `KeyboardKey.kt`.
- New field layouts go through `FieldInputKind` plus `KeyboardLayouts.qwertyBottomRowFor` / `numpadRowsFor`. Do not branch on raw `InputType` in the UI.

### 4. Code Style & Documentation
- Preserve existing comments and docstrings.
- Adhere to Kotlin standard style and Material 3 design guidelines (see `.editorconfig`).
- Use explicit type annotations on public API boundaries.
- Keep components modular and localized to their respective subpackages (`input/swipe`, `input/swipe/nn`, `input/voice`, `ui/keyboard`, `ui/app`, `data`, `theme`).
- User-visible text goes in `strings.xml` (+ `values-es/`); use `stringResource` / `getString`. Never hardcode UI copy.

### 5. Neural Parity Discipline
- `SwipeNet.kt` is a line-by-line transcription of `ml/swipe/model.py` (BatchNorm folded at export). Any change to either side must keep `SwipeNetParityTest` green — it asserts `forward()` against torch reference vectors within tolerance.
- Re-exporting weights (`ml/tools/export_weights.py`) refreshes both `swipe_encoder.bin` and the checked-in `swipe_reference.bin` fixture. Commit both together.
- `SwipeNet.load` validates every tensor's presence and shape so a bad file fails at load (geometric fallback) instead of mid-gesture.
