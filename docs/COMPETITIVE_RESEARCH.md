# Product Research: Mobile Tavern Clients

Research date: 2026-08-14. This document records product observations, not source-code reuse.

## Reviewed projects

- [MiniTavern Android](https://github.com/minitavern/MiniTavern_Android): low-friction mobile positioning, local storage, broad providers, and PNG/JSON import. The repository mainly tracks releases and does not provide a useful Android source baseline.
- [SillyTavern](https://github.com/SillyTavern/SillyTavern): the compatibility reference for character cards, lorebooks, prompts, personas, and conversation management; powerful but desktop-oriented.
- [ChatterUI](https://github.com/Vali-98/ChatterUI): mobile-friendly remote/local model modes, Character Card V2, multiple chats, sampler controls, and TTS.
- [LettuceAI](https://github.com/LettuceAI/app): privacy-first BYOK onboarding, custom characters, memory, broad provider support, and contributor documentation.
- [PocketTavern](https://github.com/Starkka15/PocketTavern): standalone Android flow, recent chats, multiple histories, alternative responses, message editing, backup, and direct model connections.
- [TavernRev](https://github.com/RedBaron1914/TavernRev): import/export compatibility, lorebooks, local memory, and visible beta/data-loss warnings.
- [LocalMind](https://github.com/tk85457/LocalMind): an adjacent native Android reference for on-device inference, encrypted Room storage, biometric locking, Markdown rendering, and local model lifecycle UX.
- [AiChat](https://github.com/dghiffjd7/AiChat) (AGPL-3.0, added 2026-09-14): Tauri v2 + Vue3, Android and Windows desktop, much larger scope than JavaTavern (creative-writing mode, image generation, moments/feed, sticker packs, a natural-language "maid" agent, memory tables, condition-graph world-book editor). See below — reviewed for product/reliability patterns only; different language and license, so no code was read line-by-line for reuse and none is eligible for reuse.

## What users consistently need

1. Start without a PC, server, account, or technical tutorial.
2. Import or create a character, connect a provider, and begin in three understandable steps.
3. Continue the last conversation instead of navigating an “AI control center”.
4. Edit, retry, branch, search, export, and recover without losing the story.
5. Know what stays local and what is sent to a model provider.
6. Keep advanced controls available without putting them in the primary flow.

## JavaTavern decisions

- Keep native Android Java/XML as the technical differentiator.
- Use ordinary product language and progressive disclosure.
- Put recent conversations and characters before provider configuration.
- Treat compatibility as data portability, not visual imitation.
- Keep Agent actions explicit, local, and auditable.
- Do not bundle third-party cards, prompts, templates, logos, or screenshots.
- Treat every borrowed preset or template as a license-review task; “available in another open-source app” does not automatically make redistribution safe.

## Lessons from AiChat

AiChat targets a much broader product surface than JavaTavern (desktop + mobile, image generation, moments/feed, a general-purpose natural-language agent). Chasing that scope would abandon JavaTavern's stated differentiator — a small, auditable, native-Android MVP — so most of AiChat's feature list is out of scope by design, not by oversight. Two reliability patterns are worth adopting regardless of feature scope, because they address gaps this review independently found in JavaTavern's own code:

- **Validate a reply before it touches storage.** AiChat's changelog describes rejecting a malformed AI response before it is persisted, surfacing the raw text and offering a patch-or-regenerate path instead of silently corrupting the chat log. JavaTavern's `SseEventParser` currently does the opposite for a bad chunk mid-stream: one malformed `data:` line raises a `JSONException` that aborts the entire in-progress reply (see `docs/CODE_REVIEW.md`). Treating stream/response validation as a first-class step — not an afterthought — is the actionable lesson, independent of AiChat's larger validation UI.
- **Prefer atomic writes for local storage.** AiChat moved its local store to atomic-write KV files specifically to survive a mid-write power loss. JavaTavern's own persistence layer (`TavernDatabase`, `ChatHistoryStore`, `BackupRepository`) already uses SQLite transactions for this, but currently ships with zero automated test coverage (see `docs/CODE_REVIEW.md`) — the lesson here is that write-safety claims need tests to back them, not just the mechanism.

Explicitly not adopted from AiChat, and why: a request/prompt inspector, a condition-graph world-book editor, and an autonomous "maid" agent are all real UX improvements, but each is a multi-week feature addition to a fundamentally different problem (discoverability and power-user tooling) than the reliability and test-coverage gaps this review prioritizes. They stay on the long-term roadmap (`docs/FULL_FEATURE_ROADMAP.md`), not this round.

## Deliberately deferred

- on-device GGUF inference;
- public character catalogs and accounts;
- social feeds and generated “companionship” notifications;
- unrestricted script/plugin execution;
- automatic memory writes without review.
