# vivo AI 助手 Android 岗位对齐

## 结论

项目已覆盖岗位最核心的可演示链路：对话、Agent 确认闭环、原生卡片、流式协议、多模态输入和移动端性能治理。仍不能声称具备生产级多 Provider、长期记忆或完整性能报告。

| 岗位要求 | 已有证据 | 仍需补强 |
| --- | --- | --- |
| 对话交互 | RecyclerView、SQLite 历史、图片消息、分页、FTS 搜索跳转 | 重新生成、回复、消息分支 |
| Agent 能力 | `/plan`、`/status`、`/clear`，提案—确认—事务执行—结果—审计 | 通用工具注册表、服务端工具 schema |
| 卡片渲染 | 原生 Agent 普通卡/提案卡/结果卡和状态按钮 | 多 ViewType、服务端 schema 降级 |
| 大模型协议 | OpenAI-compatible 请求、SSE、上下文窗口、世界书注入 | Gemini/Anthropic 原生适配、usage 统计 |
| 流式体验 | 50 ms 合并刷新、停止生成、页面销毁取消 | 弱网重试、断点和后台恢复 |
| 多模态 | Photo Picker、1600 px/85% JPEG 压缩、LRU 渲染、Data URL 请求 | 多图、相机、上传式协议 |
| 启动与性能 | 首帧埋点、`reportFullyDrawn`、键集分页、DiffUtil、FTS、图片缓存 | Macrobenchmark、1000/10000 条报告、vivo 真机 |
| 数据与安全 | DB v1→v5 迁移、Keystore、HTTPS-only、禁止备份、Agent 审计 | 主动加密导出、迁移自动化测试 |
| 代码质量 | 13 个 JVM 测试、Debug/Release 构建、lint 0 error、模拟器回归 | ViewModel、UI 测试、CI |

## 面试时可以准确表达

1. 项目是原生 Java/XML，而非 WebView 或跨端套壳。
2. 图片在后台压缩后存入应用私有目录，请求时转换成 OpenAI-compatible 多模态内容数组。
3. 流式 delta 不逐 token 刷 RecyclerView，而是每 50 ms 合并一次。
4. 危险 Agent 操作不会直接执行；`/clear` 必须确认，并在 SQLite 事务中完成清空和结果落库。
5. 长会话初始只取最近 60 条，向上滚动按自增 ID 键集加载，不使用越来越慢的 OFFSET。
6. 以上是已经实现并回归的事实；Macrobenchmark、耗电、vivo 真机数字仍是待办，不能包装成已完成。

## 差异化

- **可控 Agent**：突出权限确认、事务和审计，而不是只做聊天壳。
- **本地优先**：角色、世界书、聊天、图片都在应用私有空间，系统备份被禁止。
- **Android 证据**：Photo Picker、生命周期取消、Keystore、RecyclerView、SQLite migration 和内存缓存均可定位到代码。
- **许可证隔离**：只研究参考项目的产品边界，JavaTavern 不包含其源码和资产。
