# JavaTavern

**本地优先的 Android 角色聊天应用 —— 导入 SillyTavern 角色卡，连接任意 OpenAI 兼容模型，所有对话都只留在你自己的手机上。**

<p align="center">
  <img src="docs/images/home.png" alt="JavaTavern 首页" width="32%">
  <img src="docs/images/chat.png" alt="JavaTavern 对话" width="32%">
</p>

## 下载安装

从 [GitHub Releases](https://github.com/Cassiur/JavaTavern/releases) 下载最新的 Android 安装包（APK）。在 Android 7.0 及以上的手机上，用浏览器或文件管理器打开它，按系统提示允许安装即可。

当前版本使用开发签名。升级前请先备份本地数据；未来的稳定版会换成专门的发布签名。

> 1.0 之前的版本：如果打算跑长期剧情，请先做好备份。

## 已实现功能

- 原生单聊与多角色群聊，聊天记录存于本地 SQLite。
- 兼容 OpenAI 的 HTTPS 接口，支持 SSE 流式输出与随时中断。
- Android 相册选图发送，支持多模态 `image_url` 请求。
- 兼容 SillyTavern 的 JSON / PNG 角色卡导入，世界书条目可在应用内可视化编辑。
- 应用内直接创建和编辑角色，不需要先准备卡文件。
- 内置 OpenAI、DeepSeek、OpenRouter 与自定义服务预设，支持 `/models` 连接检查。
- 内置和自定义生成预设，覆盖 temperature、top-p、token 上限与惩罚项。
- 最近消息分页、全文搜索与上下文跳转。
- 按角色保存草稿，支持消息复制、编辑与删除。
- 引用回复、表情回应，以及助手消息重新生成。
- 用户确认制长期记忆，按角色隔离并限制注入长度。
- 本地指令卡，包括需确认并留有审计记录的会话清空。
- API Key 由 Android Keystore 加密，且已关闭 Android 备份。

## 快速开始

环境要求：

- Android Studio 与 Android SDK 36；
- JDK 17；
- Android 7.0 及以上。

```bash
git clone https://github.com/Cassiur/JavaTavern.git
cd JavaTavern
./gradlew assembleDebug
```

安装 `app/build/outputs/apk/debug/app-debug.apk`，打开「连接模型」，选择预设或自定义 HTTPS 的 OpenAI 兼容接口，填入模型 ID 与可选的 API Key，然后执行连接检查。不配置远程模型时，JavaTavern 会使用内置的离线演示回复引擎。

运行完整的本地校验：

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Windows PowerShell 用户可用 `./gradlew.bat` 执行同样的任务。

## 角色卡

在首页点击「导入角色卡」，选择 JSON 或 PNG 卡。仓库里提供了一个最小 JSON 示例：[`samples/character-card-v2.json`](samples/character-card-v2.json)。

当前兼容范围：

- V2 JSON 的 name、description、personality、scenario、first message 与 system prompt；
- PNG 的 `tEXt` / 未压缩 `iTXt` 元数据，支持常见的 `chara` 与 `ccv3` 关键字；
- 内嵌角色书条目，支持关键词、常驻、位置、优先级、深度、概率与递归设置；
- 基于来源哈希的重复文件检测。

备用开场白、示例对话、作者元数据、压缩 `iTXt` 与往返导出仍在计划中。

## 隐私模型

角色、对话、审计记录与压缩后的消息图片都存放在 Android 应用私有目录。配置远程模型后，当前角色提示词、命中的世界书条目、最近上下文和你选择的图片会直接发送给该服务商。

JavaTavern 目前不含统计、广告、账号系统，也没有项目方运营的后端。详见 [`docs/PRIVACY.md`](docs/PRIVACY.md)。

## 架构

```text
Activity + RecyclerView
  ├─ ChatViewModel / StreamSession / StreamAccumulator
  ├─ ChatRepository / TavernDatabase
  ├─ CharacterCardParser / PngCharacterCardReader
  ├─ WorldBookPromptBuilder / GroupPromptBuilder
  ├─ ChatAgentController / LocalAgentRouter
  └─ OpenAiCompatibleClient / SseEventParser
```

流式生命周期由 `ChatViewModel` 持有，各仓库共享同一个带版本的 SQLite 数据库。`ChatActivity` 仍承担较多 UI 编排；下一步的结构演进是引入带 `SavedStateHandle` 的独立屏幕状态模型，随后补充迁移相关的 instrumentation 测试并评估 Room。

## 路线图

- 每个角色多个会话、候选分支与持久化分叉；
- 带版本与冲突预览的导入导出；
- 更完整的角色卡字段与原生服务适配器；
- Macrobenchmark、万条消息测试、无障碍与 vivo 真机报告。

产品方向见 [`docs/PRODUCT_PRINCIPLES.md`](docs/PRODUCT_PRINCIPLES.md) 与 [`docs/COMPETITIVE_RESEARCH.md`](docs/COMPETITIVE_RESEARCH.md)。
与 AiChat 的逐项功能核对记录在 [`docs/AICHAT_FEATURE_AUDIT.md`](docs/AICHAT_FEATURE_AUDIT.md)。

## 参与贡献

提交 Pull Request 前请先阅读 [`CONTRIBUTING.md`](CONTRIBUTING.md)。安全问题请遵循 [`SECURITY.md`](SECURITY.md)。

## 兼容性与致谢

JavaTavern 参考了 SillyTavern、ChatterUI、LettuceAI 等项目的行为与开放数据格式。本项目不复制它们的源码、美术资源、内置角色、提示词或界面文案。

## 许可

JavaTavern 基于 [MIT License](LICENSE) 发布。

---

# JavaTavern (English)

**A local-first Android character chat app — import SillyTavern cards, connect any OpenAI-compatible model, and keep every conversation on your device.**

## Download

Download the latest Android APK from [GitHub Releases](https://github.com/Cassiur/JavaTavern/releases). Open the APK on an Android 7.0 or newer phone and allow installation from the browser or file manager when Android asks.

Current builds use a development signature. Back up local data before upgrading; a future stable channel will use a dedicated release signing key.

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
