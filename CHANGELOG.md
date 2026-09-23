# Changelog

All notable changes are documented here. The project follows Semantic Versioning after `1.0.0`.

## Unreleased

### Changed

- New visual style modeled on OmniTavern's classic light/dark themes: neutral cool-gray backgrounds, slate text, a `#199AFF` accent, and a GitHub-dark-style night palette in place of the Material default purple.
- Home lists characters as flat rows with hairline dividers and 48dp rounded-square avatars instead of floating cards; initial-letter avatars use a saturated palette.
- Chat bubbles use a light shadow instead of an outline, the back button is a vector chevron, and the search and message inputs are bordered pills.

### Fixed

- The create-character button no longer renders in Material's default teal; the theme now sets `colorSecondary` to the app accent.

## 0.4.1 - 2026-09-12

### Added

- Messages now render Markdown (bold, italics, inline code, fenced code blocks, quotes, lists) through a dependency-free parser, so roleplay `*action*` beats no longer display their asterisks.
- Regenerated replies are kept as swipable versions: every re-roll is appended to the message instead of overwriting it, and a `‹ 2 / 3 ›` control switches between them.
- Long conversations are trimmed by a configurable token budget (default 8000) instead of a fixed message count, so they stop failing once the model context is exceeded.
- Long-pressing the chat title lists which world-book entries fired this turn, which keyword matched, and whether recursion pulled them in.
- Full backup & restore as a shareable ZIP: characters, chats (including every re-roll version), world books, groups, presets, memories, drafts, and avatar/image files. Restore is an all-or-nothing transaction that rewrites file paths to the new device and prunes orphaned images; API keys are deliberately excluded because Keystore encryption is device-bound.

### Changed

- Release builds now use a real `signingConfig` fed from environment variables, with a documented fallback to the debug key so `assembleRelease` still works locally; CI builds and verifies the release APK instead of the debug one.
- Filled in `proguard-rules.pro` keep rules for inflated views, reflectively created ViewModels, and enums so R8 minification is safe (release APK is 3.8 MB versus 8.6 MB for debug).

### Fixed

- Re-rolling a reply that is not the newest message used to delete it and append the replacement at the bottom; it now regenerates in place.
- Group-chat streaming no longer loses output on rotation, because it runs through the same `ChatViewModel` lifecycle as private chat.
- Regenerating no longer destroys the previous reply, so a failed re-roll cannot leave a gap in the conversation.
- Message copies preserve speaker labels, reactions, and version info instead of silently dropping fields.

### Verification

- 23 JVM test classes, 166 tests, 0 failures.
- Backup/restore semantics verified by a scripted full round-trip (export → wipe → restore → re-roll) covering table fidelity, re-roll version history, FTS search, AUTOINCREMENT sequencing, and cross-device path rewriting.
- Android lint passes with 0 errors; debug and R8-minified release APKs both build.

## 0.4.0 - 2026-09-11

### Added

- Added native group creation, member selection, manual speaker routing, group prompts, and local group history.
- Added SillyTavern-compatible PNG `tEXt` and uncompressed `iTXt` character-card import with avatar extraction.
- Added in-app world-book editing for position, order, priority, depth, probability, and recursion fields.
- Added built-in and custom generation presets for common sampling parameters.
- Added a refreshed home, character, settings, chat, and system dark-theme experience.

### Changed

- Moved private-chat streaming ownership into `ChatViewModel` and isolated terminal persistence in `StreamSession`.
- Consolidated characters, messages, search, presets, groups, and Agent audit records into a versioned `TavernDatabase`.
- Moved group-chat database writes off the main thread and cancelled active requests when the screen closes.
- Updated public architecture, feature audit, roadmap, learning, and interview documents to match the implemented scope.

### Security

- Legacy database migration now uses savepoints, preserves source files on failure, and aborts instead of silently continuing with missing data.
- PNG metadata parsing rejects unsigned oversized and truncated chunks.
- Internal review prompts, verification artifacts, and local audit notes are excluded from Git.

### Verification

- 18 JVM test classes, 102 tests, 0 failures, and 0 errors.
- Android lint passes with 0 errors.
- Debug APK builds successfully.

## 0.2.0-preview - 2026-08-18

- Added reply quotes that are persisted and included in model context.
- Added emoji reactions with SQLite persistence.
- Added assistant-message regeneration for remote and offline reply engines.
- Added a per-character long-term memory manager; only user-confirmed entries are injected.
- Added `ChatRepository` as the storage boundary for character, message, Agent audit, and memory access.
- Added GitHub Actions verification and downloadable preview releases.
