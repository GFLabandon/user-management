# 4D：MySQL CI、安全扫描与发布流程

日期：2026-09-30。分支 `codex/ci-release-verification`，从 4C 的 `bf8a300` 继续。本阶段不修改业务 Java、V1–V4 或数据库结构，不操作已有数据库／数据卷，不开放公网。

## 交付

- GitHub Actions 分成 Java／Python、MySQL、漏洞扫描三个任务，支持 PR、main push 和手动触发，设置任务超时、并发取消和报告保留期限。
- `scripts/verify-mysql-ci.py` 复用运行与恢复脚本，新增 `verify-migrations.py`；失败不会跳过其他套件的结果收集，取消时触发各脚本清理。
- Dockerfile 的 `migration-verification` 目标从应用 JAR 提取实际 Flyway／驱动及迁移类，编译测试程序；默认最终目标仍是原来的非 root JRE 应用镜像。
- 固定校验值安装 Trivy 0.74.0，扫描应用镜像内 Java 依赖、操作系统及 MySQL 镜像，保留发现项和数据库时间。HIGH／CRITICAL 或扫描不完整时失败，不自动豁免。
- 新增[发布与回退指南](../guides/release.md)，区分本机验证、远程 CI 与实际发布。

## 本机验证

环境：macOS arm64、Temurin 17.0.20+8、Docker 29.7.2、MySQL 8.4.11、Boot 4.0.8、Flyway 11.14.1。初次应用镜像构建沿用缓存。安全补丁后已重新构建 Linux arm64 镜像，本机与容器构建各执行 75 项 Java 测试并通过；Tomcat 11.0.26、Jackson 2.21.7／3.1.7 与 OpenSSL 补丁见安全记录。

| 验证 | 结果 |
| --- | --- |
| `./mvnw --batch-mode --no-transfer-progress verify` | 75 项通过，0 失败／错误／跳过 |
| Python 安全与 CI 失败处理测试 | 18 项通过 |
| actionlint 1.7.12 | CI YAML、表达式与动作参数检查通过 |
| 默认应用与迁移测试目标构建 | 通过 |
| 运行验收 | 10 组通过 |
| 真实 MySQL 迁移验收 | 5 组通过 |
| 协调备份与恢复 | 8 组通过 |

CI 同入口本机摘要 `target/mysql-ci-summary.json` 显示三个脚本均为 exit 0，补丁后最终一轮分别耗时 137.1、39.1、45.6 秒。该时长来自小样本本机环境，不是性能或恢复服务承诺。最终三套摘要分别来自 `counselor-verify-70a34901ca26`、`counselor-migration-ba9946ef1cc6`、`counselor-recovery-84f326736d`。详细结果位于 `target/compose-verification/`、`migration-verification/`、`recovery-verification/`，均不提交构建产物。

新增迁移验收逐场景使用全新 Compose 数据库：

1. 空库完成 V1–V4，没有隐式 demo 或账号，重复迁移无变化。
2. 有一条合法映射、一条缺失映射时，整体拒绝 V3；档案和状态历史零复制，旧资料及关系保留，不继续 V4，不自动 repair。
3. 完整映射后保留原 ID、中文姓名、院系、照片路径、备注和旧角色关系，写入迁移历史，自增 ID 前移，重复运行无新迁移。
4. 已有 V3 档案只执行 V4，档案保留、账号仍为空、管理员保护行初始化。
5. 未版本化的非空库被拒绝，不自动 baseline、不删除原数据。

普通取消转为脚本退出并执行 finally 清理；强制杀进程、Docker 失联或宿主机退出不能保证清理完成。迁移失败日志仅在本机保留；CI 上传白名单中的 JSON 摘要，不上传 SQL／环境文件／头像备份。

## 安全扫描与 Flyway 范围

初次安全扫描发现应用 7 个、MySQL 64 个 HIGH／CRITICAL 组件项；独立补丁后应用归零、MySQL 降至 28 个，最终安全门禁仍失败，不能发布。实际扫描结果、工具／数据库时间和待处理项见[安全核对记录](../verification/security-2026-09-30.md)。安全任务红灯是发布阻断，不能被功能验收成功覆盖。

2026-09-30 核对：Flyway 官方 MySQL 页面仍列验证版本 5.7、8.0、9.4；H2 页面列 1.2、2.0。本项目当前 MySQL 8.4.11／H2 2.5.250 仍超出该列举范围，运行日志中的提示保留。实际迁移及回归通过提供本项目用例证据，不等于供应商完整认证；本轮没有为消除日志而降低数据库或盲目替换 Flyway。

依据：[MySQL 文档](https://documentation.red-gate.com/flyway/reference/database-driver-reference/mysql)、[H2 文档](https://documentation.red-gate.com/flyway/reference/database-driver-reference/h2)。

本轮所有随机验收容器、卷与网络已清理；原有四个其他项目容器继续保持停止，未动其数据卷。Docker 开始时未运行，验收结束后恢复关闭状态。

## 发布状态

上述验收执行时仅有本地提交，尚未运行远程 GitHub Actions 或发布。2026-09-30 后续已将阶段提交快进整合到 main；此历史验收记录不代替推送后的远程 CI 结果。Linux amd64 托管 runner 的完整结果仍需候选提交实际运行确认。HTTPS、登录限流、多实例会话、告警和异地备份不在本次交付内。

## 2026-10-01：推送后的远程兼容性检查

`main` 已推送至 `53c9895`，本地与远程没有分叉、冲突索引或残留冲突标记；已整合的四个阶段分支已删除，提交历史保留。

首次[远程 CI](https://github.com/GFLabandon/user-management/actions/runs/36723487418)的 Java／Python 和镜像构建通过；MySQL 运行 10 组、迁移 5 组通过，恢复完成数据／图片校验后，在启动应用命令处失败。该 Ubuntu runner 使用 Compose 2.38.2，官方 `start` 实现没有 `--wait`／`--wait-timeout`，与本机版本存在参数兼容差异。依据：[runner 软件清单](https://github.com/actions/runner-images/blob/ubuntu24/20260927.320/images/ubuntu/Ubuntu2404-Readme.md)、[Compose 2.38.2 源码](https://github.com/docker/compose/blob/v2.38.2/cmd/compose/start.go)。

修复改为 `start app` 后独立轮询既有容器的健康状态，保留 120 秒超时；异常退出、重启、无健康检查或 unhealthy 均明确失败。恢复验收额外确认容器 ID 和镜像 ID 未变，避免通过重建绕过固定镜像要求。新增正常就绪、异常状态和超时测试，本机共 21 项 Python 测试通过，actionlint 通过；远程复验结果以该修复提交的 Actions 记录为准。

首次远程安全报告确认应用没有 HIGH／CRITICAL，MySQL 仍有 28 个阻断项，扫描本身执行成功。该失败与 Git 冲突及恢复启动参数问题无关，安全门禁保持原标准。
