# 合并后维护与空间清理

日期：2026-10-02。基线为 PR #1 合并提交 `97e309d`；[合并后的完整 CI](https://github.com/GFLabandon/user-management/actions/runs/37022219263) 已通过快速测试、MySQL 和安全扫描三个任务。

## 空间清理

- Docker 构建缓存执行 `docker builder prune -f --filter until=1h`，回收 565.9 MB；缓存从 127 项／2.229 GB 降至 101 项／1.663 GB。
- 删除 Git 已忽略的 `target/trivy-cache/`，文件逻辑大小共 3,036,909,868 字节；它只含漏洞数据库和扫描缓存。下次本机扫描会重新下载数据库。
- `target/` 的 `du -sh` 从约 3.1 GB 降到 259 MB。保留扫描工具、测试报告、脱敏验收记录；没有删除上传文件或备份。
- 清理前后 Docker 均为 12 个镜像、4 个停止容器、6 个卷。保留应用、派生数据库、官方固定摘要镜像、验收工具及其他项目资源；没有执行容器／数据卷清空。

Docker 逻辑回收空间不等于 macOS 虚拟磁盘文件立即缩小。缓存会随后续构建重新增长。

## Actions 运行时

三个 Action 均采用官方 Node 24 版本并固定完整提交 SHA，版本注释便于后续核对：

| Action | 版本 | 固定提交 |
| --- | --- | --- |
| checkout | [v7.0.1](https://github.com/actions/checkout/releases/tag/v7.0.1) | `3d3c42e5aac5ba805825da76410c181273ba90b1` |
| setup-java | [v6.0.1](https://github.com/actions/setup-java/releases/tag/v6.0.1) | `de7274f081f381c8f8158605e0321c36c376e2e6` |
| upload-artifact | [v7.0.1](https://github.com/actions/upload-artifact/releases/tag/v7.0.1) | `043fb46d1a93c77aae656e7c1c64a875d1fc6a0a` |

核对过固定提交中的 `action.yml` 和 README：工作流使用 GitHub 托管的 Ubuntu 24.04；保留 Temurin 17 和 Maven 缓存；checkout 使用普通 `pull_request` 事件；artifact 仍采用默认 ZIP 格式、原名称、路径白名单及七天保留期。未增加权限、放宽扫描阈值或修改业务／迁移／备份契约。

## 验收与后续

本地检查为 actionlint、Markdown 本地链接和 `git diff --check`。远程验证以本维护 PR 的完整 CI 为准，合并前要求三个任务全部通过，并确认报告能下载读取。

当前说明文档已同步 PR #1 的合并和复验状态；历史阶段记录保留原始日期、分支和验收数字。后续优先按实际使用反馈修正问题；查询优化需要负载目标与对照实验，不因完成计划而继续增加功能。
