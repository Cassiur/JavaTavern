# JavaTavern

**A local-first Android character chat app — import SillyTavern cards, connect any OpenAI-compatible model, and keep every conversation on your device.**

<p align="center">
  <img src="docs/images/home.png" alt="JavaTavern home" width="32%">
  <img src="docs/images/chat.png" alt="JavaTavern conversation" width="32%">
</p>

## Download

Download the latest Android APK from [GitHub Releases](https://github.com/Cassiur/JavaTavern/releases). Open the APK on an Android 7.0 or newer phone and allow installation from the browser or file manager when Android asks.

Preview builds currently use a development signature. Back up local data before upgrading; a future stable channel will use a dedicated release signing key.

> Pre-1.0 software: keep backups before relying on it for long-running stories.

## What works today

- Native one-to-one and multi-character group conversations with local SQLite history.
- OpenAI-compatible HTTPS endpoints with SSE streaming and stop support.
- Android Photo Picker image messages and multimodal `image_url` requests.
- SillyTavern-compatible JSON and PNG character import with editable world-book entries.
- In-app character creation and editing, without preparing a card file first.
- OpenAI, DeepSeek, OpenRouter, and custom provider presets with a `/models` connection check.
- Built-in and custom generation presets for temperature, top-p, token limits, and penalties.
- Recent-message keyset pagination, FTS search, and context jump.
- Per-character draft recovery plus message copy, edit, and delete actions.
- Reply quotes, emoji reactions, and assistant-message regeneration.
- User-confirmed per-character long-term memories with bounded prompt injection.
- Local command cards, including confirmed and audited conversation clearing.
- Android Keystore-backed API-key encryption and disabled Android backup.

## Quick start

Requirements:

- Android Studio and Android SDK 36;
- JDK 17;
- Android 7.0 or newer.

```bash
git clone https://github.com/Cassiur/JavaTavern.git
cd JavaTavern
./gradlew assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk`, open **Model connection**, choose a preset or custom HTTPS OpenAI-compatible endpoint, enter the model ID and optional API key, then run the connection check. Without remote settings, JavaTavern uses a small offline demonstration reply engine.

Run the full local verification suite:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows PowerShell users can run the same tasks with `./gradlew.bat`.

## Character cards

Use **Import character card** on the home screen and select a JSON or PNG card. A small JSON example is available at [`samples/character-card-v2.json`](samples/character-card-v2.json).

Current compatibility:

- V2 JSON name, description, personality, scenario, first message, and system prompt;
- PNG `tEXt` / uncompressed `iTXt` metadata using common `chara` and `ccv3` keywords;
- embedded character-book entries with keyword, constant, position, priority, depth, probability, and recursion settings;
- duplicate-file detection with a source hash.

Alternate greetings, example dialogue, creator metadata, compressed `iTXt`, and round-trip export remain planned.

## Privacy model

Characters, conversations, audit records, and compressed message images are stored in Android private app storage. When a remote model is configured, the active character prompt, matched world-book entries, recent context, and selected images are sent directly to that configured provider.

JavaTavern currently includes no analytics, ads, account system, or project-operated backend. Read [`docs/PRIVACY.md`](docs/PRIVACY.md) for details.

## Architecture

```text
Activity + RecyclerView
  ├─ ChatViewModel / StreamSession / StreamAccumulator
  ├─ ChatRepository / TavernDatabase
  ├─ CharacterCardParser / PngCharacterCardReader
  ├─ WorldBookPromptBuilder / GroupPromptBuilder
  ├─ ChatAgentController / LocalAgentRouter
  └─ OpenAiCompatibleClient / SseEventParser
```

The stream lifecycle is retained in `ChatViewModel`, while repositories share one versioned SQLite database. `ChatActivity` still owns substantial UI orchestration; the next structural milestone is a dedicated screen-state model with `SavedStateHandle`, followed by migration instrumentation tests and a Room evaluation.

## Roadmap

- multiple conversations per character, alternatives, and persistent branching;
- versioned export/import with conflict preview;
- broader character-card fields and native provider adapters;
- Macrobenchmark, 10,000-message tests, accessibility, and vivo device reports.

See [`docs/PRODUCT_PRINCIPLES.md`](docs/PRODUCT_PRINCIPLES.md) and [`docs/COMPETITIVE_RESEARCH.md`](docs/COMPETITIVE_RESEARCH.md) for product direction.
The verified feature-by-feature comparison with AiChat is documented in [`docs/AICHAT_FEATURE_AUDIT.md`](docs/AICHAT_FEATURE_AUDIT.md).

## Contributing

Read [`CONTRIBUTING.md`](CONTRIBUTING.md) before opening a pull request. Security-sensitive findings should follow [`SECURITY.md`](SECURITY.md).

## Compatibility and attribution

JavaTavern studies the product behavior and open data formats of projects such as SillyTavern, ChatterUI, LettuceAI, and other mobile tavern clients. It does not copy their source code, artwork, bundled characters, prompts, or interface text.

## License

JavaTavern is available under the [MIT License](LICENSE).
