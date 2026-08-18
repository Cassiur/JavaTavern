# JavaTavern 开发记录

## 2026-08-14｜MVP 0.1

### 背景

- 项目定位：面向 vivo 等客户端岗位的原生 Android 求职项目。
- 技术路线：Java 17 + XML View，不使用 WebView 套壳。
- 产品参考：MiniTavern 的移动端角色聊天流程、SillyTavern 的角色卡与多模型能力。
- 许可证边界：MiniTavern GitHub 仓库没有公开 Android 源码；SillyTavern 为 AGPL-3.0。本项目首版为原创实现，不复制其代码和视觉资产。

### 已完成

- 创建 `com.zcz.javatavern` 原生 Android 工程。
- 创建角色列表页、聊天页和模型设置页。
- 使用 RecyclerView 实现角色列表和聊天消息列表。
- 使用 `SQLiteOpenHelper` 按角色保存聊天历史。
- 使用单线程 `ExecutorService` 执行数据库读写，主线程更新 UI。
- 抽象 `ReplyEngine`，首版使用可替换的 `MockReplyEngine`。
- 修复 API 36 强制 edge-to-edge 导致标题与状态栏重叠的问题。

### 验证

- `assembleDebug`：成功。
- APK 安装：成功。
- API 36 模拟器冷启动：成功。
- 角色列表：首页显示正常。
- 聊天流程：进入角色、发送消息、模拟回复成功。
- 本地持久化：重新安装覆盖并重新进入后，历史消息仍可恢复。
- 崩溃日志：未发现 `FATAL EXCEPTION`。

### 产物

- Debug APK：`D:\study\android\JavaTavern\app\build\outputs\apk\debug\app-debug.apk`
- 首页截图：`D:\study\android\JavaTavern\app-home.png`
- 聊天截图：`D:\study\android\JavaTavern\chat-fixed.png`

### 下一步

1. 用户提供 vivo 目标岗位原文，据此调整技术优先级和简历措辞。
2. 用户说明准备复用的旧项目及其路径，确定后端接口复用方案。
3. 用户亲自讲清 `MainActivity -> CharacterAdapter -> ChatActivity` 的页面流转。
4. 接入 Room/ViewModel，再接 OpenAI-compatible 流式 API。
5. 实现 SillyTavern V2 JSON 角色卡导入。

## 2026-08-14｜MVP 0.2｜vivo JD 对齐

### 参考审查

- 下载 `dghiffjd7/AiChat` 到 `D:\study\references\AiChat` 进行独立审查。
- 该项目为 Tauri v2 + JavaScript/Rust 跨端实现，不是原生 Android Java/Kotlin 项目。
- 许可证为 AGPL-3.0，因此只参考产品能力和模块边界，不复制源码、资源或页面实现。

### 新增能力

- OpenAI-compatible `/chat/completions` SSE 客户端。
- `data:` 事件增量解析、`[DONE]` 收尾、HTTP 错误归一化。
- 生成中发送按钮切换为停止按钮，页面销毁时主动取消连接。
- Android Keystore AES/GCM 加密 API Key，日志中不输出密钥。
- `/plan` 与 `/status` 本地 Agent 路由。
- `AGENT_CARD` 消息类型与原生结构化卡片渲染。
- SQLite v1→v2 无损迁移，新增消息类型和卡片标题字段。
- Application 首帧日志和 `reportFullyDrawn()`。
- 3 个 Agent 路由测试、3 个 SSE 解析测试和 2 个上下文窗口测试。

### 回归结果

- `testDebugUnitTest`：8/8 通过。
- `assembleDebug`：成功。
- 覆盖安装后旧消息保留，数据库升级无异常。
- Agent 卡片在 API 36 模拟器显示正常，进程重启后仍可恢复。
- 冷启动测量样本：ActivityTaskManager Fully drawn 约 2165 ms；应用内部首帧埋点约 1215 ms。当前仅为单次基线，不宣传为优化结论。
- 崩溃与 SQLite 异常：0。

### 代码复审修正

- 模型请求由单轮消息升级为最近 20 条纯文本上下文窗口。
- SSE delta 改为 50 ms 合并刷新，降低 token 级 UI 重绘频率。
- 移除基于毫秒时间戳的伪稳定 RecyclerView ID，避免潜在碰撞。
- 关闭 Android 自动云备份，显式禁止明文网络流量。
- 模型设置页启用 `FLAG_SECURE`，降低 API Key 被截图或录屏捕获的风险。
- 新增核心功能取舍与代码复审文档，明确当前未完成项。

### 截图

- Agent 卡片：`D:\study\android\JavaTavern\agent-card.png`

## 2026-08-14｜MVP 0.3｜Agent、多模态与长会话

### 新增能力

- `/clear` 危险操作提案、确认/取消、事务执行、结果卡和独立 Agent 审计。
- SillyTavern V2 JSON 角色卡导入、SHA-256 去重和 SQLite 角色库。
- 关键词/常驻世界书，最近会话激活和 4000 字符注入预算。
- Android Photo Picker、15 MB 输入限制、最长边 1600 px、JPEG 85% 压缩。
- 图片私有持久化、列表采样解码、LRU 缓存和 OpenAI-compatible `image_url` Data URL。
- 消息库 v3→v4 图片字段、v4→v5 FTS 表和同步触发器。
- 最近 60 条首屏、每页 40 条键集分页、DiffUtil 和搜索上下文跳转。
- 关闭云备份与设备迁移、移除根布局过绘制、补应用图标和 autofill 策略。

### 模拟器回归

- Agent 取消后跨重启保持 `CANCELLED`，按钮不再出现。
- Agent 确认后只清空当前角色消息，成功结果跨重启保留。
- 旧数据库逐级升级到 v5，旧卡片和图片消息未丢失。
- 示例 V2 角色卡经系统文档选择器导入，重启后角色与开场白恢复。
- 图片消息选择、压缩、发送、落库和重启渲染通过；样例压缩后约 64 KB。
- FTS 搜索 `what` 命中图片消息并跳转到前后上下文。
- 单次聊天进程 PSS 约 64 MB，仅记录为后续对照基线。

### 工程验证

- JVM 单元测试：13/13 通过。
- `testDebugUnitTest assembleDebug lintDebug`：通过。
- Android lint：0 error，仅保留 2 条依赖版本提示。
- 崩溃、SQLite migration 异常、图片 OOM：本轮回归未发现。

## 2026-08-14｜MVP 0.4｜开源准备与低打扰体验

### 工程与产品

- 增加 MIT License、贡献指南、安全策略、隐私说明、Issue/PR 模板和 Android CI。
- 完成 MiniTavern、SillyTavern、ChatterUI、LettuceAI、PocketTavern 等产品能力对照，只借鉴公开格式与交互原则，不复制源码和视觉资产。
- 首页改为“继续哪段故事”，弱化工具术语；新建与编辑角色不再要求用户先准备 JSON。
- 模型连接页提供 OpenAI、DeepSeek、OpenRouter 和自定义预设，并使用 `/models` 做轻量连接检查。
- 每个角色独立保存未发送草稿；生成期间仍可输入下一条内容。
- 长按消息可复制、编辑或删除，SQLite FTS 触发器同步维护搜索索引。
- 流式回复仅在用户靠近列表底部时自动跟随，阅读旧消息时不强制跳回最新内容。

### 验证

- `testDebugUnitTest assembleDebug lintDebug`：通过。
- Android lint：0 error。
