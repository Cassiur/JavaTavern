# 专题 05：SQLite 持久化

## 数据库拆分

- `characters.db`：角色、角色 Prompt、来源哈希和世界书条目。
- `java_tavern.db`：消息、Agent 审计和 FTS 虚拟表。

角色卡导入在事务中写角色和全部世界书；`source_hash` 唯一约束避免完全相同文件重复导入。

## 消息数据库 v1→v5

1. v2：增加 `kind`、`title`，支持 Agent 卡片。
2. v3：增加 action token/type/state 和 `agent_audit`。
3. v4：增加图片路径和 MIME。
4. v5：增加 `messages_fts`、插入/删除/更新触发器并回填旧消息。

每次 migration 都保留旧表和旧数据。模拟器上的已有会话已经逐级升级并验证卡片、图片和搜索仍存在。

## 分页

首屏 SQL 按 `id DESC LIMIT 60` 读取后在内存反转。向上滚动执行：

```sql
SELECT ... FROM messages
WHERE character_id = ? AND id < ?
ORDER BY id DESC
LIMIT 40;
```

这是键集分页。相比 `OFFSET 10000`，数据库不需要每次先跳过前面大量记录。

## FTS

写消息后触发器同步 `messages_fts`。搜索先用 FTS phrase 查询；未命中或表达式异常时退回 `LIKE`，保证中文和特殊字符仍有可用结果。点击结果后按消息 ID 读取前后各 20 条上下文。

## 当前问题

- 手写 migration 缺 instrumentation test。
- 两个数据库没有统一备份事务。
- 消息状态、摘要和分支会继续增加 schema 复杂度。

下一阶段迁移 Room，但迁移不是为了“看起来高级”，而是获得 DAO、migration test、事务边界和可观察数据流。

## 口述题

解释键集分页为什么比大 OFFSET 更稳定，以及它为什么依赖可排序且不重复的游标字段。
