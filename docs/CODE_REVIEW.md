# JavaTavern 代码复审

复审日期：2026-08-23

## 已解决的关键问题

- 私聊请求携带角色设定、命中世界书、确认式记忆和最近上下文。
- 流式 delta 通过缓冲区按 50 ms 合并刷新，终态竞争由 `StreamSession` 收敛。
- 私聊流式生命周期迁入 `ChatViewModel`，旋转后按稳定操作 ID 恢复占位行。
- `/clear` 必须经过提案和用户确认，清空与结果落库使用事务并记录审计。
- 长会话使用最近 60 条首屏和 `id < beforeId` 键集分页，支持 FTS 跳转。
- 图片复制到私有目录并采样解码；API Key 使用 Keystore AES/GCM。
- 角色、消息、预设和群聊合并到统一 SQLite；旧库迁移失败会回滚并保留源文件。
- PNG 导入按不可信二进制处理，拒绝无符号超大 chunk 和截断数据。
- 群聊数据库写入移出主线程，页面销毁时取消活动请求并隔离旧回调。

## 当前主要债务

### 高优先级：群聊生命周期

群聊仍由 Activity 直接组织网络和 UI。应迁移到 ViewModel，补停止按钮、合并刷新、旋转恢复、并发终态测试和错误占位清理。

### 高优先级：数据库迁移测试

统一 `SQLiteOpenHelper` 已采用事务与失败保留策略，但仍缺真实设备上的 migration instrumentation test。继续扩展分支、摘要和导入导出前必须补齐。

### 中优先级：页面状态与 Provider

私聊 Activity 仍承担分页、图片和局部 UI 编排；需要 `SavedStateHandle` 和统一 screen state。Provider 目前仅抽象为 OpenAI-compatible 协议，Gemini/Anthropic 原生请求不应继续堆在同一实现中。

### 中优先级：性能和错误证据

已有分页、缓存、节流和首帧埋点不等于已证明性能优秀。仍需 Macrobenchmark、1000/10000 条消息、弱网、耗电和 vivo 真机数据，并细分 TLS、HTTP、协议、模型和图片错误。

## 当前验证

- 18 个 JVM 测试类，102 项测试，0 failures，0 errors。
- `testDebugUnitTest` 与 `lintDebug` 通过；lint 0 error、19 warnings。
- `assembleDebug` 在发布前单独执行并核验 APK。
- 仍未完成迁移 instrumentation test、旋转真机回归和群聊生命周期自动化测试。
