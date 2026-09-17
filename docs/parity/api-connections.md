# API / 模型连接层 —— SillyTavern 行为清单

调研对象：本地 SillyTavern 1.18.0（`D:\SillyTavern`）。本文档只记录**行为与协议语义**（接口地址、鉴权方式、参数名、数据形状），不包含从 AGPL-3.0 代码复制的任何实现——JavaTavern（MIT）要做同样的事需要独立实现。

JavaTavern 现状（已核实）：只有一种接口——OpenAI 兼容 `/chat/completions`，`Bearer` 鉴权，`OpenAiCompatibleClient.java`。采样参数只发 5 个（temperature/top_p/max_tokens/frequency_penalty/presence_penalty）。流式解析（`SseEventParser.java`）只读 `delta.content`，`reasoning_content` 等推理字段被丢弃。`ProviderCatalog.java` 只有 4 个预设：OpenAI、DeepSeek、OpenRouter、自定义。

---

## 1. 顶层 API 类型

ST 的 `main_api` 有 5 种：`textgenerationwebui`（Text Completion，15 个后端）、`openai`（Chat Completion，27 个来源）、`novel`（NovelAI）、`koboldhorde`（AI Horde）、`kobold`（KoboldAI Classic 旧版 `/v1/generate`）。

JavaTavern 优先级：只做 **Chat Completion**（27 个来源里挑高价值的一批）。Text Completion 类后端假设用户有自建/局域网服务器，移动端价值低但并非不可做——放到后续阶段，且要求用户自己填 局域网/自建服务地址。NovelAI/AI Horde/Kobold Classic 是小众，暂不做。

## 2. Chat Completion 来源（`CHAT_COMPLETION_SOURCES`，`src/constants.js`）

27 个值：`openai, claude, openrouter, ai21, makersuite, vertexai, mistralai, custom, cohere, perplexity, groq, chutes, electronhub, nanogpt, deepseek, aimlapi, xai, pollinations, moonshot, fireworks, cometapi(已禁用), azure_openai, zai, siliconflow, minimax, workers_ai`。

**按接线方式分组**：

| 组 | 来源 | 说明 |
|---|---|---|
| 纯 OpenAI-wire，直接复用现有客户端 | OpenAI, Custom, Perplexity, Groq, Fireworks, NanoGPT, Pollinations, Moonshot, Z.AI, SiliconFlow, Workers AI, AI/ML API, ElectronHub, Chutes, xAI, DeepSeek, Azure OpenAI, OpenRouter, MiniMax | 只是 base URL + 鉴权头不同 |
| 需要专门的请求/响应转换器 | **Claude**（Messages API，`x-api-key`+`anthropic-version`，`system` 是数组，`content` 是 block 数组）、**Google MakerSuite/Vertex**（`contents`/`parts`/`generationConfig`，`candidates` 响应，`?key=` 查询参数或 OAuth2）、**Cohere**（`chatHistory`，`k`/`p` 代替 top_k/top_p）、**AI21**（把多条前导 system 压成一条）、**Mistral**（tool-call id 需要哈希成 9 位） | |

**Base URL 全表**（节选高优先级）：OpenAI `api.openai.com/v1`、Claude `api.anthropic.com/v1`、DeepSeek `api.deepseek.com/beta`、OpenRouter `openrouter.ai/api/v1`、Groq `api.groq.com/openai/v1`、Google AI Studio `generativelanguage.googleapis.com`、Mistral `api.mistral.ai/v1`、xAI `api.x.ai/v1`、Moonshot `api.moonshot.ai/v1`、SiliconFlow 国际版/国内版两个域名、MiniMax 国际版/国内版两个域名。完整 27 个见调研原始记录。

**鉴权**：默认 `Authorization: Bearer <key>`；Claude 用 `x-api-key` 头；Azure 用 `api-key` 头；Google 系用 `?key=` 查询参数（或 Vertex 完整模式的服务账号 JSON→JWT→OAuth2）。

**模型列表**：多数来源 `GET <base>/models`；Google AI Studio `GET /v1beta/models?key=` 并过滤 `supportedGenerationMethods`；Azure 要两步探测（先列 deployment 再拿一次真实 5-token 补全确认底层模型）。

**多模态**：`image_url` data URL 是通用格式；Claude/Google 需要转换成各自的 block 格式。是否支持视觉靠模型名子串匹配或 `model_list` 元数据里的 `capabilities.vision`/`input_modalities` 字段判断，没有统一标准字段。

**推理/思考内容提取**（`extractReasoningFromData`，按来源分支）：
- DeepSeek / xAI → `message.reasoning_content`
- OpenRouter → `message.reasoning ?? message.reasoning_content`
- Claude → `content[].type==='thinking'` 拼接
- Google → `parts[].thought===true` 拼接
- Mistral → `content[0].thinking[].text`
- 流式场景类似，字段名相同，只是嵌在 `delta` 里

这是 JavaTavern 目前**完全没做**的部分，直接导致接 DeepSeek R1/Claude extended thinking 时思考过程会混进正文或直接丢失。

**工具调用**：OpenAI 式 `tool_calls[]` 是主流形状；Claude 是 `content_block` 里 `type:'tool_use'`；Google 是 `functionCall` part。

**Prompt 缓存**：仅 Claude（`cache_control:{type:'ephemeral'}`，插在 system prompt / 工具定义 / 指定深度消息上）和通过 OpenRouter 转发的 Claude/部分 Gemini 模型。

## 3. Text Completion 后端（15 种，`TEXTGEN_TYPES`）

`ooba, mancer, vllm, aphrodite, tabby, koboldcpp, togetherai, llamacpp, ollama, infermaticai, dreamgen, openrouter, featherless, huggingface, generic`。

**采样参数命名差异**（这是这类后端最麻烦的部分——同一个采样器概念，不同后端字段名不同，SillyTavern 用一个大参数对象再按后端过滤/改名）：

| 概念 | 常见字段名（可能双发） |
|---|---|
| 温度 | `temperature`（内部叫 `temp`） |
| top_p / top_k | 直接透传，HuggingFace 会把 top_p 限制在 [0, 0.999] |
| min_p / typical_p / tfs / top_a | `typical_p` 同时发 `typical`（llama.cpp 命名） |
| 重复惩罚 | `rep_pen`/`repetition_penalty` 双发，`repeat_penalty`（llama.cpp）、`rep_pen_range`/`repetition_penalty_range`、`rep_pen_slope`（仅 KoboldCpp） |
| DRY | `dry_multiplier`/`dry_base`/`dry_allowed_length`/`dry_penalty_last_n`/`dry_sequence_breakers` |
| XTC | `xtc_threshold`/`xtc_probability` |
| mirostat | `mirostat_mode`/`mirostat` 双发，`mirostat_tau`/`mirostat_eta` |
| 动态温度 | `dynatemp_low/high/range/exponent`，部分后端叫 `dynatemp_min/max` |
| 封禁 token/字符串 | `custom_token_bans`（逗号串或整数数组，视后端而定）、`banned_strings` |
| logit bias | 多数是 `{token_id: bias}` 对象；llama.cpp/Ollama 要 `[[id,bias],...]` 数组 |
| grammar | `grammar_string`（Ooba/Aphrodite/Tabby）或 `grammar`（KoboldCpp） |
| stop | `stopping_strings` 和 `stop` 双发 |
| 输出长度 | `n_predict`/`num_predict`/`max_new_tokens`/`max_tokens` **四个一起发**，后端各取所需，无害冗余 |

这套"一个参数发好几个同义字段名，后端各取所需"的容错模式值得直接照抄——比为每个后端精确适配更省事且更抗后端差异。

**生成端点**：多数 `/v1/completions`；llama.cpp 是原生 `/completion`；Ollama 是原生 `/api/generate`（JSON-lines 伪流式）。

**移动端相关性**：自建后端（Ooba/KoboldCpp/llama.cpp/TabbyAPI/vLLM/Ollama/HuggingFace TGI）需要手机和电脑在同一局域网或 VPN mesh（如 Tailscale）——这类支持的价值是"让手机变成自建模型的客户端"，不是"直接调用某个新云服务"。云托管型的 Mancer/TogetherAI/InfermaticAI/DreamGen/Featherless 则和 Chat Completion 来源一样，纯 HTTPS + 用户自己的 key，直接可做。

## 4. 连接配置档案（Connection Profiles）

`extensions/connection-manager/`。一个"档案"打包：API 类型、预设、反代 URL、模型、代理配置、停止词、Start Reply With、推理模板、Prompt 后处理方式、`secret-id`（指向密钥库里具体哪一把 key，不是明文存 key）。**关键设计**：档案本身不存密钥，只存指针——切换档案不用重新填 key。JavaTavern 目前是单一全局连接设置，没有"多套配置快速切换"的概念，这是 SillyTavern 用户高频依赖的功能（"GPT-4 写作用一套，本地模型测试用另一套"）。

## 5. 密钥管理

ST 用一个中心化的 `SecretManager`，支持**同一个 key 类型存多把、按 label 命名、标记哪把是 active**，`secrets.json` 存 `{id, value, label, active}` 数组。JavaTavern 现在是 `SecureModelSettingsStore`（Android Keystore 加密）存单一全局连接——这个安全存储机制本身已经比 ST 的"服务端 JSON 文件"更好（移动端天然优势），缺的是"一个 key 类型下存多把、连接档案指针引用"这层结构。

## 6. 移动端可直接调用 vs 需要额外基础设施

**可直接调用**（用户自己的 key，纯 HTTPS，JavaTavern 现有 OpenAI-wire 客户端稍加改造即可覆盖大半）：OpenAI、Claude、Mistral、Cohere、Groq、DeepSeek、xAI、OpenRouter、Google AI Studio、Azure OpenAI（需要 base URL + deployment name，仍是直连）、Moonshot、SiliconFlow、MiniMax 等。

**需要更多客户端逻辑但仍不需服务器**：Google Vertex 完整模式（服务账号 JSON → RS256 JWT 签名 → OAuth2 token，Java 自带 RSA 签名能力可实现，但比 Bearer token 复杂不少）。

**需要局域网/VPN**：所有自建 Text Completion 后端。

## 7. 建议的实施优先级（供路线图引用）

1. **P0**：重构 `OpenAiCompatibleClient`，把当前的 4 预设扩展到 8-10 个高频来源（OpenAI/Claude/DeepSeek/OpenRouter/Google AI Studio/Groq/Mistral/xAI），Claude 和 Google 需要专门的请求/响应转换器。
2. **P0**：接住 `reasoning_content`/`thinking` 字段，存进消息的 `extra.reasoning`，参照 ST 按来源分支提取。
3. **P1**：连接配置档案（多套连接配置 + 一键切换），复用即将做的多 key 存储。
4. **P1**：采样参数从 5 个扩展到 SillyTavern 同款集合（至少 min_p/top_k/频率惩罚系列 + 停止词），Text Completion 的多字段容错发送模式可以直接照抄这个思路用在未来的自建后端支持上。
5. **P2**：工具调用（tool_calls 统一解析层）。
6. **P2**：Prompt 缓存（仅 Claude，纯粹是省钱功能，非必需）。
7. **P3**：自建 Text Completion 后端（Ollama/llama.cpp/KoboldCpp，局域网场景）。
8. **P3 或不做**：NovelAI、AI Horde、KoboldAI Classic——用户基数小，投入产出比低。
