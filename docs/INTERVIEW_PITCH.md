# JavaTavern 面试讲稿

## 30 秒版本

JavaTavern 是我针对 AI 助手 Android 岗位做的原生 Java 项目。它不是 WebView 套壳，包含本地聊天历史、OpenAI-compatible SSE 流式响应、生成取消、Android Keystore 密钥保护，以及本地 Agent 指令和结构化卡片。为了避免只做功能 Demo，我还加入数据库版本迁移、单元测试和首帧耗时基线，下一步会补多模态与 Macrobenchmark。

## 消息发送链路

1. `ChatActivity` 接收输入并先把用户消息写入 UI 与数据库队列。
2. `LocalAgentRouter` 优先识别受控本地命令；命中后生成 `AGENT_CARD`。
3. 未命中 Agent 时读取 Keystore 保护的模型配置。
4. 已配置模型则由 `OpenAiCompatibleClient` 发起 SSE 请求；未配置则使用离线回复保证演示可用。
5. 网络线程解析 `data:` 事件，主线程增量刷新最后一条消息。
6. 用户停止或页面销毁时断开连接，最终文本再写入 SQLite。

## 不要夸大的地方

- 当前 Agent 是本地指令路由，还不是完整的多步工具调用 Agent。
- 当前只实现 OpenAI-compatible 文本协议，还没有图片输入。
- 首帧数字是测量基线，不是经过严格对照实验的优化结果。
- SQLite 实现用于展示原理，后续会迁移 Room/ViewModel。

## 必须能回答的追问

- 为什么流式网络回调不能直接修改 View？
- 为什么取消请求后不能把占位消息一直留在数据库？
- Android Keystore 保护了什么，不能保护什么？
- 数据库从 v1 升级 v2 为什么不能直接删表？
- Agent 写操作为什么需要确认卡片和审计日志？
