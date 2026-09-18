# Changelog

All notable changes to this project are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Fixed

- Neural encoder parity test: `swipe_reference.bin` fixture + `SwipeNetParityTest`
  guards the Kotlin forward pass against torch.
- `bumpScore` is now wired into every `SwipeDictionary.learn` call site.
- Single universal APK (ABI splits removed; duplicate `versionCode` rejected by Play).
- Whisper model download pinned to an immutable commit with SHA-256
  verification, metered-network warning, and cancel support.
- `updateBeam` synchronized against concurrent trie rebuilds.
- Removed unimplemented `supportsInlineSuggestions`; IME opts out of
  fullscreen extract mode.
- Added MIT `LICENSE` and security/contribution docs.

## [1.2.0] - 2026-09-18

- Neural swipe decoder with layout-agnostic spectral head.
- Voice input via on-device Whisper Tiny.
- Companion app, theming, clipboard history, emoji picker.
