# JavaTavern 架构

## 当前边界

```text
ChatActivity + RecyclerView
  ├─ ChatRepository
  │    ├─ CharacterRepository
  │    ├─ ChatHistoryStore
  │    └─ LongTermMemoryStore
  ├─ CharacterCardParser / WorldBookPromptBuilder
  ├─ ImageAttachmentStore / MessageImageLoader
  ├─ LocalAgentRouter
  └─ OpenAiCompatibleClient / SseEventParser
```

`ChatRepository` 是聊天页面唯一的数据访问边界，统一角色、消息、搜索、Agent 审计和确认式长期记忆。`ChatActivity` 仍负责网络编排、临时输入和列表更新，这是下一阶段需要继续拆分的部分。

## 已落实的工程约束

- Java 17 与 XML Views，最低 Android 7.0。
- SQLite v1→v6 增量迁移，不通过删库规避兼容问题。
- 数据库使用单线程 `ExecutorService`，UI 更新切回主线程。
- OpenAI-compatible SSE 请求支持 50 ms 合并刷新、停止和生命周期取消。
- 图片后台压缩后写入私有目录，列表使用 LRU 缓存异步解码。
- API Key 使用 Android Keystore AES/GCM，加密配置只允许 HTTPS。
- Agent 写操作经过提案、确认、事务执行、结果和审计。
- 长期记忆只能由用户管理，并以长度受限的背景段落注入 Prompt。

## 下一阶段

1. 引入 `ChatViewModel + SavedStateHandle`，接管加载、发送、流式状态和旋转恢复。
2. 增加 `conversations`、`message_variants` 与分支关系，支持多会话和左右切换。
3. 把 Provider、AgentTool、备份恢复抽象为独立接口，减少 Activity 条件分支。
4. 增加数据库迁移测试、UI 测试和 Macrobenchmark。
