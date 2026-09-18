# Security Policy

## Supported versions

The latest `main` branch is supported. Older releases receive security fixes
only when a fix cannot be forward-ported cleanly.

## Reporting a vulnerability

Open a private security advisory on GitHub (preferred) or email
xx.parthparekh.xx@gmail.com with:

- what you found and which version/commit it affects,
- steps to reproduce or a proof of concept,
- anything you have already tried.

You will get an acknowledgement within 72 hours. Please do not open a public
issue for a vulnerability until a fix is available.

## Scope notes

- The Whisper model download is pinned to an immutable commit and verified
  against a bundled SHA-256 (`WhisperModelStore`). A hash mismatch deletes the
  file and reports failure rather than handing bytes to native code.
- Clipboard history may contain passwords and OTPs. It is excluded from cloud
  and device-transfer backup (`res/xml/data_extraction_rules.xml`,
  `res/xml/backup_rules.xml`) and never leaves the device.
- Dictionary learning is disabled for password fields and for editors that set
  `IME_FLAG_NO_PERSONALIZED_LEARNING`.
