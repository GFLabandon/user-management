# MySQL 运行镜像的来源、构建与更新

当前 Compose 使用本项目构建的 `campus-counselor-mysql:8.4.11-runtime-2`。它是官方 MySQL 8.4.11 的小范围派生版本，**不是新的数据库产品或官方发布包**。官方基础镜像、Go 构建镜像、gosu 源码提交及归档摘要均固定在 [Dockerfile](../../docker/mysql/Dockerfile) 中。

## 为什么派生

2026-10-02 候选提交的远程扫描在官方镜像中发现 30 项 HIGH／CRITICAL：MySQL Shell 私有 Python 包 8 项，以及 gosu 内嵌 Go 1.24.6 标准库 22 项。官方 8.4.11 摘要尚未更新。本项目保留原门禁，不使用忽略列表，也不把组件发现数解释为已证明的攻击路径。

在隔离容器中核对 RPM 归属和依赖后确认：`mysql-shell-8.4.10-1.el9` 拥有 `/usr/lib/mysqlsh` 下的 Python 包，没有其他已安装 RPM 依赖它。数据库进程、传统 `mysql` 客户端和 `mysqldump` 均属于 `mysql-community-server-minimal-8.4.11-1.el9`；项目启动、迁移、查询和备份脚本使用这些工具，不使用 `mysqlsh`、Shell AdminAPI 或其 Python／JavaScript 管理功能。

因此通过 RPM 正常卸载整包 Shell，依赖预检失败即停止构建。卸载后仅在确认残留全为 `.pyc` 字节码和目录时清理缓存；不批量删除未知系统文件。派生版本不提供 MySQL Shell，高级 Shell 管理操作需在单独的受控工具环境中进行。

入口脚本使用 gosu 从 root 降权为 mysql，不能删除。保留 gosu 1.19 的原始源码和依赖版本，用 Go 1.27.2 静态重编译，保留构建信息和许可证；不将 Go 编译工具链带入数据库运行镜像。数据库服务端、客户端、导出工具、配置和入口脚本保持基础版本不变。

上游对 gosu 的部分 Go 标准库告警有可达性解释，本项目没有把这当作整体豁免，而是重编译后全量扫描。[官方 MySQL Dockerfile](https://github.com/docker-library/mysql/blob/01f90d87012e46cd174073bba02d64e9fc693ed3/8.4/Dockerfile.oracle)、[gosu 1.19](https://github.com/tianon/gosu/tree/6456aaa0f3c854d199d0f037f068eb97515b7513)、[gosu 安全说明](https://github.com/tianon/gosu/blob/1.19/SECURITY.md)、[Go 官方下载](https://go.dev/dl/)。

## 2026-10-10 补丁更新

`runtime-2` 保持 MySQL 8.4.11、gosu 1.19 源码及原入口，使用 Go 1.27.2 重编译 gosu，并通过 Oracle Linux 软件源安装固定版本 `openssl`／`openssl-libs` `3.5.8-2.0.1.el9_8`。构建与运行验收均检查实际安装版本。此更新处理 10 月 9 日复扫发现的 4 条 HIGH；结果与验证范围见[本轮记录](../verification/security-mysql-runtime-2026-10-10.md)。

## 构建、扫描和验收

```sh
docker build -t campus-counselor-mysql:8.4.11-runtime-2 docker/mysql
docker build -t campus-counselor-management:local .
docker build --target migration-verification -t campus-counselor-migrations:local .
python3 -B -m unittest discover -s scripts/tests -v
python3 -B scripts/verify-mysql-ci.py
python3 -B scripts/install-trivy.py
python3 -B scripts/check-security.py
```

正常部署的 `docker compose ... up --build` 会构建应用和数据库两个镜像。独立 `--no-build` 验收／恢复要求所需镜像预先存在。此标签用于本机及 CI，不表示镜像已推送到注册表；需要跨主机使用时保存、传输相应不可变镜像 ID。

扫描器从显式 `compose.yaml` 获取 app/db 镜像，不读取私有 `.env` 或额外 Compose 文件；再锁定本机镜像 ID，要求扫描报告身份相同。必须有操作系统包、应用 Java 依赖、数据库 gosu／Go 标准库的扫描覆盖；扫描错误或任何 HIGH／CRITICAL 都会失败，未修复项也不豁免。

运行验收确认数据库 PID 1 为 mysqld、UID 为 999，且传统客户端／导出工具仍为 8.4.11。迁移验收额外在官方镜像初始化一个临时卷、建立合成档案／账号／记录，再停库切换到派生镜像，比较全部数据行摘要和 Flyway 历史，检查继续写入。该测试中的账号哈希为非登录用的合成字段；真正密码哈希保留和登录在完整备份恢复演练中验证。

## 已有部署与备份

本次没有改动 V1–V4、11 张表和备份格式。已有部署升级前仍须停应用、创建并校验完整备份、保存旧镜像，并在隔离副本演练；不要直接删除或清空原数据卷。相同 8.4.11 版本的本机小样本切换通过，不替代真实实例升级评估。

恢复脚本按清单中的原始 app/db 镜像 ID 创建新项目，并核对 MySQL 版本，不会擅自把旧备份换到新镜像。旧官方镜像可能仍被备份依赖，清理 Docker 时不能仅凭无标签删除。新备份同样须保留对应派生镜像 ID。[完整恢复步骤](backup-and-restore.md)。

## 后续维护成本

这是本项目维护的派生构建，需同时跟踪官方 MySQL 基础摘要、Oracle Linux OpenSSL 补丁、Go 工具链和 gosu 源码／依赖。任一变化都要更新固定值，重建、跑完整 MySQL 验收并复扫。上游官方镜像将来能通过同一门禁时，可单独验证回归后撤掉派生层。

不通过 `pip install` 覆盖供应商私有 Python，也不通过更换数据库大版本、删扫描元数据或降低门禁解决告警。扫描通过只说明该次扫描范围内无 HIGH／CRITICAL，不等于没有中低危项、未知漏洞或已经适合公网生产运行。具体结果见[安全修复验收](../verification/security-mysql-runtime-2026-10-02.md)。
