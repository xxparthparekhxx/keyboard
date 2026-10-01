# Changelog

All notable changes to this project are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Gboard-style OTP paste chip on API 30+: `method.xml` advertises
  `supportsInlineSuggestions`, and the service answers
  `onCreateInlineSuggestionsRequest` / `onInlineSuggestionsResponse` with an
  `InlinePresentationSpec` built from the chip's dp bounds. The chip inflates
  inside the spec's size range, with a defensive catch so a size mismatch
  drops the chip instead of crash-looping the IME.
- Playground "OTP" field type (numeric input with a `oneTimeCode` autofill
  hint) to exercise OTP detection and the inline chip.

### Fixed

- Neural encoder parity test: `swipe_reference.bin` fixture + `SwipeNetParityTest`
  guards the Kotlin forward pass against torch.
- `bumpScore` is now wired into every `SwipeDictionary.learn` call site.
- Single universal APK (ABI splits removed; duplicate `versionCode` rejected by Play).
- Whisper model download pinned to an immutable commit with SHA-256
  verification, metered-network warning, and cancel support.
- `updateBeam` synchronized against concurrent trie rebuilds.
- IME opts out of fullscreen extract mode.
- Added MIT `LICENSE` and security/contribution docs.

## [1.2.0] - 2026-09-18

- Neural swipe decoder with layout-agnostic spectral head.
- Voice input via on-device Whisper Tiny.
- Companion app, theming, clipboard history, emoji picker.

[Unreleased]: https://github.com/xxparthparekhxx/keyboard/compare/v1.2.0...HEAD
[1.2.0]: https://github.com/xxparthparekhxx/keyboard/releases/tag/v1.2.0
