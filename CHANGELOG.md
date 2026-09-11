# Changelog

All notable changes are documented here. The project follows Semantic Versioning after `1.0.0`.

## Unreleased

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
