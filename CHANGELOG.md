# Changelog

All notable changes to this project are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Next-word prediction: between words the suggestion strip offers likely
  next words. A shipped table (`assets/next_words.txt`, 20k context words,
  ~1 MB) built by `ml/tools/build_next_words.py` from C4 (ODC-BY 1.0) makes it
  work out of the box. Pairs the user types, swipes or picks are learned
  on-device on top of it (`NextWordPredictor`, `next_words_user.txt`) and
  outrank the table, gated like dictionary learning (no passwords, terminals
  or incognito fields) and excluded from backups. A swiped word taken back
  with backspace or replaced from the strip unlearns its pair.
- Emoji search: Enter inserts the top result, which is highlighted. Before
  typing, the search panel offers popular searches and recent emojis.

### Changed

- Typing logic (shift/caps lock, double-space period, auto-caps, swipe
  alternatives, emoji search keys) moved out of the `KeyboardScreen`
  composable into `TypingState`, a plain state holder with unit tests.
- The service is now the single owner of the word being typed (`currentWord`)
  and re-reads it from the editor on field start, external caret moves and
  backspacing across a word boundary (`WordContext`). The UI no longer keeps
  its own copy, so completions and learning can no longer disagree.
- Dictionary-learning gate reuses `FieldInputKind.allowsLearning` instead of
  repeating the password checks.
- Emoji picker redesign: evenly spaced monochrome category tabs, a heading
  per category, press feedback on emojis, an empty-state hint for Recent,
  and a bottom bar styled like keyboard keys. The search field has a
  caret, a clear button and a back arrow, and results scroll sideways.
- Emoji search ranking: every query word must match the start of a word in
  the emoji's name ("face" finds 😀, "hea red" finds ❤️). Aliases and exact
  names rank first. Substring matches need 3+ characters.

### Fixed

- Possible native crash: freeing the Whisper model while a transcription was
  still running. Model use, load and release are now serialized
  (`GuardedModel`), and concurrent first uses no longer load it twice.
- Clipboard history could be overwritten with a single clip if a copy landed
  before the saved history finished loading.
- Typing could stall on the main thread behind a gesture decode or a full
  trie rebuild: `bumpScore` no longer shares the decode lock, and rebuilds
  build outside it and replay bumps made mid-build.
- Whole-word backspace after a swipe, and swapping a swiped word from the
  strip, now check that the word is still before the caret, so editors that
  never report selection changes cannot lose unrelated text.
- `SwipeNet.forward` no longer builds weight-name strings per gesture.

## [1.2.1] - 2026-10-01

### Added

- Gboard-style OTP paste chip on API 30+: `method.xml` advertises
  `supportsInlineSuggestions`, and the service answers
  `onCreateInlineSuggestionsRequest` / `onInlineSuggestionsResponse` with an
  `InlinePresentationSpec` built from the chip's dp bounds. The chip inflates
  inside the spec's size range, with a defensive catch so a size mismatch
  drops the chip instead of crash-looping the IME.
- Playground "OTP" field type (numeric input with a `oneTimeCode` autofill
  hint) to exercise OTP detection and the inline chip.
- Comprehensive unit tests covering IME Enter/action handling, swipe-commit editing flow,
  selection echo tracking, and backup rule exclusions.

### Fixed

- Sized inline OTP chip spec from shared dp bounds and removed style passthrough.
- Neural encoder parity test: `swipe_reference.bin` fixture + `SwipeNetParityTest`
  guards the Kotlin forward pass against torch.
- `bumpScore` is now wired into every `SwipeDictionary.learn` call site.
- Single universal APK (ABI splits removed; duplicate `versionCode` rejected by Play).
- Whisper model download pinned to an immutable commit with SHA-256
  verification, metered-network warning, and cancel support.
- `updateBeam` synchronized against concurrent trie rebuilds.
- IME opts out of fullscreen extract mode.
- ProGuard rules document whisper-jni Maven group vs Java package name.
- Added MIT `LICENSE` and security/contribution docs.

## [1.2.0] - 2026-09-18

- Neural swipe decoder with layout-agnostic spectral head.
- Voice input via on-device Whisper Tiny.
- Companion app, theming, clipboard history, emoji picker.

[Unreleased]: https://github.com/xxparthparekhxx/keyboard/compare/v1.2.1...HEAD
[1.2.1]: https://github.com/xxparthparekhxx/keyboard/compare/v1.2.0...v1.2.1
[1.2.0]: https://github.com/xxparthparekhxx/keyboard/releases/tag/v1.2.0
