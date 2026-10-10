# MySQL 运行镜像补丁更新

日期：2026-10-10。对应 PR #5 的补丁提交；本轮不修改 MySQL 服务端版本、数据库迁移或备份格式。

## 触发依据

[首次 CI](https://github.com/GFLabandon/user-management/actions/runs/37942906020) 的 Java 与 MySQL 验收通过，安全扫描失败。2026-10-09 的 Trivy 数据库在 `runtime-1` 发现 4 条 HIGH（3 个 CVE）；应用镜像阻断项为 0：

| 组件 | CVE | 原版本 | 补丁版本 |
| --- | --- | --- | --- |
| openssl、openssl-libs | CVE-2026-84782 | 3.5.8-1.0.1.el9_8 | 3.5.8-2.0.1.el9_8 |
| gosu 内嵌 Go 标准库 | CVE-2026-78667、CVE-2026-97031 | 1.27.1 | 1.27.2 |

Go 官方漏洞记录：[GO-2026-6609](https://pkg.go.dev/vuln/GO-2026-6609)、[GO-2026-6607](https://pkg.go.dev/vuln/GO-2026-6607)；[Go 发布记录](https://go.dev/doc/devel/release)。Oracle 补丁版本来自本次扫描报告及供应商软件源，安装时保留包签名检查。发现数不等于已证实的可利用路径。

## 变更

- 新标签 `campus-counselor-mysql:8.4.11-runtime-2`，保留固定的官方 MySQL 8.4.11 基础摘要。
- 固定 Go 1.27.2 构建镜像摘要 `sha256:5cf287a799e6b94384bad13d16b14904c531f51ba65792237e122ce42b392f61`，gosu 源码、归档校验及依赖不变。
- 经包管理器安装两个固定版本 OpenSSL RPM，构建阶段检查版本并清理软件源缓存。运行验收再次检查实际版本，以及 gosu 降权、数据库版本和 Shell 移除。
- Compose、CI、验收和发布命令统一使用新标签。旧备份仍按其清单中的镜像 ID 恢复，不覆盖旧镜像或数据卷。
- 继续阻断任何 HIGH／CRITICAL，不加忽略项或放宽扫描覆盖。

## 验证状态

补丁提交 `5199082` 的 Java／Python 检查及镜像安全复扫通过：[CI](https://github.com/GFLabandon/user-management/actions/runs/38057017199)、[扫描摘要](security-mysql-runtime-2026-10-10-result.json)。应用与数据库镜像 HIGH／CRITICAL 均为 0，扫描报告确认 gosu 的 Go 版本为 1.27.2。完整 MySQL 运行、迁移、恢复检查和最终提交状态以 [PR #5 Checks](https://github.com/GFLabandon/user-management/pull/5/checks) 为准。

原功能提交已通过 121 项 Java、32 项 Python、本地浏览器验证及首次远程 MySQL 验收。本机 Docker 守护进程未运行，本轮补丁镜像验证在 GitHub Actions 的隔离环境执行。扫描结果仅对应所列镜像 ID 和数据库时间。
