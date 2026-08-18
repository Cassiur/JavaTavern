# JavaTavern 代码复审

## 已解决的关键问题

### 请求缺少连续上下文

请求现携带最近 20 条文本或图片消息，并过滤 Agent 卡片。角色卡基础 Prompt、命中的世界书和当前对话会共同组成请求。

### 流式输出频繁刷新 UI

网络 delta 先进入线程安全缓冲区，每 50 ms 合并更新一次；完成和取消前强制 flush，减少 RecyclerView 重绑和主线程消息数量。

### 危险 Agent 写操作直接执行

`/clear` 现在生成带随机 action token 的提案卡。用户确认后，清空消息和写入结果卡在同一 SQLite 事务中完成；取消、确认、成功和失败写入独立审计表。

### 长会话全量加载

首屏只读最近 60 条，向上滚动按 `id < beforeId` 加载 40 条。键集分页不会像大 OFFSET 一样随着历史增长反复跳过记录。

### 图片解码和持久权限风险

Photo Picker 返回的 URI 不直接长期保存。图片在后台解码、缩放至最长边 1600 px、JPEG 85% 压缩后复制到应用私有目录；列表使用采样解码和 LRU 缓存。

### RecyclerView 全量刷新和根布局过绘制

角色和消息列表使用 DiffUtil/范围插入；主题负责窗口背景，Activity 根布局不再重复绘制同色背景。lint 从 16 条降到 2 条依赖版本提示。

### 本地数据和密钥暴露

系统备份/设备迁移被规则排除，API Key 使用 Keystore AES/GCM，密钥页启用 `FLAG_SECURE`，模型地址只允许 HTTPS。

## 当前主要债务

### P1：ChatActivity 职责过重

它仍承担 UI、分页、搜索、图片处理、Agent、网络和数据库协调。下一步必须迁移为 `ChatViewModel + ChatRepository + SavedStateHandle`，否则旋转恢复和自动化测试会越来越困难。

### P1：数据库仍是手写 SQLiteOpenHelper

v1→v5 迁移已经在保留旧数据的模拟器上逐级验证，但缺少 migration instrumentation test。继续增加消息分支、摘要和群聊前应迁移 Room。

### P1：Provider 仍是单实现

现仅支持 OpenAI-compatible SSE。Gemini 与 Anthropic 的 URL、认证、请求和事件不同，应使用 `ModelProvider` 接口隔离，不能在 Activity 中堆条件分支。

### P2：图片与请求错误恢复

需要区分文件缺失、解码失败、请求体过大、DNS、TLS、HTTP、协议和模型错误；远程模型不支持视觉时应给出可理解提示。

### P2：性能证据仍不完整

已有实现手段不等于性能结论。还缺 Macrobenchmark、多轮启动统计、1000/10000 条长列表、弱网、耗电和 vivo 真机数据。

## 当前验证

- 13 个 JVM 测试通过。
- `testDebugUnitTest assembleDebug lintDebug` 通过，lint 0 error。
- API 36 模拟器验证数据库 v2→v3→v4→v5、Agent 取消/确认、角色卡导入、图片发送重启恢复、FTS 搜索跳转。
- 图片样例压缩后约 64 KB；单次运行进程 PSS 约 64 MB，只作为基线，不作为优化结论。
