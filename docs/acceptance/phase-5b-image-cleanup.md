# 5B 图片失败场景与清理可靠性

验收日期：2026-10-02。基线 `0580c8b`，分支 `codex/image-cleanup`。以下为本机 macOS arm64 和 Linux arm64 容器结果，未推送、合并或执行新远程 CI；本轮不修改依赖、镜像固定摘要、数据库迁移或备份格式。完整脱敏结果见[JSON 摘要](phase-5b-image-cleanup-result.json)。

## 结果与改动

原 Controller 在服务返回后直接删除旧图，失败时直接删除新图，没有在删除时统一协调已提交引用。现由 `ImageLifecycle` 在事务完成回调中处理：确认提交后清理旧图，确认回滚后清理新图；结果不明时保留。单实例锁覆盖档案创建／更新、事务完成及引用检查／删除；独立只读事务查询当前档案和旧 `users` 表，任何引用存在都保留图片。

存储层拒绝目录穿越、非允许文件名、目录和符号链接；部分写入失败只清理自己已经创建的文件，不能删除 `CREATE_NEW` 失败前已存在的文件。清理失败记录受控文件名、结果及触发点，不暴露完整路径、SQL 或堆栈。旧图删除失败不改变已提交保存的成功结果。

新增 `scripts/cleanup-images.py`，默认只预览。真正删除要求已停止应用、私有健康数据库、独占卷、单文件名、同项目有效备份和当前文件摘要一致；读锁期间重新查引用再删除，失败可重试，已不存在时返回幂等结果。命令见[图片清理指南](../guides/image-cleanup.md)。

## 验证

| 检查 | 结果 | 证据范围 |
| --- | --- | --- |
| 本机 `./mvnw --batch-mode --no-transfer-progress clean verify` | 111 项，0 失败／错误／跳过 | 既有业务回归和新增文件／事务故障、引用竞争 |
| Docker 应用镜像构建 | 镜像内同样 111 项通过 | 最终 Linux arm64 应用镜像 |
| `python3 -B -m unittest discover -s scripts/tests -v` | 29 项通过 | 维护保护与新增清理工具检查 |
| `verify-compose.py` | 13 组通过 | 真实 MySQL、HTTP multipart、共享引用和失败上传 |
| `verify-migrations.py` | 5 组通过 | 空库、缺失映射、旧库迁移、V3 升级、未知非空库拒绝 |
| `verify-recovery.py` | 10 组通过 | 11 张表、原备份格式、离线清理保护与恢复 |

Java 故障测试实际使用事务及临时文件，注入部分输出失败、审计数据库写入失败、删除失败、引用查询失败；确认数据库回滚后旧引用仍在且新文件被清理。外层事务尚未提交时旧图不删除。未知事务结果通过同步回调注入验证保留策略，没有把它描述为真实数据库网络分区实验。

共享图片测试覆盖当前档案以及旧迁移表引用；两个竞争测试分别控制“删除先获得锁”和“新引用先获得锁”：前者拒绝随后引用已不存在文件，后者保留已有新引用的文件。过期 Web 表单测试在真实服务事务边界执行，避免测试外层事务掩盖提交时机。

真实 MySQL 运行检查通过 HTTP 上传替换共享旧图；第一份档案替换后旧图仍可读，最后一个引用替换后旧图被删除。过期表单和重复工号数据库拒绝均返回 409，上传目录没有多出失败的新图，胜出的引用不受影响。

恢复演练拒绝运行中的应用、当前／历史引用、路径穿越、缺少备份和备份后变化的文件。通过上传目录权限模拟真实删除失败，修复权限后重试成功，再次执行返回已不存在；原始备份保持完整，并在全新项目恢复出删除前的图片。恢复后实际登录、查档案、编辑和读图通过。

```sh
./mvnw --batch-mode --no-transfer-progress clean verify
python3 -B -m unittest discover -s scripts/tests -v
docker compose --env-file <验收环境文件> build app
docker build --target migration-verification -t campus-counselor-migrations:local .
python3 -B scripts/verify-mysql-ci.py
```

验收入口内部使用随机隔离项目；凭据文件由本机验收创建并仅供当次使用，不提交真实环境文件。报告位于被忽略的 `target/compose-verification/counselor-verify-8ef3757e2d7d/`、`target/migration-verification/counselor-migration-84abbea3d64b/`、`target/recovery-verification/counselor-recovery-b767b0bae9/`。三套脚本退出码均为 0，本轮随机容器、网络和命名卷已清理；没有清理其他项目。

最终应用镜像 ID：`sha256:035064e11bc8050c5ba6577e3260564debc71780b7d85c2cc68db419bf0ca738`。JSON 中的基线提交表示构建时工作区 HEAD，代码包含本次尚未提交的修改，不能将它冒充镜像源码提交。构建后只修改了服务注释与文档，未改变运行代码。

## 边界

这是单实例下的同步协调与人工维护入口，没有持久化队列、定时任务、跨重启自动补偿或多实例锁。清理查询失败、连接不足、进程退出或事务结果不明都可能留下文件；日志和离线扫描用于后续核查。创建／更新串行经过同一个锁，不声称吞吐量提升。

应用内竞争已验证；维护窗口要求操作者暂停直接数据库、Docker 和文件操作。数据库读锁不能阻止宿主机管理员直接改卷，文件与数据库也不是分布式原子事务。删除前备份可以恢复，但不表示已实现生产 RTO／RPO。

V1–V4、11 张表和 `scripts/maintenance.py` 保持不变，无需 V5。没有改 UI，因此本阶段使用真实 HTTP、SQL 与文件结果验收，未新增浏览器截图。安全扫描未重跑，基线 MySQL 镜像发布阻断仍保留。下一阶段可按计划先建立有边界的性能基线，再决定是否需要索引或查询优化。
