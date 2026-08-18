# 专题 04：Agent 与卡片

## 已实现链路

`LocalAgentRouter` 识别 `/plan`、`/status` 和 `/clear`。前两个生成只读 `AGENT_CARD`；`/clear` 生成带 `actionToken`、`actionType` 和 `PENDING` 状态的 `AGENT_PROPOSAL`。

```text
/clear
  -> 提案卡(PENDING)
  -> 用户取消：CANCELLED + audit
  -> 用户确认：CONFIRMED
  -> SQLite 事务清空当前角色消息
  -> 结果卡(SUCCEEDED) + audit
```

事务的意义是：不能出现“消息删了，但成功结果没写进去”的半完成状态。审计表独立于消息表，所以清空当前会话后仍能保留执行轨迹。

## 卡片状态

- `NONE`：只读卡片。
- `PENDING`：等待用户确认，显示确认/取消按钮。
- `CONFIRMED`：用户已授权，正在执行。
- `CANCELLED`：用户拒绝。
- `SUCCEEDED` / `FAILED`：最终结果。

## 为什么原生渲染

- 不执行任意 HTML/脚本。
- 按本地 schema 决定按钮和权限。
- 易于无障碍、主题和性能控制。
- 未知 action 不会通过反射直接调用 Android 方法。

## 当前边界

目前只有一个写工具，逻辑仍在 `ChatActivity`。下一步应定义 `AgentTool` 接口、参数 schema、风险级别和统一执行器，再把 UI 只保留为状态观察者。

## 口述题

用 60 秒解释：为什么“模型返回 clear_conversation”不能等价于“客户端立即清空聊天”？
