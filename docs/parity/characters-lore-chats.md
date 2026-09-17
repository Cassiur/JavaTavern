# 角色卡 / 世界书 / 聊天 / 群组 / Persona —— SillyTavern 行为清单

调研对象：本地 SillyTavern 1.18.0。行为/格式语义记录，不含 AGPL 代码复制。

JavaTavern 现状（已核实）：V2 JSON + PNG tEXt（`chara`/`ccv3` 关键字，未压缩 iTXt）导入；description/personality/scenario/system_prompt 被**有损拼接**成一整段中文 system prompt 存储，原字段结构不保留（导致做不了导出，也做不了分段组装）；世界书只区分"角色定义前/后"两个位置，关键词纯包含匹配（不分大小写），无次要关键词/逻辑组合/扫描深度/整词匹配/sticky/cooldown；一个角色一个聊天，重新生成的回复存成 swipe 版本；基础群聊，手动选发言者。

---

## 1. 角色卡格式

### V2（`chara`）`data` 必需字段
`name, description, personality, scenario, first_mes, mes_example, creator_notes, system_prompt, post_history_instructions, alternate_greetings[], tags[], creator, character_version, extensions{}`，可选 `character_book`。

**`data.extensions`**（SillyTavern 自己的命名空间，JavaTavern 需要独立设计等价字段，不是照抄）：
- `talkativeness`（0-1，群聊里决定发言概率）
- `fav`（收藏）
- `world`（关联的主世界书文件名）
- `depth_prompt: {prompt, depth, role}`（角色的"深度笔记"，见 [prompt-engine.md](prompt-engine.md) §5）
- `regex_scripts[]`（角色自带的查找替换脚本，见 [extensions-app.md](extensions-app.md) §正则）

### V3（`ccv3`）
是 V2 的超集，多了 `assets[]`（`embeded://` 前缀指向 `.charx` 包内文件，类型含 icon/expression/background）。**关键发现**：SillyTavern 自己写卡时，V3 是**从 V2 派生出来的**——每次保存先构造完整 V2 JSON，再克隆一份改 `spec`/`spec_version` 字段另存一份 `ccv3` chunk；读取时优先读 `ccv3`，没有则退回 `chara`。也就是说 V3 不是独立权威结构，JavaTavern 不需要为 V3 单独设计数据模型，V2 字段集合已经覆盖核心需求。

### PNG chunk 细节
只用 **tEXt**（未压缩），没有 zTXt/压缩 iTXt 的写入。**这意味着 JavaTavern 现在的"未压缩 iTXt"支持已经能兼容 SillyTavern 导出的卡**——压缩 iTXt/zTXt 只在兼容其他小众来源时才用得上，不是 ST 兼容性的硬需求，可以降低优先级。

### 其他格式
- **`.charx`**（ZIP 容器）：内含 `card.json`（V2/V3）+ 素材文件。
- **`.byaf`**（Backyard AI 存档，ZIP）：`manifest.json` + 角色 JSON + 多个"场景" JSON；场景的首条消息映射成 `first_mes`/`alternate_greetings`；场景里的对话历史可以在首次导入时自动建聊天记录。
- **URL 导入**：Chub/CharacterHub、Pygmalion.chat、JanitorAI、AICharacterCards.com、RisuRealm、Perchance，各自不同的 REST API 形状，需要逐个适配。

### 有损问题的具体修法
JavaTavern 要做"导出"和"分段 prompt 组装"，必须先把 `CharacterCardParser` 从"拼成一段文字"改成**保留原始分段字段**（description/personality/scenario/system_prompt/post_history_instructions/alternate_greetings/creator_notes/character_version 分别存列），把拼接这一步挪到 prompt 组装阶段（即 [prompt-engine.md](prompt-engine.md) 的 Prompt Manager），而不是导入阶段就损失信息。这是后续几乎所有角色卡相关功能（导出、多开场白、备用问候语）的地基，应该最先做。

## 2. 角色管理

- **标签**：标签实体 + `character_id ↔ tag_id` 关联表；一个标签可以标记为"文件夹"，让分享该标签的角色在列表里折叠成一个可展开的组（纯 UI 概念，不是真实文件夹）。
- **收藏**：布尔字段。
- **复制/改名**：复制是纯文件拷贝（不复制聊天记录）；改名要同时改内部 `name` 字段和聊天文件夹路径。
- **批量编辑**：用一次 JSON merge 应用到多个角色，带"删除该字段"的哨兵值——JavaTavern 移动端场景下不需要这么复杂，简单的"选中多个角色批量设置某字段"就够。

## 3. 世界书（World Info / Lorebook）

### 完整字段表（JavaTavern 现有字段用 ✅ 标注，缺的用 ❌）

| 字段 | 语义 | JavaTavern |
|---|---|---|
| `key` / `keysecondary` | 主/次关键词 | ✅ 仅主关键词 |
| `selectiveLogic` | 次关键词组合逻辑：AND_ANY / NOT_ALL / NOT_ANY / AND_ALL | ❌ |
| `constant` | 常驻激活 | ✅ |
| `order` / `priority` | 排序权重 | ✅ order，❌ priority |
| `position` | before/after/AN顶/AN底/@深度/示例消息顶/示例消息底/outlet，共7种 | ✅ 仅 before/after 2 种 |
| `disable` | 禁用 | ✅ enabled 字段等价 |
| `depth` | @深度位置用 | ✅ 存储但未真正按深度注入 |
| `probability` + `useProbability` | 触发概率 | ✅ |
| `excludeRecursion` / `preventRecursion` | 递归扫描控制 | ✅ |
| `scanDepth` / `caseSensitive` / `matchWholeWords` | 每条目覆盖全局扫描参数 | ❌ |
| `group` / `groupOverride` / `groupWeight` / `useGroupScoring` | 互斥组，加权随机选一个激活 | ❌ |
| `sticky` / `cooldown` / `delay` | 计时效果：激活后强制保持N轮/结束后冷却N轮/开局N轮内不激活 | ❌ |
| `matchPersonaDescription` 等 6 个 match 开关 | 除聊天记录外还扫描哪些文本源 | ❌ |
| `characterFilterNames/Tags` | 按角色名/标签限定条目生效范围 | ❌ |
| `triggers[]` | 限定在哪些生成类型下生效 | ❌ |

### 激活算法要点
1. 计算 token 预算 = 上下文窗口 × 百分比（默认 25%），可选绝对值封顶。
2. 候选排序：聊天绑定世界书 > Persona 世界书 > 角色/全局（按策略交错或分先后）。
3. 逐条目判断：跳过禁用/不匹配生成类型/在 delay 期内/在 cooldown 期内；`constant` 和当前 `sticky` 的直接激活；否则做关键词扫描（主关键词必须命中，再按 `selectiveLogic` 判断次关键词）。
4. 新激活的条目做**互斥组过滤**：同组内 sticky 优先，否则按 `groupOverride`（最高 order 直接赢）或按 `groupWeight` 加权随机抽一个。
5. 概率骰子。
6. 预算检查，超预算的非 `ignoreBudget` 条目不激活。
7. 递归：新激活条目的内容本身可以作为下一轮扫描的搜索文本（最多 N 步），直到没有新激活或达到步数上限。
8. 按 `position` 分桶组装进最终 prompt 的六个不同位置。

**这个算法是世界书里复杂度最高的单一部分**，建议照抄整体流程（不是照抄代码，是照抄步骤顺序和状态机），不要重新设计——ST 这套是经过大量边界情况打磨的。

### 多来源挂载与优先级
全局选中的世界书 + 角色主世界书（`data.extensions.world`）+ 角色附加世界书（可以挂多个）+ 聊天绑定世界书 + Persona 绑定世界书，五个来源按固定优先级合并去重。JavaTavern 现在只有"角色自带世界书"一种来源。

### 导入格式
NovelAI Lorebook、Agnai Memory Book、RisuAI Lorebook、ST 原生 JSON、内嵌 `naidata` 的 PNG——五种格式自动按 JSON 形状探测（不需要用户手动选格式）。

## 4. 聊天

### 存储格式（JSONL，一行一条 JSON）
首行是聊天级元数据（`chat_metadata`：世界书绑定、Persona 锁定、sticky/cooldown 计时状态、场景覆盖）；每条消息行含 `name/is_user/is_system/send_date/mes/extra/swipes/swipe_id/swipe_info`。

**`is_system` 就是"从 prompt 里隐藏此消息"的开关**——消息还留在聊天记录和 UI 里，只是不进 context。JavaTavern 目前没有"隐藏消息"这个概念，只有硬删除。

**`swipes[]` / `swipe_info[]`**：swipe_info 是跟 swipes 平行的数组，每个 swipe 有独立的生成耗时/token数/模型信息——JavaTavern 现有的 swipe 版本机制方向一致，可以对照补上"每个版本独立记录生成元数据"这部分。

**`extra` 字段值得关注的**：`reasoning`+`reasoning_duration`（推理内容和耗时）、`display_text`（经过正则处理后展示给用户的文本，跟发给模型的原始 `mes` 分开存——这个"展示版本 vs 发送版本分离"的设计在做正则脚本功能时会需要）、`tool_invocations`（工具调用记录）。

### 一角色多聊天
ST 原生支持——聊天文件按 `角色目录/聊天文件名.jsonl` 存，角色对象只记"当前打开的是哪个"。JavaTavern 现在是一角色一聊天的强假设，这是数据模型层面**优先级最高的一项改造**，因为分支/检查点/聊天改名/聊天搜索/聊天导出全部建立在"一个角色可以有多个聊天行"这个前提上。

### 分支与检查点
两者底层机制相同：把当前聊天截到某条消息为止，另存成一个新聊天文件，新文件的元数据里记一个指回原聊天的指针（`main_chat`）。检查点（Checkpoint/Bookmark）是"从当前显示的版本"截断；分支（Branch）额外支持"从某条消息的某个特定 swipe 版本"截断，且一条消息可以有多个分支（数组）但只有一个"最新检查点"链接（单值）。

**这意味着**实现分支/检查点不需要新的消息级 schema，只需要：聊天表加 `parent_chat_id` + `branch_point_message_id`，消息表加一个可选的"这条消息衍生出了哪些分支"记录。

### 聊天导入
oobabooga（`data_visible` 数组对）、Agnai、CAI Tools（一次导入可能拆成多个聊天文件）、Kobold Lite、RisuAI——五种格式，按 JSON 顶层字段形状自动探测，不需要用户选格式。

## 5. 群聊

### 激活策略（JavaTavern 现有"手动选择"= MANUAL）
- **NATURAL**：扫描用户输入里提到的角色名（提到的必激活），其余角色按各自 `talkativeness`（0-1）概率骰
- **LIST**：全员按列表顺序轮流发言
- **POOLED**：优先选"这轮还没发过言"的成员，都发过了就随机挑（避开连续重复同一人）
- **MANUAL**：用户手动指定（JavaTavern 已有）

### 生成模式（角色卡如何拼进群聊 prompt）
- **SWAP**：只用当前发言角色自己的卡片字段（JavaTavern 现状）
- **APPEND**：拼接**所有未静音成员**的描述/性格/场景（各自可配置前后缀模板）
- **APPEND_DISABLED**：同 APPEND，但**连静音成员也包含进去**——这是"静音成员是否参与拼接"这个问题的确切答案：只有这个模式下才包含。

### 成员管理
静音（`disabled_members[]`，不是移除，只是激活时跳过）、自动模式（定时器按 `auto_mode_delay` 秒间隔自动让下一个人发言）、每个新成员加入时可以有自己的开场白（从该角色的 alternate_greetings 里随机选）。

## 6. Persona（用户人设）

JavaTavern **完全没有**这个实体，详见 [prompt-engine.md](prompt-engine.md) §5。数据模型：`personas` 表（name/description/title/position/depth/role/lorebook_id/avatar），加一个全局默认 Persona 设置 + 按聊天锁定 + 按角色/群组锁定（多对多）三种解析优先级。这是六个调研领域里**复杂度最低、用户感知最强**的一块，建议第一阶段就做。

## 7. 复杂度与实施建议（供路线图引用）

**优先级 0（地基）**：
1. 角色卡解析从"拼接存储"改成"分段字段存储"——不做这个，导出/多开场白/示例对话/system_prompt 独立管理全部无法推进。
2. 一角色多聊天的数据模型改造——不做这个，分支/检查点/聊天搜索/聊天导出全部无法推进。
3. Persona 实体。

**优先级 1**：
4. 世界书字段补全（selectiveLogic、priority、scanDepth/caseSensitive/matchWholeWords、@深度真正生效）——中等复杂度，直接提升现有功能的兼容性。
5. 消息 `is_system` 隐藏标记 + `display_text`/`mes` 分离。
6. 备用开场白（alternate_greetings）+ 示例对话（mes_example）——地基打好后这两个字段的解析和使用都是小工作量。

**优先级 2**：
7. 世界书互斥组（group/groupWeight/groupOverride）+ sticky/cooldown/delay 计时效果——世界书里最复杂的部分，放后面。
8. 分支与检查点。
9. 群聊 NATURAL/POOLED 策略 + APPEND 生成模式。
10. 聊天/角色卡导入导出（覆盖 ST 原生 JSON 格式即可，第三方格式按需再加）。

**优先级 3 或按需**：CharX/BYAF 容器格式、URL 导入源（每个来源都要单独适配对方 API，边际收益递减）、标签文件夹、批量编辑。
