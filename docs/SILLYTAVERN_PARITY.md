# SillyTavern 功能对齐：差距分析与分阶段计划

调研日期：2026-09-17。调研对象：本地 SillyTavern 1.18.0（`D:\SillyTavern`）。

## 前提与边界

SillyTavern 是 AGPL-3.0，JavaTavern 是 MIT。四份细节文档（`docs/parity/*.md`）只记录**行为、数据格式与协议语义**——接口地址、字段名、算法步骤、枚举取值——不包含任何从 SillyTavern 代码复制的实现。JavaTavern 要做同样的事，代码要独立编写。这跟 `docs/CORE_FEATURE_SCOPE.md` 里"防止抄袭的约束"是同一套原则的延伸：兼容公开数据格式和协议是移植性，不是照抄。

## JavaTavern 当前真实状态（已核实，非 README 自述）

- **接口**：唯一支持 OpenAI 兼容 `/chat/completions`，`Bearer` 鉴权，采样参数只有 5 个（temperature/top_p/max_tokens/frequency_penalty/presence_penalty）。
- **推理内容**：流式解析只读 `delta.content`，DeepSeek R1 等模型的 `reasoning_content` 被直接丢弃。
- **角色卡**：导入时把 description/personality/scenario/system_prompt **有损拼接**成一整段中文 system prompt 存储，原字段结构不保留——这是做导出、多开场白、分段 prompt 组装的最大障碍。
- **世界书**：位置只分"角色定义前/后"两档；关键词纯包含匹配；预算固定 4000 字符（非 token、非百分比）；没有次要关键词/逻辑组合/扫描深度/整词匹配/sticky/cooldown/互斥组。
- **完全没有**：宏（`{{char}}`/`{{user}}`）、用户 Persona、Author's Note、示例对话、备用开场白、Prompt 管理器、停止词、续写/代写、一角色多聊天、聊天导入导出、连接配置档案、正则脚本、快捷回复、STscript、工具调用、向量检索、TTS/生图/翻译等扩展。

## 四份细节文档

| 文档 | 覆盖范围 |
|---|---|
| [parity/api-connections.md](parity/api-connections.md) | 27 个 Chat Completion 来源 + 15 个 Text Completion 后端的接口、鉴权、参数、推理提取、工具调用、Prompt 缓存 |
| [parity/prompt-engine.md](parity/prompt-engine.md) | 生成类型、Prompt Manager 槽位结构、宏系统、Author's Note、Persona、Token 预算算法、推理模板 |
| [parity/characters-lore-chats.md](parity/characters-lore-chats.md) | 角色卡格式（V2/V3/CharX/BYAF）、世界书激活算法全字段、聊天 JSONL 格式、分支/检查点、群聊策略、Persona 数据模型 |
| [parity/extensions-app.md](parity/extensions-app.md) | 20+ 内置扩展逐项评估、STscript、应用层功能（主题/多语言/备份），附"移动用户最想念功能"排名 |

## 分阶段计划

优先级依据：四份文档各自给出的 S/M/L 复杂度、移动端可行性分级，加extensions-app.md 的"最想念功能"排名交叉排序。原则是**先打地基，再堆功能**——角色卡分段存储、一角色多聊天、Persona 实体、Prompt 管理器的深度注入原语，这四项被多份文档反复提到是后续功能的前置依赖，即使它们本身不是用户最想要的功能，也要最先做。

### Phase 0：地基重构（不直接面向用户，但后续一切依赖它）
1. 角色卡解析从"拼接存储"改为"分段字段存储"（description/personality/scenario/system_prompt/post_history_instructions/alternate_greetings/creator_notes/character_version 独立列）。
2. 数据模型从"一角色一聊天"改为"一角色多聊天"（chats 表 + character_id 外键）。
3. Prompt 组装重构为"槽位列表 + 深度注入原语"的 Prompt 管理器雏形（先内部重构，不急着暴露用户可配置顺序）。
4. Persona 实体（personas 表 + 默认/聊天锁/角色锁三种解析优先级）。

### Phase 1：接口与兼容性刚需
5. 扩展 Chat Completion 来源到 8-10 个高频（OpenAI/Claude/DeepSeek/OpenRouter/Google AI Studio/Groq/Mistral/xAI），Claude 和 Google 需要专门请求/响应转换器。
6. 接住 `reasoning_content`/`thinking` 字段，存进消息 `extra.reasoning`。
7. 核心宏注册表（15-20 个高频宏：user/char/persona/description/time/random/variables/if）。
8. 世界书字段补全：selectiveLogic、priority、scanDepth/caseSensitive/matchWholeWords、@深度真正生效、预算改百分比。
9. 消息 `is_system` 隐藏标记 + `display_text`/`mes` 分离。
10. 备用开场白 + 示例对话解析与使用。

### Phase 2：生成控制与自动化
11. 停止词、trim_sentences/collapse_newlines、Start Reply With 前缀续写、续写(continue)/代写(impersonate) 生成类型。
12. Author's Note 三层合并（依赖 Phase 0 的深度注入原语）。
13. 采样参数扩展到 SillyTavern 同款集合（min_p/top_k/停止词等）。
14. 正则脚本（全局+角色卡自带+预设三个作用域，角色卡自带的需要用户显式允许才生效）。
15. 连接配置档案（多套连接配置 + 一键切换 + 密钥多把存储）。

### Phase 3：内容与协作
16. 世界书互斥组（group/groupWeight/groupOverride）+ sticky/cooldown/delay 计时效果。
17. 分支与检查点（依赖 Phase 0 的一角色多聊天）。
18. 聊天/角色卡导入导出（覆盖 ST 原生 JSON 格式）。
19. 群聊 NATURAL/POOLED 激活策略 + APPEND 生成模式。
20. 工具调用（复用现有 `LocalAgentRouter`/`ChatAgentController` 的确认执行机制）。

### Phase 4：扩展功能（按用户反馈择优）
21. 总结/长期记忆自动化（跟现有"用户确认长期记忆"整合）。
22. 快捷回复（先做手动点击版，自动触发和世界书联动后置）。
23. 图片生成（1-2 个云端 provider：如 DALL-E、Pollinations）。
24. TTS（Android 系统 TTS + 1-2 个云端 provider）。
25. 向量检索精简版（聊天记录 RAG，单一云端 embedding provider，暴力余弦相似度）。
26. 数据银行/文件附件（本地文件上传 + 文本提取）。
27. 表情/立绘（LLM 分类模式）。
28. 聊天翻译（1-2 个 provider）。

### 明确不做（见 extensions-app.md §5 的详细理由）
- 多用户账号（移动端不适用）
- 第三方 JS 扩展安装机制（成本远超收益，如需扩展性走原生插件）
- 本地 WASM 模型（设备端 ML 推理是完全不同量级的工程投入）
- NovelAI/AI Horde/KoboldAI Classic（用户基数小）
- 自建 Text Completion 后端（Ooba/llama.cpp/KoboldCpp 等，局域网场景，放到 Phase 4 之后按需评估）
- CharX/BYAF 容器格式、URL 角色卡导入源（Chub/JanitorAI 等）——边际收益递减，按需再加

## 与现有路线图文档的关系

本计划是 `docs/FULL_FEATURE_ROADMAP.md` 和 `docs/CORE_FEATURE_SCOPE.md` 的**具体化**——那两份文档定义"要不要做"和产品取舍边界，本计划回答"具体做什么、按什么顺序、参照什么行为标准"。两份文档中标记为未完成的项目（世界书条件组、消息分支、通用 Agent 工具注册表等）在本计划里都能找到对应的、更细粒度的落地步骤。
