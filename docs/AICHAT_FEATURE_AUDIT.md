# JavaTavern 与 AiChat 功能核验

核验日期：2026-08-23

## 结论

JavaTavern 是原生 Java Android 本地优先角色聊天客户端，不是 AiChat 的全量复刻。当前已形成可安装、可配置、可导入角色并持续聊天的完整主链路，覆盖私聊、基础群聊、SSE 流式、多模态图片、JSON/PNG 角色卡、世界书、生成参数预设、本地历史、确认式长期记忆与受控 Agent。创意写作、生图、动态、完整 Agent 工具体系和跨设备备份仍未实现。

## 已实现

| 能力 | JavaTavern 当前实现 |
| --- | --- |
| 原生 Android | Java 17、XML Views、RecyclerView；最低 Android 7.0 |
| 私聊 | 分角色历史、最近上下文、离线演示回复、远程模型流式回复 |
| 基础群聊 | 创建群聊、选择成员、手动选择本轮发言角色、保存群聊历史 |
| 流式生命周期 | OpenAI-compatible SSE、停止生成、50 ms 合并刷新；私聊状态由 `ChatViewModel` 保留 |
| 图片输入 | Photo Picker、后台压缩、私有目录存储、LRU 解码与 `image_url` 多模态请求 |
| 消息能力 | 草稿、复制、编辑、删除、回复引用、emoji 反应、重新生成、键集分页、FTS 搜索与跳转 |
| 角色管理 | App 内创建和编辑；SillyTavern V2 JSON、PNG `tEXt`/未压缩 `iTXt` 导入 |
| 世界书 | 关键词/常驻规则、位置、顺序、优先级、深度、概率和递归字段编辑与 Prompt 注入 |
| 模型配置 | OpenAI、DeepSeek、OpenRouter、自定义兼容端点；`/models` 连接检查 |
| 生成预设 | 内置和自定义 temperature、top-p、max tokens、frequency/presence penalty |
| 受控 Agent | `/plan`、`/status`、`/clear`；清空操作经过提案、确认、事务执行和审计 |
| 长期记忆 | 用户手动确认新增/删除；限制单条、数量与 Prompt 总长度 |
| 本地安全 | Android Keystore AES/GCM、HTTPS-only、关闭 Android 自动备份 |
| 数据层 | 统一 `TavernDatabase`、旧库保留式迁移、消息 FTS、角色和群聊关系 |

## 部分实现

| 能力 | 已有部分 | 仍然缺少 |
| --- | --- | --- |
| Agent 助手 | 本地指令路由、确认和审计 | 自然语言工具调用、工具注册表、多步任务、跨轮续作、Agent Center |
| 卡片渲染 | 本地 Agent 普通卡、提案卡、结果卡 | 版本化 schema、多 ViewType 降级和复杂交互组件 |
| 角色卡兼容 | JSON/PNG 基础字段、头像与世界书 | alternate greetings、example dialogue、压缩 `iTXt`、正则脚本和导出 |
| 世界书 | 常驻/关键词规则和高级排序字段 | 条件图、命中解释、AI 自动生成 |
| 群聊 | 成员管理、手动指定发言者、历史落库 | `@` 提及、自动调度、群聊 ViewModel、停止按钮和长期记忆 |
| Provider | OpenAI-compatible 协议覆盖多家服务 | Gemini/Anthropic 原生协议、按会话独立配置、usage 统计 |
| 数据恢复 | 统一数据库和失败保留旧库 | ZIP 导入导出、冲突预览、完整性校验、迁移 instrumentation test |
| 性能 | 分页、DiffUtil、图片缓存、流式节流、首帧埋点 | Macrobenchmark、万条消息、弱网、内存、耗电和 vivo 真机报告 |

## 未实现

- 持久化消息分支、左右切换和完整版本树。
- AI 回复整轮验收、补丁修复、Prompt 浏览器与请求血缘图。
- 自动摘要、自动记忆表格、跨模块记忆共享。
- 独立创意写作、图片生成、贴图管理和动态/朋友圈。
- 角色库、标签检索和相似角色推荐。
- 自定义聊天壁纸、桌面版和大屏双栏布局。
- 完整数据打包导入导出与跨设备恢复。

## 后续开发优先级

1. 为群聊补齐 ViewModel、停止生成、错误恢复和并发测试。
2. 引入 `SavedStateHandle` 与统一 screen state，继续缩小 Activity 编排职责。
3. 增加数据库迁移 instrumentation test 和版本化备份恢复。
4. 建立 `AgentTool` 注册表与版本化卡片 schema，再扩展写工具。
5. 增加消息分支、Prompt 诊断和可量化性能报告。

## 表达边界

对外可以说“参考 AiChat、SillyTavern 等产品的功能边界和开放格式”，不能说“已复刻全部功能”。差异化应落在原生 Android、明确权限、可审计写操作、生命周期治理和可验证工程质量，而不是复制名称、设定、界面、文案、资源或源码。
