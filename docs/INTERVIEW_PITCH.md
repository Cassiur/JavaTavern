# JavaTavern 面试讲稿

## 30 秒版本

JavaTavern 是我因为持续关注 SillyTavern 角色卡生态而开发的原生 Java Android 项目。它支持 JSON/PNG 角色卡、世界书、私聊与基础群聊、本地聊天历史、OpenAI-compatible SSE、图片多模态、本地 Agent 指令和密钥保护。私聊流式生命周期已经迁入 ViewModel，当前 102 项单元测试、lint 和 Debug APK 构建通过；它仍是个人工程项目，不把尚未完成的真机性能报告包装成生产结论。

## 消息发送链路

1. `ChatActivity` 接收输入，普通消息交给聊天控制器，本地命令优先进入 `LocalAgentRouter`。
2. 未命中 Agent 时读取 Keystore 保护的模型配置，由 `OpenAiCompatibleClient` 发起 HTTP/SSE 请求。
3. `ChatViewModel` 持有单次流式会话，管理连接、增量、停止、完成、异常与最终落库。
4. Activity 观察流式快照并更新 UI；页面旋转时 ViewModel 保留会话，避免重复占位和重复写入。
5. 历史记录、分页、搜索和消息动作统一经仓储层访问 SQLite。

## 不要夸大的地方

- 当前 Agent 是本地指令路由，还不是完整的多步工具调用 Agent。
- 已支持图片输入，但仍以 OpenAI-compatible 协议为主，没有完成所有厂商原生协议适配。
- 已使用 ViewModel 与仓储层管理流式和数据访问，但底层仍是 SQLite，不宣称已经迁移 Room。
- 已有分页、DiffUtil 等优化手段，没有 Macrobenchmark、耗电或 vivo 真机对照数据，不能声称完成系统级性能优化。
- 群聊是首版 Activity 编排，尚未达到私聊 ViewModel 的生命周期完整度。

## 必须能回答的追问

- 为什么流式网络回调不能直接修改 View？
- 为什么取消请求后不能把占位消息一直留在数据库？
- Android Keystore 保护了什么，不能保护什么？
- 数据库从 v1 升级 v2 为什么不能直接删表？
- Agent 写操作为什么需要确认卡片和审计日志？

面试前还应亲自完成一次旋转、停止生成、长会话搜索和数据库升级回归，并记录现象与结论。
