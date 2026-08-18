# Contributing to JavaTavern

Thanks for helping make JavaTavern calmer, safer, and easier to use.

## Before opening a change

1. Search existing issues and discussions.
2. Keep a pull request focused on one user-visible problem.
3. Do not copy source code, prompts, artwork, presets, or product copy from other tavern clients.
4. Never include API keys, chat exports, private character cards, emulator databases, or user media.

## Local setup

Requirements:

- Android Studio with Android SDK 36.
- JDK 17.
- An Android 7.0+ device or emulator.

Run the verification suite:

```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

On Windows:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

## Pull requests

- Explain the user problem before the implementation.
- Add or update tests for parsers, routing, migrations, and prompt assembly.
- Include screenshots for UI changes.
- Preserve local-first defaults and explicit confirmation for destructive actions.
- Keep network, database, and image work off the main thread.
- Update `CHANGELOG.md` under `Unreleased` when behavior changes.

## Product language

Prefer ordinary language such as “继续聊天”, “连接模型”, and “需要确认”. Avoid exaggerated claims such as “智能大脑”, “全自动代理”, or “永不遗忘”.

## Reporting security issues

Do not open a public issue for a vulnerability involving secrets, data loss, or unsafe Agent execution. Follow `SECURITY.md` instead.
