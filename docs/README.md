# 文档导航

当前进展：4A 部署配置与依赖核对，分支 `codex/deployment-config`，基于 `main@6936373`。更新时间：2026-09-07。

## 从这里开始

| 目的 | 文档 |
| --- | --- |
| 本地启动、账号和当前功能 | [项目 README](../README.md) |
| 下一轮开发顺序与验收标准 | [后续优化方案](plans/next-optimization-plan-2026-09-06.md) |
| 数据库迁移与恢复 | [迁移指南](guides/counselor-migration.md) |
| deploy 启动与环境变量 | [部署指南](guides/deployment.md) |
| 实际依赖、已修复项和发布遗留项 | [依赖核对](verification/dependencies-2026-09-07.md) |
| 已实现能力与表述边界 | [项目证据](project-evidence.md) |

## 阶段记录

| 阶段 | 提交 | 验收记录 |
| --- | --- | --- |
| 一：工程名称与页面统一 | `a916d1d` | [第一阶段](acceptance/phase-1-foundation.md) |
| 二：辅导员档案与数据迁移 | `1adc0da` | [第二阶段](acceptance/phase-2-counselor-records.md) |
| 三：账号、权限与审计 | `c193486` | [第三阶段](acceptance/phase-3-account-security.md) |
| 4A：部署配置、版本核对及认证补丁 | `codex/deployment-config`；补丁 `2f27992` | [4A 验收](acceptance/phase-4a-deployment-config.md) |
| 重构前的 MySQL 验收 | 历史版本 | [2026-08-07 记录](acceptance/2026-08-07-mysql.md) |

第一至三阶段分支已在快进合入本地 `main` 后删除，提交及全部历史仍保留。4A 使用独立分支。旧记录中出现的分支名、登录方式和功能边界描述的是当时状态；以当前 README 和项目证据为准。本次未推送远程分支。

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
