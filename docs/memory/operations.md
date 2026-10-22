# Memory 运维说明（M1）

> 本文随 M1 各任务补充；恢复手册与故障演练在 Task 10 完成。

## 附件存储与保留

- 附件保存在本地目录 `${app.upload.dir}/{conversationId}/`。目录名就是会话 ID（小写规范 UUID），同一会话的附件在同一目录，不同会话不会共用目录。
- **M1 不承诺附件原件可永久恢复。** `UploadCleanupTask` 在启动时和此后每小时运行一次，删除最后修改时间早于 `app.upload.ttl-hours`（默认 24 小时）的会话目录。会话消息默认永久保留：消息正文和其中提到的文件名会保留，附件原件可能已被清理。
- 清理任务只处理名称符合会话 ID 规则的目录。其他目录（例如 M1 之前按 sanitize 规则创建的 `conv-1`、`a_b` 等）不会被自动删除，需要运维确认后手动清理。M1 之前的这类旧目录也无法再通过下载接口访问。
- 附件不在 M1 的数据库备份与恢复范围内（RPO/RTO 只覆盖会话、消息与执行记录）。归档与 MinIO 存储见 spec 14.1。

## 会话删除清理

- `DELETE /api/conversations/{id}` 只写删除标记、使执行权失效，并在 `memory_jobs` 登记 `DELETE_CONVERSATION` 任务。物理清理由 `MemoryMaintenance` 执行，它在启动时以及此后每隔 `app.memory.cleanup-scan-interval`（默认 30 秒）扫描一次；`app.memory.recovery-enabled=false` 时与恢复扫描一起关闭。
- 清理内容：`chat_messages`、`chat_tool_events`、Redis 中该会话的缓存条目（替换为删除标记），以及 `${app.upload.dir}/{conversationId}/`。清理前会再次确认该会话已标记删除，不会清理仍在使用的会话。
- 失败重试：失败的任务回到 PENDING，退避时间从 30 秒开始指数增长，上限 30 分钟，不限次数。`last_error` 只记录异常类型，不保存消息或路径，原始异常只写服务端日志（`memory.delete cleanup failed`）。进程在清理中途退出时，任务停留在 RUNNING，5 分钟租约到期后会被重新领取。
- 排查：`SELECT status, attempts, last_error, available_at FROM memory_jobs WHERE job_type = 'DELETE_CONVERSATION' AND status <> 'DONE'`。长期 PENDING 且 attempts 持续增长，通常是附件目录权限或 Redis 不可用。
- 删除标记（`sessions.deleted_at`）与已完成的任务记录永久保留，不要手动清除，否则同一会话 ID 可能被重新创建，或无法确认清理已完成。
