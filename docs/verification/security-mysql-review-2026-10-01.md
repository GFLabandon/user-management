# 4D.1：MySQL 官方补丁核对

核对日期：2026-10-01。结论：**未发现新的同版本官方镜像；发布继续阻断，允许推进独立的本地功能开发。** 本次是上游核对，不是重新执行完整扫描或可利用性审计。

## 镜像与组件证据

`docker buildx imagetools inspect mysql:8.4.11` 返回的索引摘要仍为 `sha256:6ea90827b1100f8f2ae306a539f86d2c264a26ed435a2a9f75551dd5c3aeb242`，与 `compose.yaml` 完全一致。官方标签清单中的 8.4 系列仍是 8.4.11；本次没有找到可供替换并验证的新构建。

| 平台 | 平台镜像摘要 | 构建时间（UTC） |
| --- | --- | --- |
| linux/amd64 | `sha256:80f4933e3835f9dc4d35a28ec500d7986cb4414e6c6821c5461239cb7beb8995` | 2026-09-29 18:37:51 |
| linux/arm64/v8 | `sha256:ca3f0494c0f1fc86eb45f5e4786a1bb9f64d2f85b562cc74a9595046f6519a42` | 2026-09-29 18:09:35 |

官方构建源码 `01f90d87012e46cd174073bba02d64e9fc693ed3` 安装 MySQL Server 8.4.11、MySQL Shell 8.4.10 和 gosu 1.19。gosu 上游当前最新发布仍为 1.19，使用 Go 1.24.6 构建；它由入口脚本实际调用来降权启动数据库，因此不能当作无用文件直接删除。

依据：[官方标签清单](https://raw.githubusercontent.com/docker-library/official-images/master/library/mysql)、[固定版本 Dockerfile](https://github.com/docker-library/mysql/blob/01f90d87012e46cd174073bba02d64e9fc693ed3/8.4/Dockerfile.oracle)、[入口脚本](https://github.com/docker-library/mysql/blob/01f90d87012e46cd174073bba02d64e9fc693ed3/8.4/docker-entrypoint.sh)、[gosu 1.19 发布](https://github.com/tianon/gosu/releases/tag/1.19)。

## 剩余项分类和决定

下表沿用[基线远程 CI](https://github.com/GFLabandon/user-management/actions/runs/36810609072)的 amd64 报告。Trivy 0.74.0，漏洞库更新时间 `2026-10-01T01:24:14Z`。报告的 30 项是组件发现数（1 CRITICAL、29 HIGH），并非本次新扫描结果。已将组件版本、CVE、修复版本及摘要保存为[可长期查看的 JSON](security-mysql-review-2026-10-01.json)。

| 组件 | 报告版本 | 阻断项 | 用途核对与后续处理 |
| --- | --- | --- | --- |
| gosu 内嵌 Go stdlib | 1.24.6 | 22 | 入口脚本使用；上游安全说明认为部分标准库告警需结合可达性判断。本项目尚未完成对应证明，继续阻断；等待官方重建，或另案设计有版本和来源校验的构建替换 |
| cryptography | 46.0.5 | 3 | MySQL 镜像 Python 组件；不能据报告直接认定可在当前供应商镜像中安全升级，保留待处置 |
| pyOpenSSL | 25.3.0 | 1 | 同上，需核对与 cryptography 及供应商工具的兼容性 |
| urllib3 | 2.6.3 | 4 | 同上，10 月 1 日报告较前一报告增加两项；不添加忽略规则 |

Python 组件的原报告目标为 `Python`，官方 Dockerfile 安装了 MySQL Shell。当前证据不足以逐一确认这些包的 RPM 归属、全部运行调用或可达性，不把“本项目未主动使用 Python”当作无风险依据。后续若评估派生镜像，需要逐包核对文件／RPM 归属与依赖，验证初始化、降权、停止重启、迁移和恢复，并完整重扫；本轮没有建立足以接受该维护成本的依据。

没有改动镜像、依赖、扫描阈值、忽略列表或备份格式；没有声称安全门禁通过。下一次有新官方摘要时，按原计划独立验证候选，执行完整 MySQL 运行／迁移／恢复及安全扫描。

## Actions 运行时提示

工作流当前使用 checkout/setup-java/upload-artifact v4。官方已经提供 Node 24 版本，checkout v5、setup-java v5 明确要求运行器至少为 2.327.1；upload-artifact v5 也已切换到 Node 24；升级可独立进行，但需要远程 runner 复验。本轮只记录这项 CI 维护待办，不把未经远程运行的升级并入账号选择改动。

依据：[checkout v5](https://github.com/actions/checkout/releases/tag/v5.0.0)、[setup-java v5](https://github.com/actions/setup-java/releases/tag/v5.0.0)、[upload-artifact v5](https://github.com/actions/upload-artifact/releases/tag/v5.0.0)。
