# JavaTavern 代码复审

复审日期：2026-09-14（对当前 `main` 分支 `7e41c5b`／v0.4.1 重新取证，不沿用旧版结论）

## 已解决的关键问题

- 私聊请求携带角色设定、命中世界书、确认式记忆和最近上下文；上下文按可配置 token 预算（默认 8000）裁剪，不再按固定条数硬切。
- 流式 delta 通过缓冲区按 50 ms 合并刷新，终态竞争由 `StreamSession` 收敛。
- 私聊与**群聊**流式生命周期都已迁入 `ChatViewModel`（`GroupChatActivity` 复用同一套 `StreamSnapshot` 观察者），旋转屏幕或切后台不丢流；**本轮**群聊也补上了停止按钮（`GroupChatActivity.handlePrimaryAction()` 复用 `ChatViewModel.stopStreaming()`，与私聊 `ChatActivity.java:524` 同一套机制），此前 README 宣传的"随时中断"只对私聊成立。
- `/clear` 必须经过提案和用户确认，清空与结果落库使用事务并记录审计。
- 长会话使用最近 60 条首屏和 `id < beforeId` 键集分页，支持 FTS 跳转。
- 图片复制到私有目录并采样解码；API Key 使用 Keystore AES/GCM。
- 角色、消息、预设和群聊合并到统一 SQLite；旧库迁移失败会回滚并保留源文件。
- PNG 导入按不可信二进制处理，拒绝无符号超大 chunk 和截断数据；已支持 `tEXt`/未压缩 `iTXt` 提取头像。
- 世界书已有应用内可视化编辑（位置、顺序、优先级、深度、概率、递归）。
- 重新生成的回复保留为可左右翻页的版本历史，而不是覆盖或另起一条。
- 消息渲染支持 Markdown（粗体/斜体/行内代码/代码块/引用/列表），不再直接显示 `*action*` 星号。
- 支持深色主题（`values-night`）。
- 完整备份/恢复为可分享 ZIP：角色、聊天（含每个重新生成版本）、世界书、群聊、预设、记忆、草稿与图片；恢复是全有或全无事务，按新设备重写路径并清理孤立图片；API Key 因 Keystore 与设备绑定而被刻意排除。
- Release 构建已接入真实 `signingConfig`（从环境变量读取，本地无变量时回退 debug 签名保证 `assembleRelease` 可跑）；CI 改为构建并校验 release APK，R8 压缩后 3.8 MB（对比 debug 8.6 MB）。
- **本轮**：`SseEventParser.parse()` 遇到 `data:` 开头但 payload 不是合法 JSON 的行不再抛出并中止整个流，而是把该行当噪声跳过、保留已生成内容（`SseEventParserTest` 补充了畸形/半截 JSON 与非 JSON payload 两个回归用例）。
- **本轮**：持久化层引入 `org.robolectric:robolectric:4.17` 作为 `testImplementation`，为 `TavernDatabase`（v1→v5 完整迁移链路、成功与失败两种遗留库迁移路径）、`ChatHistoryStore`（键集分页边界、重 roll 版本切换、FTS/LIKE 回退、事务性清空）、`BackupRepository`（导出→恢复完整往返、路径重写、孤儿文件清理、全量替换语义、坏归档拒绝）补齐了此前完全没有的 JVM 内测试覆盖，见下方「本轮完成的批次」。

## 当前主要债务

### P1：`ChatActivity` 职责仍偏重

同时持有分页、图片、Agent 确认、草稿、搜索等 UI 编排（813 行）。下一步是 `SavedStateHandle` 统一 screen state，把非流式逻辑也拆出去。

### P1：Provider 抽象仅覆盖 OpenAI-compatible 协议

`OpenAiCompatibleClient` 是目前唯一的网络实现；Gemini/Anthropic 原生协议不应堆进同一个类，需要先定义一个 Provider 接口再新增实现。

### P2：性能和真机证据仍未产出

已有分页、缓存、节流和首帧埋点不等于已证明性能优秀。仍需 Macrobenchmark、1000/10000 条消息、弱网、耗电和 vivo 真机数据。

### P2：持久化测试覆盖了核心路径，但不是穷尽式的

本轮新增的三个 Robolectric 测试类覆盖了迁移、分页边界、版本切换、备份往返等最高风险路径，但 `ChatHistoryStore`（736 行）和 `BackupRepository`（715 行）的次要方法（如 `loadMessageContext`、`clearConversationAndAddAgentResult`、附件缺失时的清空逻辑）仍未逐一覆盖。真实设备上的 migration instrumentation test 依然没有——Robolectric 证明了迁移 SQL 本身正确，但不能替代在真实 Android SQLite 实现和真机 I/O 行为上的验证。

## 本轮完成的批次

按"小步提案 → 实现 → 验证"执行，全部已落地并通过 `testDebugUnitTest lintDebug assembleDebug`：

1. 群聊补停止按钮，复用 `ChatViewModel.stopStreaming()`（`GroupChatActivity.java`）。
2. `SseEventParser` 单行 JSON 解析失败时跳过该行而不是中止整个流；补充两个回归单测。
3. 引入 Robolectric，为 `TavernDatabase`（4 个测试，含 v1→v5 迁移与遗留库迁移成功/失败两条路径）、`ChatHistoryStore`（6 个测试）、`BackupRepository`（2 个测试）补齐 JVM 内可跑的持久化测试，不依赖模拟器。副产品：`TavernDatabase` 新增一个仅测试可见的 `resetSingletonForTest()`，用于在测试间清理进程级单例；`app/build.gradle.kts` 新增了 Robolectric 在 JDK 17+/23 下必需的 `--add-opens`/`--add-exports` 测试 JVM 参数（仅影响测试进程，不影响出包）。

不在本轮范围内（仍是已知债务，留给后续批次）：`ChatActivity` 的 `SavedStateHandle` 重构、Provider 接口抽象、Macrobenchmark/万条消息/弱网/耗电/vivo 真机数据、真实设备 migration instrumentation test。

## 当前验证

- 26 个 JVM 测试类，180 项测试，0 failures，0 errors（`app/build/test-results/testDebugUnitTest`；本轮从 166 项增至 180 项：SSE 解析 +2，`TavernDatabaseTest` +4，`ChatHistoryStoreTest` +6，`BackupRepositoryTest` +2）。
- `testDebugUnitTest` 与 `lintDebug` 通过；lint 0 error、20 warnings（`app/build/reports/lint-results-debug.xml`；新增的 4 条均为依赖版本提示，不是本轮代码引入的问题）。
- `assembleDebug` 与 `assembleRelease`（R8 压缩）均构建成功；本轮新增的 Robolectric/androidx.test 依赖仅在 `testImplementation` 作用域，不影响 release 包体积。
- 仍未完成：真实设备 migration instrumentation test、真机旋转回归、群聊并发终态自动化测试。
