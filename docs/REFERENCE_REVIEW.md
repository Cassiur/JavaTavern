# AiChat 参考审查

## 基本信息

- 上游：`https://github.com/dghiffjd7/AiChat`
- 本地只读参考：`D:\study\references\AiChat`
- 审查提交：`e5fed66`
- 技术栈：Tauri v2、JavaScript、Rust，Android 由 Tauri 生成工程承载。
- 许可证：AGPL-3.0。

## 借鉴的产品原则

- Provider 与聊天业务分离。
- 流式输出必须支持主动取消。
- Agent 写操作需要确认和可审计记录。
- 结构化内容使用独立卡片，而不是把所有内容塞进纯文本气泡。
- 长会话需要分片或分页，不能无限把全部消息同时载入内存。

## 明确未复制

- 未复制上游 JavaScript、Rust、HTML 或 CSS。
- 未复制上游图片、图标、角色、提示词和页面布局。
- 未使用上游生成的 Android/Tauri 工程。
- JavaTavern 的 Java 类、XML 布局、数据库 schema 和协议客户端均为独立实现。

## 为什么不照搬

vivo 岗位明确要求 Java/Kotlin 与 Android 基本原理。直接复用 Tauri 项目会削弱原生 Android 证据，也会引入 AGPL 许可证义务，和本项目的求职目标不一致。
