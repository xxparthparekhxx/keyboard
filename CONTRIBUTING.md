# Contributing

## Quick start

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

See `AGENTS.md` for the architecture guide. The short version:

- Touch-path code must not recompose per frame; the swipe trail renders in the
  draw phase only.
- Heavy work (`SwipeDecoder.decode`, dictionary I/O, trie rebuilds) stays off
  the main thread; `InputConnection` edits stay on `Dispatchers.Main.immediate`.
- Compound edits go in `beginBatchEdit()` / `endBatchEdit()` and bump
  `selfEditsPending`.
- Run `./gradlew testDebugUnitTest` before pushing. If you re-export
  `swipe_encoder.bin` (`ml/tools/export_weights.py`), the parity fixture at
  `app/src/test/resources/swipe_reference.bin` refreshes automatically — commit
  it alongside the weights.

## Pull requests

- Small, focused diffs with a clear description.
- New behavior needs a test (`app/src/test`) or a reason it cannot be unit
  tested.
- Do not commit secrets, keystores, or `keystore.properties`
  (see `keystore.properties.example`).
