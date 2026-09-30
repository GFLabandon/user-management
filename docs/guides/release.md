# 本机演示版本的发布与回退

本流程适用于当前单实例 Compose。它不自动发布镜像、开放公网或切换服务入口；实际部署需先通过下述检查。发布日期、提交和镜像摘要必须取自当次结果，不复用历史报告冒充当前验收。

## 1. 确定候选版本

确认工作区干净，记录 `git rev-parse HEAD`。阅读对应阶段验收、依赖扫描和待处理项。V1–V4 不得改写；未来结构变化追加版本，同时扩展备份脚本支持范围和恢复演练。

在隔离环境构建两个镜像：

```sh
./mvnw --batch-mode --no-transfer-progress verify
python3 -B -m unittest discover -s scripts/tests -v
docker build -t campus-counselor-management:local .
docker build --target migration-verification -t campus-counselor-migrations:local .
python3 -B scripts/verify-mysql-ci.py
python3 -B scripts/install-trivy.py
python3 -B scripts/check-security.py
```

迁移验收镜像只用于测试，内含 JDK 与验证程序，不用于运行 Web 应用。它使用应用打包出来的 Flyway、MySQL 驱动与原始 V1–V4，因此无需另装或另配一套 Flyway CLI。

`verify-mysql-ci.py` 顺序执行运行、迁移和恢复三套验收，即使某套失败也继续收集其余结果；每套有超时和资源清理。普通取消会触发清理，进程被强制杀死或 Docker 中断仍可能遗留资源，应按摘要中的精确项目名检查；不要运行全局 prune，也不要按宽泛前缀批量删除资源。

## 2. 检查 CI 与扫描证据

CI 在 main push、PR 或手动触发时分开运行三项任务：Java／Python、真实 MySQL、安全扫描。只保留必要报告 7 天，不上传 SQL、头像归档或环境文件。

| 证据 | 内容 |
| --- | --- |
| Java 测试报告 | `target/surefire-reports/TEST-*.xml` |
| MySQL 汇总 | `target/mysql-ci-summary.json`，各套 `*-verification/*/result.json` |
| 扫描报告 | `target/security/application.json`、`mysql.json`、`java-dependencies.json`、`summary.json` |

Trivy 0.74.0 的安装包固定 SHA-256；脚本扫描实际应用镜像中的 Java 依赖和操作系统包，以及 Compose 指定摘要的 MySQL 镜像。扫描数据库需要联网更新，首次 Java 数据库下载较大。报告保留扫描器版本、数据库更新时间、镜像身份、受影响版本和修复版本。

HIGH／CRITICAL（包括暂无修复的条目）、扫描错误、缺少 Java／系统包扫描结果均导致检查失败。没有忽略清单或自动豁免。先核对上游公告、受影响组件及可用修复，再单独升级、回归和重扫；不能通过降低严重性门槛或忽略扫描错误使任务变绿。扫描通过也不能覆盖应用逻辑、配置和未知漏洞的风险。

本机执行通过与 GitHub 托管 runner 通过是不同证据。合入／发布前核对候选提交在远程的三项任务状态和报告；未运行的远程 CI 不记为通过。GitHub artifact 行为参考[官方文档](https://docs.github.com/en/actions/tutorials/store-and-share-data)，扫描范围参考 [Trivy 镜像扫描](https://trivy.dev/docs/latest/target/container_image/)。

## 3. 准备配置与一致备份

新部署按[部署指南](deployment.md)准备权限为 600 的环境文件，使用独立数据库密码，空账号库才配置首次管理员。只绑定本机回环端口；首次启动后移除初始化密码。不得把 demo 账号、环境文件、数据库备份或个人资料加入镜像和 Git。

升级已有实例前安排维护窗口，按[备份恢复指南](backup-and-restore.md)停止业务写入并同时备份数据库、头像。用 `maintenance.py verify` 验证，另行将可信备份和原应用／数据库镜像保存到受控位置，并在全新环境实际恢复。仅有导出文件或容器卷持久化不算恢复验证。

旧版／V2／V3 数据库不属于 4C 脚本的 V1–V4 完整库备份范围，先按[旧库迁移指南](counselor-migration.md)处理副本，不强行套用当前备份脚本或自动 baseline。

## 4. 启动和检查

记录应用与数据库不可变镜像 ID。正式候选使用受控 Compose 覆盖文件固定已验证镜像，避免之后 `up` 时使用被重打的 `:local` 标签。禁止将镜像构建、数据库升级和未知业务改动混成一次无记录的替换。

检查 readiness 与 liveness、管理员及只读登录、中文检索、档案／头像、写入和状态历史、权限拒绝、操作记录及日志。确认数据卷、账号哈希、首次凭据移除后的重启行为。新的应用容器会丢失内存 Session，使用者需要重新登录。

若恢复脚本已创建容器，首次使用 `docker compose ... start --wait app` 启动，遵守其固定镜像要求。网页验证成功后才考虑切换入口；当前仓库不自动完成该动作。

## 5. 失败时回退

先停止新实例写入，保留失败摘要和请求编号。只有确认结构与数据兼容时才考虑单独回退应用；否则使用维护前的数据库与头像备份，在另一个全新项目中恢复原镜像并重新核对网页功能。

不要覆盖原项目、删除已有卷、修改已执行迁移、盲目执行 repair 或只切 Git 提交。版本回退不会撤销已经发生的业务写入；维护窗口结束后若出现新数据，需要明确核对和补偿，不能声称恢复旧备份不会丢数据。

HTTPS、可信代理、登录限流、告警与持续备份保管仍属后续范围。上述本机流程通过不等于具备公网生产发布条件。
