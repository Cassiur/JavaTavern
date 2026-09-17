# Prompt 组装与生成控制 —— SillyTavern 行为清单

调研对象：本地 SillyTavern 1.18.0。行为/格式语义记录，不含 AGPL 代码复制。

JavaTavern 现状（已核实）：`WorldBookPromptBuilder` + `OpenAiCompatibleClient.buildSingleSystemPrompt()` 把角色描述/性格/场景/系统规则拼成一整段中文 system prompt，世界书按"角色定义前/后"两档插入，用户确认的长期记忆整段追加。没有宏、没有 Persona、没有 Author's Note、没有 Prompt 管理器、没有停止词、没有续写/代写、没有推理模板。

---

## 1. 生成类型

ST 用一个入口函数按 `type` 分派 6 种：`normal`（默认）、`swipe`（重新生成，重进前先弹出当前最后一条消息）、`regenerate`、`continue`（续写——把最后一条助手消息当前缀，模型接着写，完成后拼接而非新建）、`impersonate`（代写——生成结果写回输入框而不是发进聊天）、`quiet`（后台静默生成，扩展/总结用，不进聊天记录）。

JavaTavern 已有"重新生成"（对应 swipe），**缺**：continue（续写）、impersonate（代写）、quiet（后台静默调用，未来做摘要/总结时会需要这个入口）。

**Start Reply With / 前缀续写**：Text Completion 场景直接把 `角色名:` 拼在 prompt 末尾让模型接着写；Claude 场景用 `assistant_prefill` 字段。JavaTavern 目前完全没有"预填开头"能力。

**消息 Bias**：`{{bias "文本"}}` 从用户消息里提出来，作为 prompt 末尾的软性引导后缀，不是 token 级 logit bias。

## 2. Chat Completion 的 Prompt 结构（Prompt Manager）

**固定锚点顺序**（用户能拖拽调整的是"相对"位置的提示词，这些锚点顺序是写死的）：

```
世界书(before) → 主提示词(main) → 世界书(after) → 角色描述 → 性格 → 场景 → 用户Persona描述
  → [nsfw、jailbreak/越狱后指令、用户自定义顺序的相对提示词、补充定义、bias]
  → [按位置插入的扩展提示词：摘要/Author's Note/向量记忆]
  → 聊天历史 → 对话示例(或反转，若锁定示例)
  → [代写/静默生成控制提示词，永远最后]
```

角色卡自带的 `system_prompt`/`post_history_instructions` 会覆盖 `main`/`jailbreak` 两个槽位的**内容**，位置不变。

**绝对位置注入**（"@深度" 类）独立于上面这条线性列表，按 `injection_depth`（从聊天记录末尾数第几条）+ `injection_order`（同深度多个时的优先级）+ `role`（system/user/assistant）插入到消息数组的对应位置——这是一个通用机制，Author's Note、角色卡的"深度笔记"、Persona 在深度插入、世界书 @D 位置的条目，全部复用同一套"深度注入"原语。

**Token 预算与裁剪**：`预算 = 上下文窗口 - 回复长度`。锚点提示词是强制的，超预算直接报错提示（不静默截断）；聊天历史则是"预留其余槽位后，从最新往旧塞，塞不下就停"——**整条消息为单位丢弃**，不会把单条消息从中间截断。世界书预算是上下文窗口的一个百分比（默认 25%），单独预留，比聊天历史预算计算更早发生。

## 3. Text Completion 的 Prompt 结构

**Context Template（"故事字符串"）**：一个 Handlebars 风格的模板，变量包括 `description/personality/persona/scenario/system/char/user/wiBefore/wiAfter/mesExamples` 等，`{{#if x}}...{{/if}}` 控制空字段不占位。

**Instruct Template（指令模板）**：字段很多——`input_sequence`/`output_sequence`/`system_sequence`（各自的 wrap 标签）、`first_input_sequence`/`last_input_sequence`（首尾特殊覆盖）、`stop_sequence`、`wrap`（是否按换行分隔）、`names_behavior`（是否在每轮前缀角色名）、`activation_regex`（按模型名自动选中该模板）。这套东西本质是给不同模型的对话格式模板（ChatML、Alpaca、Vicuna 等）建了一个通用参数化结构。

## 4. 宏（Macros）

完整列表约 40+ 个，核心高价值的一批：

- **身份**：`{{user}}` `{{char}}` `{{group}}`（群成员逗号列表）
- **角色字段**：`{{description}}` `{{personality}}` `{{scenario}}` `{{persona}}`
- **时间**：`{{time}}` `{{date}}` `{{weekday}}` `{{isotime}}` `{{idle_duration}}`（距上条用户消息多久）
- **随机**：`{{random::a::b::c}}`（每次解析重新随机）、`{{roll::1d20}}`（骰子）、`{{pick::a::b::c}}`（同一位置稳定的伪随机——按聊天ID+内容哈希做种，同一句话反复渲染结果不变，这个"确定性随机"的实现细节值得留意）
- **变量**：`{{setvar::name::value}}` / `{{getvar::name}}` / `{{incvar::name}}`，分聊天局部和全局两个作用域
- **控制**：`{{if cond}}...{{/if}}`（支持取反、变量简写条件）
- **杂项**：`{{trim}}`（去首尾空行）、`{{// comment}}`（注释）

**实现建议**：`{{name::arg1::arg2}}` 正则/分词做一个 `name -> resolver` 的注册表即可覆盖 80% 真实用法（user/char/persona/description/time/random/variables）。`{{if}}` 和 `{{pick}}` 的确定性随机是唯二有点绕的部分，其余都是简单字符串替换。

JavaTavern 目前**零宏支持**——角色卡里任何用了 `{{char}}`/`{{user}}` 的内容都不会被替换，这是兼容性上的一个硬伤，很多从 SillyTavern 导出的角色卡会因此显示原始占位符。

## 5. Author's Note / 角色深度笔记 / Persona

**Author's Note** 三层合并：默认（全局）→聊天级覆盖→角色级覆盖（可选开关）。每层字段：`prompt` 文本、`interval`（每 N 条用户消息插入一次）、`depth`+`position`+`role`（用同一套深度注入原语）。

**角色深度笔记**（`character.data.extensions.depth_prompt`）：跟 Author's Note 是两码事，是角色卡自带的、每次生成都会在固定深度插入的一段文本，独立于 AN 的间隔触发机制。JavaTavern 的 `CharacterCardParser` 目前完全没解析这个字段。

**Persona（用户人设）**：JavaTavern **完全没有**这个概念——现在的"用户"就是裸的消息发送者，没有名字、没有描述、不会被注入 prompt。ST 里 Persona 是一个独立实体（名字+描述+头像+可选世界书），可以：
- 设一个全局默认 Persona
- 按聊天锁定一个 Persona（这个聊天永远用这个人设，换角色也不变）
- 按角色/群组锁定一个 Persona（多对多，一个 Persona 能连接多个角色）

这是**用户体验上非常容易被感知到缺失**的功能——没有 Persona 意味着无法在同一账号下扮演不同身份跟不同角色互动，而这是角色扮演类应用的核心卖点之一。

## 6. Token 预算细节

`最大上下文 - 回复长度 = 可用 Prompt 预算`，各家来源的最大上下文取值方式不同（有硬编码上限，如 NovelAI 各档模型 ≤8192）。世界书预算是百分比转 token 数，默认 25%，可选绝对值上限封顶。

JavaTavern 目前用固定 4000 字符（非 token）作为世界书预算，`TokenEstimator` 是启发式估算（CJK 按 1 token/字，其余 4 字符 1 token）——量级是对的，但没有百分比联动机制，也没有跟世界书预算打通。

## 7. 推理（Reasoning）模板

`{name, prefix, suffix, separator}` 的模板集合（如 DeepSeek 的 `<think>...</think>`）。**自动解析**：当模型把推理内容和正文混在同一个文本流里输出时（多数开源模型走 Text Completion 路线），靠前后缀字符串边界切割；有独立 API 字段的模型（Claude/Gemini/DeepSeek 官方 API/xAI）直接读字段，不需要切割。

对应 [api-connections.md](api-connections.md) 里提到的"原生推理提取"，这里补充的是**没有独立字段、混在正文里**的那类模型的处理方式——JavaTavern 如果要支持本地/自建模型（走类 OpenAI 接口但服务端把 `<think>` 标签混进 content），也需要这层前后缀解析。

## 8. 停止词与其他生成控制

**停止词**：自定义停止词列表（纯字符串数组）+ 指令模板自带的序列（如果开启"序列也当停止词"）+ 角色名（可选）。JavaTavern 目前完全没有停止词概念，`applyGenerationParams` 里没有 `stop` 字段。

**续写自动化**（`auto_continue`）：回复长度不够时自动点"继续"，是个简单的自驱动循环。

**收尾清理**：`trim_sentences`（截到最后一个完整句子）、`collapse_newlines`（多个空行合并）——纯字符串后处理，成本很低但用户能明显感知到"回复不会烂尾"。

## 9. 复杂度分级与实施建议（供路线图引用）

- **S，优先做**：停止词、trim_sentences/collapse_newlines、Start Reply With 前缀续写、角色卡深度笔记解析、推理模板存储字段（`extra.reasoning` 列）、续写自动化。
- **M**：核心宏注册表（~15-20 个高频宏）、Author's Note 三层合并、Persona 实体 + 三种锁定、指令模板引擎（面向未来的 Text Completion 支持）、Token 预算按百分比联动。
- **L，架构核心但要最先设计**：Chat Completion 的 Prompt 管理器——有序槽位列表 + 锚点 + 绝对位置深度注入 + 按生成类型触发 + 预算裁剪的两阶段构建器。这是"深度注入"这一个通用原语的最终形态，Author's Note、角色深度笔记、Persona 深度位、世界书 @D 位置，全部应该复用同一套注入机制，所以哪怕分阶段实现，这个原语要在第一阶段就设计对，避免后面推倒重来。

建议顺序：先做 Persona（S/M，用户感知强）→ 深度注入原语 + Author's Note（M，架构基础）→ 核心宏（M，兼容性刚需）→ 停止词/续写/收尾清理（S，见效快）→ 完整 Prompt 管理器（L，长期项目）。
