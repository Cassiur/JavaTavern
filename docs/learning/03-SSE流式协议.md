# 专题 03：SSE 流式协议

## 文件

- `network/OpenAiCompatibleClient.java`
- `network/SseEventParser.java`
- `network/ConversationWindow.java`
- `network/SseEventParserTest.java`

## 三个线程问题

1. 网络读取在后台线程。
2. delta 进入线程安全缓冲区。
3. View 更新切回主线程。

## 为什么合并 50 ms

模型可能产生大量小 delta。如果每个字符都触发 `notifyItemChanged`、测量和绘制，会造成主线程压力。50 ms 是当前经验值，必须用性能数据继续调整。

## 取消

`StreamCall.cancel()` 设置原子标记并断开连接。读取循环检查取消状态，取消导致的异常不应展示成普通错误。

## 实践

1. 增加畸形 JSON 测试。
2. 增加非 `data:` 行测试。
3. 模拟快速 1000 个 delta，观察合并刷新次数。

## 追问

- SSE 和 WebSocket 的适用场景有什么差别？
- 为什么 `[DONE]` 不等于 HTTP 连接一定已经关闭？
