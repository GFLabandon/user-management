# 文档导航

当前进展：阶段成果已整合到 `main`，代码基线 `7706eaa`。更新时间：2026-10-01。该提交的远程 CI 中 Java/Python 与真实 MySQL 运行、迁移、恢复验收通过；安全扫描仍有 MySQL 镜像阻断项，尚未发布。5A.1 账号关联选择及 5A.2 错误反馈已通过本机验收，当前在 `codex/error-feedback`，尚未推送／合并；见[5A.2 验收](acceptance/phase-5a2-error-feedback.md)。具体证据与后续安排见[下一阶段实施方案](plans/next-steps-2026-10-01.md)。

## 从这里开始

| 目的 | 文档 |
| --- | --- |
| 本地启动、账号和当前功能 | [项目 README](../README.md) |
| 下一轮开发顺序与验收标准 | [下一阶段实施方案](plans/next-steps-2026-10-01.md) |
| 原始阶段划分与历史计划 | [后续优化方案（2026-09-06）](plans/next-optimization-plan-2026-09-06.md) |
| 数据库迁移与恢复 | [迁移指南](guides/counselor-migration.md) |
| 维护窗口备份、校验与空环境恢复 | [备份恢复指南](guides/backup-and-restore.md) |
| deploy、容器、健康检查与运行维护 | [部署指南](guides/deployment.md) |
| 实际依赖、已修复项和发布遗留项 | [依赖核对](verification/dependencies-2026-09-13.md)、[4D 补丁依赖树](verification/dependency-tree-4d-2026-09-30.txt) |
| 候选版本检查与回退 | [发布指南](guides/release.md)、[安全核对](verification/security-2026-09-30.md) |
| 已实现能力与表述边界 | [项目证据](project-evidence.md) |

## 阶段记录

| 阶段 | 提交 | 验收记录 |
| --- | --- | --- |
| 一：工程名称与页面统一 | `a916d1d` | [第一阶段](acceptance/phase-1-foundation.md) |
| 二：辅导员档案与数据迁移 | `1adc0da` | [第二阶段](acceptance/phase-2-counselor-records.md) |
| 三：账号、权限与审计 | `c193486` | [第三阶段](acceptance/phase-3-account-security.md) |
| 4A：部署配置、版本核对及认证补丁 | `ed0b0c2`；补丁 `2f27992` | [4A 验收](acceptance/phase-4a-deployment-config.md) |
| 4B：依赖升级、容器、持久化与健康检查 | `493fed6`；依赖 `96507d8` | [4B 验收](acceptance/phase-4b-runtime.md) |
| 4C：协调备份、恢复保护与真实 HTTP 验收 | `bf8a300` | [4C 验收](acceptance/phase-4c-backup-recovery.md) |
| 4D：MySQL CI、扫描与发布流程 | `88e2f9c`、补丁 `61635f8` | [4D 验收](acceptance/phase-4d-ci-release.md) |
| 4D.1：官方镜像处置核对 | 本地核对，发布仍阻断 | [4D.1 核对](verification/security-mysql-review-2026-10-01.md) |
| 5A.1：账号关联档案搜索选择 | 独立分支，本机验收 | [5A.1 验收](acceptance/phase-5a1-account-picker.md) |
| 5A.2：错误与失败提示 | 独立分支，本机验收 | [5A.2 验收](acceptance/phase-5a2-error-feedback.md) |
| 重构前的 MySQL 验收 | 历史版本 | [2026-08-07 记录](acceptance/2026-08-07-mysql.md) |

第一至 4D 阶段均已快进整合到 `main`；阶段分支不再作为维护入口，阶段提交及全部历史仍保留。旧记录中的分支名、登录方式和功能边界描述当时状态；以当前 README 和项目证据为准。

## 目录约定

```text
docs/
├── README.md             # 文档入口
├── project-evidence.md   # 当前事实和项目表述
├── guides/               # 可按步骤操作的使用、迁移说明
├── acceptance/           # 各阶段验收，保留历史结果
├── plans/                # 待实施方案，不作为已完成功能
├── verification/         # 实际依赖快照和版本核对
├── images/
│   ├── current/          # 当前阶段截图
│   └── archive/          # 按 original、phase-1、phase-2 归档
└── legacy/               # 旧版 H2/MySQL SQL，仅用于迁移核对
```

源码继续采用标准 Maven 目录。`.idea/` 是本机 IDE 设置，`target/` 是被 Git 忽略的构建与测试结果；它们不进入文档归档或提交。数据库文件、真实凭据和上传文件不放入 `docs/`。已经执行的 Flyway 版本文件保持原位且不修改，新的结构变化追加迁移。
