# AGENTS.md — Agent & Developer Guide for Compose Keyboard

This document provides AI coding agents and developers with essential context, architectural principles, development workflows, and coding conventions for the **Compose Keyboard** project.

---

## 1. Project Overview

**Compose Keyboard** is a modern, high-performance Android Soft Keyboard (Input Method Editor / IME) built entirely with **Android Jetpack Compose**, **Kotlin 2.0**, and **Material 3**.

### Technical Specifications
- **Application ID / Namespace**: `com.example.composekeyboard`
- **Minimum SDK**: 24 (Android 7.0 Nougat)
- **Target / Compile SDK**: 34 (Android 14)
- **Kotlin Version**: 2.0.0 (with Kotlin Compose Compiler Plugin)
- **Android Gradle Plugin (AGP)**: 8.4.2
- **Gradle Version**: 8.7
- **JVM Target / Compatibility**: Java 17

---

## 2. Repository Structure

```
keyboard/
├── app/
│   ├── build.gradle.kts                             # Module build config & dependencies
│   ├── proguard-rules.pro                           # ProGuard / R8 rules
│   └── src/main/
│       ├── AndroidManifest.xml                      # Service declaration & permissions
│       ├── assets/
│       │   └── swipe_words.txt                      # ~35,000-word lexicon with log-frequency scores
│       ├── java/com/example/composekeyboard/
│       │   ├── MainActivity.kt                      # Setup Wizard, Test Sandbox & Companion App
│       │   ├── data/
│       │   │   ├── ClipboardHistoryManager.kt       # Persistent JSON-backed clipboard storage
│       │   │   ├── KeyboardData.kt                  # Key layouts, symbols, emoji matrices, KeyType
│       │   │   ├── KeyboardPreferences.kt           # SharedPreferences + StateFlow settings
│       │   │   └── SwipeDictionary.kt               # Bucketed word lexicon & user-learning engine
│       │   ├── input/swipe/
│       │   │   ├── SwipeController.kt               # Gesture state coordinator & decode dispatcher
│       │   │   ├── SwipeDecoder.kt                  # 4-signal spatial beam-search gesture decoder
│       │   │   ├── SwipeGestureDetector.kt          # PointerInputScope touch event recognizer
│       │   │   ├── SwipeKeyGeometry.kt              # Key bounding box tracker in window coords
│       │   │   ├── SwipeKeyMap.kt                   # Immutable snapshot of key center coordinates
│       │   │   └── SwipeTrace.kt                    # Polyline trace sampler & arc-length calculator
│       │   ├── service/
│       │   │   └── ComposeInputMethodService.kt     # InputMethodService + Compose lifecycle bridge
│       │   ├── theme/
│       │   │   ├── Color.kt                         # Theme color palette presets
│       │   │   └── Theme.kt                         # CompositionLocal theme provider
│       │   └── ui/
│       │       ├── keyboard/
│       │       │   ├── ClipboardView.kt             # In-keyboard clipboard history panel
│       │       │   ├── EmojiPicker.kt               # Categorized emoji grid panel
│       │       │   ├── KeyboardHeader.kt            # Action toolbar (Emoji, Clipboard, Themes, Settings)
│       │       │   ├── KeyboardKey.kt               # Single key composable with press feedback & popup
│       │       │   ├── KeyboardScreen.kt            # Root keyboard composable & layout orchestration
│       │       │   ├── QuickSettingsView.kt         # In-keyboard quick toggles sheet
│       │       │   ├── SuggestionBar.kt             # Candidate prediction strip & dynamic preview
│       │       │   ├── SwipeTrail.kt                # GPU-rendered gesture trail (draw phase)
│       │       │   └── ThemePicker.kt               # Visual theme picker sheet
│       │       └── theme/
│       │           ├── ColorPicker.kt               # HSV color picker dialog with hex/swatches
│       │           └── CustomThemeEditor.kt         # Live mini-keyboard preview custom theme editor
│       └── res/
│           ├── xml/method.xml                       # IME subtype & configuration
│           └── values/strings.xml                   # String resources
├── gradle/
│   └── libs.versions.toml                           # Gradle Version Catalog
├── tools/
│   └── build_dict.py                                # Lexicon preprocessor & contraction handler
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
- Uses `currentInputConnection` to perform batched text commits (`beginBatchEdit()`, `commitText()`, `deleteSurroundingText()`, `endBatchEdit()`).
- Tracks `selfEditsPending` to differentiate internal text updates from external user caret movements.

### B. Glide / Swipe Typing Engine (`input/swipe/`)
1. **Gesture Detection (`SwipeGestureDetector.kt`)**: Intercepts drag gestures across letter keys, distinguishing fast swipes from intentional key presses.
2. **Geometry Tracking (`SwipeKeyGeometry.kt`, `SwipeKeyMap.kt`)**: Tracks on-screen letter key centers and bounding boxes. Registered via `.trackLetterKey()` modifier.
3. **Trace Resampling (`SwipeTrace.kt`)**: Normalizes touch polyline to equal arc-length points (default: 32 points).
4. **Scoring Algorithm (`SwipeDecoder.kt`)**:
   - **Corridor Walk**: Gating filter ensuring points fall within a letter corridor.
   - **Pivot Coverage**: Identifies sharp corners (angles $\ge 62^\circ$) and requires corresponding key centers.
   - **Shape Silhouette**: Compares resampled trace against ideal polyline connecting key centers.
   - **Lexicon Prior**: Logarithmic word frequency + learned user word bonuses.
   - **Two-Pass Decoding**: Runs strict decode pass; falls back to relaxed tolerances on failure.
5. **GPU Trail Rendering (`SwipeTrail.kt`)**: Operates strictly inside Compose's draw phase (`drawWithContent`) without triggering recompositions or relayouts.

### C. Data Persistence & State Management
- **`KeyboardPreferences.kt`**: Thread-safe `SharedPreferences` wrapper exposing reactive `StateFlow<KeyboardSettings>`. Supports themes, custom HSV palettes, haptics, audio, number row, and scale multiplier.
- **`ClipboardHistoryManager.kt`**: Captures clipboard copies across apps, storing up to 50 items in `clipboard_history.json` with item pinning.
- **`SwipeDictionary.kt`**: Memory-efficient bucketed word storage indexed by starting character. Dynamically learns new typed/selected words and saves to `learned_words.txt`.

### D. Theming & UI Hierarchy
- **Preset Themes**: Material Dark/Light, Pitch Black AMOLED, Dynamic Material You (Dark/Light), Nordic Frost, Sunset Glow, Cyber Neon.
- **Custom Theme Creator**: HSV spectrum slider, hex input, saturation/brightness canvas, and live interactive preview.
- **Header & Suggestion Strip**: The `SuggestionBar` dynamically replaces `KeyboardHeader` during active swipe or when suggestions are available, preventing keyboard height jumps.

---

## 4. Build & Development Workflows

### Gradle Commands

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean build artifacts
./gradlew clean

# Run Android lint
./gradlew lintDebug
```

### ADB Testing & Deployment

```bash
# Install debug APK to connected device/emulator
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch Setup Wizard / Companion App
adb shell am start -n com.example.composekeyboard/.MainActivity

# Open Android Input Method Settings
adb shell am start -a android.settings.INPUT_METHOD_SETTINGS

# Enable keyboard via ADB (requires adb root or developer settings)
adb shell ime enable com.example.composekeyboard/.service.ComposeInputMethodService

# Select as active keyboard
adb shell ime set com.example.composekeyboard/.service.ComposeInputMethodService

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

---

## 5. Guidelines & Conventions for AI Agents

### 1. Performance & Zero-Recomposition Rules
- **Never trigger per-frame recomposition during touch movements**: The swipe trail uses `trailVersion` read only during the Compose draw phase (`drawWithContent`). Do not move trail state into standard `@Composable` parameters.
- **Offload Heavy Computation**: Keep `SwipeDecoder.decode()` and dictionary I/O on `Dispatchers.Default` and `Dispatchers.IO`. UI interactions and `InputConnection` operations must remain on `Dispatchers.Main.immediate`.
- **Minimize Garbage Collection in Hot Loops**: `SwipeDecoder` reuses pre-allocated `FloatArray` buffers (`idealX`, `idealY`) and utilizes a fixed-size `TopWords` array rather than allocating lists on every candidate word.

### 2. InputConnection & Editing Conventions
- Always wrap compound edits (e.g. deleting a word and inserting replacement) in `beginBatchEdit()` and `endBatchEdit()`.
- Increment `selfEditsPending` for internal edits to prevent `onUpdateSelection` from clearing candidate state unintentionally.
- Maintain single-word backspace logic: pressing backspace immediately following a swipe removes the whole committed word and preceding auto-space.

### 3. Layout & Geometry Updates
- If adding keys to alphanumeric layouts (`KeyboardLayouts.qwertyRow*`), ensure letter keys have `.trackLetterKey(key, geometry)` attached.
- When adding new special keys, extend `KeyType` sealed class and handle both click dispatching in `KeyboardScreen.kt` and rendering in `KeyboardKey.kt`.

### 4. Code Style & Documentation
- Preserve existing comments and docstrings.
- Adhere to Kotlin standard style and Material 3 design guidelines.
- Use explicit type annotations on public API boundaries.
- Keep components modular and localized to their respective subpackages (`input/swipe`, `ui/keyboard`, `data`, `theme`).
