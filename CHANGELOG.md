# Changelog

All notable changes to this project are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

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
