# 图片清理与失败重试（5B）

适用于当前单实例 Compose、独占命名卷、私有 MySQL 和 Flyway V1–V4。正常建档／编辑通过事务完成回调清理图片：已确认回滚时尝试删除新图，提交成功后尝试删除被替换的旧图。删除前重新查询当前档案及旧 `users` 表的引用；仍有引用就保留。

同一应用进程的档案创建／更新共用一个锁，覆盖引用检查、事务完成和删除，避免检查后另一请求重新引用文件。清理查询使用独立只读事务读取已提交状态。文件与数据库仍不是原子事务；进程崩溃、提交结果不明、查询或删除失败时可能留下孤立文件。没有持久化任务表、自动重试或多实例协调；本阶段保留 11 张表、V1–V4 和原备份格式。

## 定位失败

应用日志使用受控文件名，不记录完整路径、SQL 或堆栈：

```text
image_cleanup file=<文件名> result=<结果> trigger=<触发点>
```

`deleted`、`absent`、`referenced` 分别表示已删除、已不存在、仍被引用。`failed`、`reference_check_failed`、`transaction_unknown`、`retained_for_review` 和无效路径结果需要核查；它们不是文件可以删除的证明。部分写入后删除失败也使用该格式。旧图清理失败不会把已经提交的档案保存变成失败响应。

进程退出前来不及记录日志的孤立文件，可在维护窗口通过下面的候选清单发现。不要根据日志直接执行 `rm`，也不要以图片目前无法经网页读取为删除依据：旧迁移表的引用同样受保护。

## 停机、备份、预览

以下 `counselor`、环境文件和备份目录是示例，替换为实际运行项目。不要在维护窗口内直接修改数据库、头像卷或用另一终端重启应用。脚本使用项目文件锁和数据库全局读锁，但无法约束宿主机管理员绕过工具直接操作文件或 Docker。

按[备份恢复指南](backup-and-restore.md)准备环境文件及受控备份目录，创建新的备份。该命令会停止应用；成功后保持停止：

```sh
./scripts/backup.sh --project counselor --env-file .env.compose \
  --output backups/counselor-before-image-cleanup-20261002
python3 -B scripts/maintenance.py verify \
  --backup backups/counselor-before-image-cleanup-20261002

# 仅列出未被引用的候选文件，不删除。
python3 -B scripts/cleanup-images.py --project counselor --env-file .env.compose

# 只核查一张图片，输出当前大小与 SHA-256。
python3 -B scripts/cleanup-images.py --project counselor --env-file .env.compose \
  --file example.png
```

脚本要求应用已经停止、数据库健康且未开放宿主机端口、卷由项目独占。任一被引用图片缺失、存在链接或异常归档条目时拒绝清理，应先排查。候选清单仅表示当前未引用；仍需结合失败记录确认维护目的。文件参数只能是允许的普通 JPG／JPEG／PNG 文件名，不能传 `/uploads/` 前缀、目录或通配符。

## 逐个重试与恢复

确认后一次只处理一个文件：

```sh
python3 -B scripts/cleanup-images.py --project counselor --env-file .env.compose \
  --file example.png --apply \
  --backup backups/counselor-before-image-cleanup-20261002
```

只有同一项目的有效备份包含该文件，且当前大小和 SHA-256 与备份一致、当前仍无引用时才删除。引用来自当前档案和旧 `users` 表；清理时持有数据库读锁。删除工具容器无网络、以非 root 用户运行，删除前再次检查文件类型及摘要。

成功输出 `result=deleted`；再次执行会输出 `result=absent`，不会误删其他文件。权限等故障会返回非零退出码，保持应用停止；先修复实际故障，再用同一个文件参数重试。备份中的图片不会被删除。来自其他项目的备份不能授权本项目删除；恢复到新项目后，如需清理，应先为该新项目另做备份。

需要恢复时使用现有[空项目恢复流程](backup-and-restore.md#恢复到新的空环境)，从删除前的完整备份恢复到新项目并核对图片和档案。不要将旧 SQL 或图片包直接覆盖到正在运行的实例。

检查处理结果后，由操作者明确启动原应用：

```sh
docker compose --project-name counselor --env-file .env.compose start app
docker compose --project-name counselor --env-file .env.compose ps app
```

等待 healthy，重新登录并检查档案详情、现有图片和编辑。脚本没有自动启动、批量删除或定时清理入口。验收证据见[5B 验收](../acceptance/phase-5b-image-cleanup.md)。
