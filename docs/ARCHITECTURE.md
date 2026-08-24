# JavaTavern 架构

## 当前结构

```text
MainActivity / GroupListActivity
  ├─ CharacterRepository / GroupRepository
  ├─ CharacterCardParser / PngCharacterCardReader / AvatarStore
  └─ TavernDatabase

ChatActivity
  ├─ ChatViewModel
  │    ├─ StreamSession / StreamAccumulator
  │    ├─ OpenAiCompatibleClient
  │    └─ ChatRepository
  ├─ ChatAgentController
  ├─ ChatMessageActionsController / ChatSearchController
  └─ MessageAdapter / MessageImageLoader

GroupChatActivity
  ├─ GroupRepository / ChatRepository
  ├─ GroupPromptBuilder
  └─ OpenAiCompatibleClient
```

私聊流式会话由 `ChatViewModel` 持有，Activity 通过不可变快照渲染状态。`StreamSession` 负责一次请求的状态转换、终态竞争和一次性落库，`StreamAccumulator` 负责增量文本聚合。

角色、世界书、消息、FTS、Agent 审计、生成预设和群聊共用版本化 `TavernDatabase`。首次打开时会迁移旧 `characters.db` 与 `java_tavern.db`；迁移失败会回滚并保留源文件，不静默删除旧数据。确认式长期记忆仍保存在应用私有 `SharedPreferences`。

## 已落实的工程约束

- Java 17、XML Views、最低 Android 7.0。
- 数据库和文件写入使用进程级串行磁盘执行器，不阻塞主线程。
- 网络请求使用有界线程池；页面销毁时取消仍在运行的请求。
- OpenAI-compatible SSE 支持合并刷新、主动停止和异常终态。
- 图片后台压缩后写入私有目录，列表使用采样解码和 LRU 缓存。
- API Key 使用 Android Keystore AES/GCM，远程地址只允许 HTTPS。
- Agent 写操作经过提案、确认、事务执行、结果和审计。
- 所有导入内容按不可信输入处理，PNG chunk 长度和 JSON 字段均做边界校验。

## 当前债务

1. `ChatActivity` 已拆分流式与操作控制器，但仍承担较多页面编排；需要统一 screen state 和 `SavedStateHandle`。
2. 群聊仍是首版 Activity 编排，应迁移到 ViewModel 并补停止、节流和旋转恢复。
3. 手写 SQLite 缺少 migration instrumentation test；继续扩 schema 前应补测试并评估 Room。
4. Provider 仍集中在 OpenAI-compatible 实现，Gemini/Anthropic 原生协议应通过接口隔离。
5. 尚无 Macrobenchmark、长列表、弱网、耗电与真机对照报告。
