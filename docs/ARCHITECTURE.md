# JavaTavern MVP 架构

## 目标

首版优先证明四件事：原生 Android 页面组织、Java 并发与生命周期意识、本地数据持久化、可替换的模型服务边界。

## 分层

```text
Activity / RecyclerView Adapter
              |
              v
CharacterRepository / ChatHistoryStore
              |
              v
SQLite                  ReplyEngine
                           |
                           v
                    Mock / Remote API
```

## 关键取舍

- 使用 Java 17 与 XML View，突出用户已有 Java 能力，并补齐 Android 原生基础。
- MVP 使用 `SQLiteOpenHelper` 展示数据库原理；第二阶段迁移 Room，形成可讨论的重构过程。
- 数据库操作放入单线程 `ExecutorService`，UI 更新切回主线程。
- `ReplyEngine` 隔离回复来源，当前离线模拟，后续替换为 OpenAI-compatible 流式客户端。
- API Key 暂未参与网络请求；正式接入前必须使用加密存储，日志中禁止打印密钥。

## 面向 vivo 岗位的后续强化

- 生命周期与状态恢复：旋转、进后台、进程被回收。
- 性能：首屏、消息长列表、数据库分页、内存与卡顿分析。
- 稳定性：弱网、超时、重试、请求取消和离线降级。
- 系统适配：通知权限、深色模式、字体缩放、不同分辨率与 vivo 真机验证。
- 工程质量：单元测试、UI 测试、R8、构建变体和 CI。
