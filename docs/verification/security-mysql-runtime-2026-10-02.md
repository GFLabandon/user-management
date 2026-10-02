# 4D.2：MySQL 派生运行镜像安全修复

核验日期：2026-10-02。开发基线 `6253082`，候选成果汇集于 [PR #1](https://github.com/GFLabandon/user-management/pull/1)。本记录描述本机 Linux arm64 实测，远程 Linux amd64 与合并状态以 PR 最新 CI 为准。[可保存的验收摘要](security-mysql-runtime-2026-10-02.json)。

## 变化及依据

官方 `mysql:8.4.11` 摘要仍为 `sha256:6ea90827b1100f8f2ae306a539f86d2c264a26ed435a2a9f75551dd5c3aeb242`。此前 [CI 36990866511](https://github.com/GFLabandon/user-management/actions/runs/36990866511) 在 amd64 官方镜像发现 30 项阻断：Shell 私有 Python 包 8 项，gosu 内嵌 Go 标准库 22 项。本轮不更换 MySQL 版本，采用固定来源的派生镜像。

| 改动 | 证据和保护 |
| --- | --- |
| 正常卸载 `mysql-shell` RPM | 已核对受影响 Python 包的 RPM 归属、无其他包依赖 Shell；先做卸载依赖预检，不使用强制忽略依赖 |
| 清理卸载后 Shell 私有目录残留 | 实测只剩运行时生成的 `.pyc`；仅确认全部为该类文件及目录后清理，遇到其他文件即停止构建 |
| 重编译 gosu 1.19 | 源码提交 `6456aaa0f3c854d199d0f037f068eb97515b7513`，归档 SHA-256 `33d7537d588ea49458b9509bcf4554bdf5ceacc66da71e5caa1058ea3b689c3b`；Go 1.27.1 镜像固定摘要；原 go.mod/go.sum 依赖不变并执行校验 |
| 保留数据库实际能力 | `mysqld`、`mysql`、`mysqldump`、入口脚本及 `/etc/my.cnf` 与官方基础镜像逐字节摘要一致；PID 1 实测为 UID 999 的 mysqld |
| 绑定实际扫描对象 | 从显式 Compose 配置读取镜像，再按本机镜像 ID 扫描并核对报告身份；不读取私有 env；强制 OS／Java／gosu 标准库扫描覆盖 |

维护方式和取舍见[镜像指南](../guides/mysql-runtime-image.md)。此方案不再提供 `mysqlsh`／AdminAPI／Shell Python 和 JavaScript 功能，项目的 SQL、Flyway、传统导出和恢复流程保持可用。原官方镜像和 Go／源码固定值来自[官方构建](https://github.com/docker-library/mysql/blob/01f90d87012e46cd174073bba02d64e9fc693ed3/8.4/Dockerfile.oracle)、[gosu 源码](https://github.com/tianon/gosu/tree/6456aaa0f3c854d199d0f037f068eb97515b7513)和 [Go 发布](https://go.dev/dl/)。

## 本机结果

| 验证 | 实测结果 |
| --- | --- |
| Java 回归 | 111 项，0 失败／错误／跳过 |
| Python 维护与扫描保护 | 32 项通过 |
| 真实 MySQL 运行 | 14 组通过，新增派生镜像身份、Shell 缺失及实际降权验证 |
| 真实 MySQL 迁移／镜像切换 | 6 组通过；原 5 组加官方基础镜像数据卷切换 |
| 真实 MySQL 备份恢复 | 10 组通过，包含登录、密码哈希、全表数据、图片和离线重试 |
| Trivy 0.74.0 | 两个实际运行镜像 HIGH／CRITICAL 均为 0；门禁通过 |
| 工作流与文档 | actionlint、语法、链接及差异检查通过 |

完整三套验收入口先通过 14／5／10 组，随后新增镜像切换用例并独立重跑迁移套件，6 组全部通过；没有将尚未执行的新增用例算入第一次运行。镜像切换在原官方镜像创建合成数据，停止数据库后复用同一临时卷启动派生镜像，核对全部数据行导出摘要、Flyway 历史及继续写入。没有接触已有用户部署或其他项目数据。

隔离项目分别为 `counselor-verify-659aaeb24b21`、`counselor-migration-6c9171cd3fc8`、`counselor-migration-d5aa411cc1b7`、`counselor-recovery-b1786d54dd`。脚本正常退出并执行其项目清理；中断后复核时 Docker 已停止响应，未为重复盘点重新启动 Docker。

扫描时间：2026-10-02 17:58（Asia/Shanghai）。漏洞库更新于同日 06:55 UTC；Java 数据库仍在扫描器有效缓存期内，具体元数据保留在 JSON。应用镜像 `sha256:035064e11bc8050c5ba6577e3260564debc71780b7d85c2cc68db419bf0ca738`；派生 MySQL 镜像 `sha256:dd9c43cd775892060f08c7e49aa75f33a99cac4a6ce7e173c722d2ae2f290da8`。运行代码未变，应用镜像沿用 5B 已验证产物；远程会从候选提交重新构建。

## 保留的边界

HIGH／CRITICAL 为 0 不等于没有发现项：本机应用为 52 MEDIUM、16 LOW；派生 MySQL 为 3 MEDIUM（gawk）和 1 UNKNOWN（golang.org/x/sys）。UNKNOWN 项为 CVE-2026-39824，官方报告限定 `golang.org/x/sys/windows.NewNTUnicodeString`；当前镜像为 Linux。没有删除此报告项或新增忽略规则。[Go 官方漏洞条目](https://pkg.go.dev/vuln/GO-2026-5024)。

保持原有“所有 HIGH／CRITICAL 及扫描不完整均失败”的门禁。派生层增加了维护责任：后续必须跟踪官方 MySQL、Go 和 gosu 变更，更新固定版本后重新验收；官方镜像能通过同一门禁时再评估回归。不是对 MySQL／gosu 全部漏洞可达性的认证，也没有证明生产容量、HTTPS 或真实学校部署。

V1–V4、11 张表和备份格式未改变，恢复仍使用备份记录的原始镜像 ID。Actions Node 20 运行时弃用提示继续作为独立维护项，不与此次镜像修复混合。
