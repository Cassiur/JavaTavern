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

## Deliberately deferred

- on-device GGUF inference;
- public character catalogs and accounts;
- social feeds and generated “companionship” notifications;
- unrestricted script/plugin execution;
- automatic memory writes without review.
