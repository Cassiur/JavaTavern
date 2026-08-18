# Changelog

## 0.2.0-preview - 2026-08-18

- Added reply quotes that are persisted and included in model context.
- Added emoji reactions with SQLite persistence.
- Added assistant-message regeneration for remote and offline reply engines.
- Added a per-character long-term memory manager; only user-confirmed entries are injected.
- Added `ChatRepository` as the storage boundary for character, message, Agent audit, and memory access.
- Migrated the message database from v5 to v6 without deleting existing chats.
- Added interaction model tests and an audited AiChat feature matrix.

All notable changes are documented here. The project follows Semantic Versioning after `1.0.0`.

## Unreleased

### Added

- GitHub Actions verification, contribution guide, security policy, privacy notes, and issue templates.
- Product research and experience principles for a calmer mobile-first chat flow.
- OpenAI, DeepSeek, OpenRouter, and custom connection presets with a lightweight `/models` check.
- In-app character creation and editing.
- Per-character draft recovery and long-press message copy, edit, and delete actions.
- Streaming replies that preserve the reader's scroll position and allow drafting the next message.

## 0.3.0 - 2026-08-14

### Added

- SillyTavern V2 JSON character import and keyword world books.
- Photo Picker image messages with private compression and multimodal requests.
- Agent proposal, confirmation, transaction result, and audit flow for conversation clearing.
- Keyset message pagination, FTS search, result context jump, and image LRU cache.

### Security

- Android Keystore API-key encryption, HTTPS-only endpoints, disabled backup, and secure settings screen.
