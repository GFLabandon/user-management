# 文档导航

当前基线：2026-10-02，4D.2、5A.1–5A.3、5B 和第 6 阶段已通过 [PR #1](https://github.com/GFLabandon/user-management/pull/1) 合并到 `main`（`97e309d`）。[main CI](https://github.com/GFLabandon/user-management/actions/runs/37022219263) 三项任务全部通过：111 项 Java、32 项 Python、14 组运行、6 组迁移／镜像切换、10 组恢复；应用与派生数据库镜像 HIGH／CRITICAL 均为 0。扫描结果描述当次报告，不代表生产部署或零风险。

后续维护见[空间清理与 CI 更新](verification/maintenance-2026-10-02.md)；功能演示见[演示路径](guides/phase-5a-demo.md)，原方案及执行记录见[下一阶段实施方案](plans/next-steps-2026-10-01.md)。历史验收文档保留当时分支和结果。

2026-10-04 演示复核补充：按权限区分空列表指引、提前说明头像尺寸限制、统一账号保存反馈并改善窄屏表格；见[本轮验收](acceptance/demo-usability-2026-10-04.md)。

2026-10-05：档案返回列表、编辑取消、保存及冲突重开现保留原筛选与页码，见[条件保留验收](acceptance/list-context-2026-10-05.md)。

2026-10-09：管理员可从档案、账号和院系直接定位操作记录，见[快捷入口验收](acceptance/audit-shortcuts-2026-10-09.md)。

2026-10-10：MySQL 派生镜像更新 OpenSSL 与 Go 补丁，见[安全修复记录](verification/security-mysql-runtime-2026-10-10.md)。

## 从这里开始

| 目的 | 文档 |
| --- | --- |
| 本地启动、账号和当前功能 | [项目 README](../README.md) |
| 5A 完整演示和截图 | [5A 演示路径](guides/phase-5a-demo.md) |
| 本机 Docker 资源用途与清理 | [Docker 资源说明](guides/docker-resources.md) |
| MySQL 派生镜像构建、维护与升级 | [数据库镜像指南](guides/mysql-runtime-image.md) |
| 三档查询基线、计划与复现 | [查询基线](acceptance/phase-6-query-baseline.md) |
| 下一轮开发顺序与验收标准 | [下一阶段实施方案](plans/next-steps-2026-10-01.md) |
| 原始阶段划分与历史计划 | [后续优化方案（2026-09-06）](plans/next-optimization-plan-2026-09-06.md) |
| 数据库迁移与恢复 | [迁移指南](guides/counselor-migration.md) |
| 维护窗口备份、校验与空环境恢复 | [备份恢复指南](guides/backup-and-restore.md) |
| 图片清理失败核查与受控重试 | [图片清理指南](guides/image-cleanup.md) |
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
| 4D.2：数据库派生镜像安全修复 | `a9c776e`；已合并，远程通过 | [4D.2 验收](verification/security-mysql-runtime-2026-10-02.md) |
| 5A.1：账号关联档案搜索选择 | `e2f1689`；已合并 | [5A.1 验收](acceptance/phase-5a1-account-picker.md) |
| 5A.2：错误与失败提示 | `8f79615`；已合并 | [5A.2 验收](acceptance/phase-5a2-error-feedback.md) |
| 5A.3：操作记录中文说明、筛选与分页 | `0580c8b`；已合并 | [5A.3 验收](acceptance/phase-5a3-audit-query.md) |
| 5B：图片事务协调、失败保护与离线重试 | `0e2546e`；已合并 | [5B 验收](acceptance/phase-5b-image-cleanup.md) |
| 6：三档查询基线 | 本机 33 场景；保留现有 SQL／索引 | [第 6 阶段](acceptance/phase-6-query-baseline.md) |
| 重构前的 MySQL 验收 | 历史版本 | [2026-08-07 记录](acceptance/2026-08-07-mysql.md) |

第一至第 6 阶段均已整合到 `main`；阶段分支不再作为维护入口，阶段提交及全部历史仍保留。旧记录中的分支名、登录方式和功能边界描述当时状态；以当前 README 和项目证据为准。

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
