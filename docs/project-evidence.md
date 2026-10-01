# 项目证据与表述边界

核验日期：2026-10-01。第一至 4D 阶段已整合到 `main`，基线 `7706eaa`；5A.1 提交为 `e2f1689`，5A.2 在 `codex/error-feedback` 完成本地验收，尚未推送或合并。Boot 升级基线为 `96507d8`，4D 另补 Tomcat／Jackson／OpenSSL 与 MySQL 镜像摘要修复。产品名称：高校辅导员信息管理系统（Campus Counselor Management）。仓库目录仍为 `user-management`。

## 当前实现

| 能力 | 实现位置／验证 |
| --- | --- |
| Java 17、Spring Boot、MyBatis、Thymeleaf | pom.xml；标准 Maven 目录 |
| 档案对象 | entity/Counselor.java：工号、姓名、院系、任职状态、照片、备注、时间戳、版本 |
| 分层与输入边界 | CounselorController、CounselorForm、CounselorService、CounselorMapper；显式字段白名单 |
| 检索与分页 | 工号／姓名、院系／状态筛选，COUNT + JOIN 分页，参数化 SQL |
| 并发修改保护 | UPDATE 的 id + version 条件，受影响行数检查；旧表单不能覆盖新修改 |
| 状态历史 | 建档、停用、恢复在职记录，与档案写入同一事务；保存操作者和时间 |
| 院系维护 | 新增、编辑、停用；有新档案或旧资料引用时禁止删除 |
| 图片 | 解码和尺寸／大小校验、重新编码、UUID 存储；读取要求登录且有档案引用，拒绝符号链接；保留失败清理 |
| 登录与权限 | Spring Security 表单与 CSRF；独立账号、bcrypt、ADMIN／VIEWER，账号版本变化撤销旧会话 |
| 账号关联选择 | 工号／姓名搜索，最多 20 条，仅返回选择所需字段；编辑回显、取消关联、JOIN 列表；真实 MySQL 双管理员争用与版本冲突验收 |
| 错误反馈 | MVC 与过滤器中文错误页，400／403／404／409／413／500／503 等状态、请求编号；表单保留与密码不回填，真实 HTTP 上传边界验收 |
| 操作记录 | 登录结果、访问拒绝、账号／档案／院系成功与已处理失败；成功记录与业务同事务 |
| 数据库 | H2 演示；MySQL 持久化；Flyway V1–V4 和显式旧数据工号映射 |
| 部署配置 | deploy 启动入口、数据库连接前的配置与目录校验；非 root 数据库账号验收 |
| 依赖核对 | Boot 4.0.8 / Security 7.0.7 / MyBatis Starter 4.0.1，H2 2.5.250 修复迁移回归；见 verification 目录 |
| 容器运行 | 非 root 应用、只读根文件系统、MySQL 8.4.11 与头像独立卷、回环 HTTP；见 Dockerfile / compose.yaml |
| 健康与日志 | 独立无状态探针授权、readiness 检查 DB、liveness 不依赖 DB；请求编号与脱敏日志、Docker 日志保留限制 |
| 备份恢复 | 停应用并持有数据库读锁；SQL、头像、清单和摘要；仅恢复到全新项目；见 scripts/maintenance.py |
| 测试 | 5A.2 本机重跑：91 项 Java、21 项 Python、真实 MySQL 运行 11 组通过；迁移 5 组、恢复 8 组沿用基线远程 CI 证据 |
| CI 与扫描 | 分离快速测试、MySQL、安全扫描任务；扫描实际运行镜像与 Java 依赖；远程 CI 状态以 GitHub Actions 为准，扫描结论见 4D 核对记录 |
| MySQL 验收 | 第二阶段迁移、重启和备份恢复；第三阶段另验证 V3→V4、首次凭据、CSRF multipart 和权限；4A 新增专用数据库账号启动及重启验收；详见各阶段验收记录 |

Java 代码位于 `src/main/java/io/github/gflabandon/counselor/`，Java 迁移位于 `src/main/java/db/migration/`。自动化报告位于本地 `target/surefire-reports/`，不提交构建产物。

4B 的独立 MySQL 容器验收覆盖慢启动、首次凭据、真实登录与 multipart CSRF、只读越权、旧会话撤销、数据库停止／恢复、重建后账号和图片持久化。脚本为 `scripts/verify-compose.py`，记录见 [4B 验收](acceptance/phase-4b-runtime.md)。Flyway 仍提示 MySQL 8.4 / H2 2.5 超出其内置验证范围；本项目用例通过不等于供应商完整兼容认证。

4C 通过随机隔离 Compose 项目核对全部 11 张表的行数和数据行摘要、Flyway 历史、账号哈希、头像引用及文件内容；恢复后实际登录、搜索和编辑。额外覆盖读锁阻止写入与异常释放、损坏备份拒绝、已有目标拒绝、缺失图片导致备份无效。脚本为 `scripts/verify-recovery.py`，见 [4C 验收](acceptance/phase-4c-backup-recovery.md)。本阶段只修改维护脚本、CI 快速检查和文档，没有重跑 Java 测试或远程 CI。

4D 的本机 CI 同入口验证覆盖旧库映射缺失时零复制、完整迁移保留 ID／关系／图片引用、V3→V4 及未知非空库拒绝接管。工具和发布步骤见 [4D 验收](acceptance/phase-4d-ci-release.md)、[安全核对](verification/security-2026-09-30.md)及[发布指南](guides/release.md)。应用的 HIGH／CRITICAL 发现已归零，9 月 30 日 MySQL 镜像仍有 28 个阻断项；10 月 1 日基线远程 CI 为 30 项，安全任务保持失败。当天官方摘要未更新，详见[4D.1 核对](verification/security-mysql-review-2026-10-01.md)。这不代表实际发布已经通过。

5A.1 通过工号／姓名选择档案，保持服务端存在性、唯一关联、账号版本检查和旧会话撤销。新增查询不暴露备注、照片路径、密码或其他账号信息；截图、命令、并发与权限结果见[5A.1 验收](acceptance/phase-5a1-account-picker.md)。没有修改迁移、备份契约或镜像依赖。

5A.2 补齐失败状态与操作入口，区分 CSRF、权限、编辑冲突、上传校验和存储故障。新增真实 Tomcat 测试发现并修复上传超限响应被截断的问题；请求体清理有上限，未放宽上传接受限制。见[5A.2 验收](acceptance/phase-5a2-error-feedback.md)。

## 可解释的项目描述

用于演示高校辅导员档案维护的 Java Web 项目，支持工号与姓名检索、院系归属、任职状态、头像上传和状态记录。按 Controller、Service、Mapper 分层实现，采用 Flyway 管理数据库变化，以版本号检查避免过期编辑覆盖。

本次是在旧通用用户管理原型上进行业务重构，不是从真实学校需求推导出的已交付系统。前三阶段开发和验收日期为 2026-09-06，4A 为 2026-09-07，4B 为 2026-09-13，4C 为 2026-09-25，4D 为 2026-09-30，不能倒填功能完成日期。

## 不应声称

- 当前授权是两种固定角色，不是可配置权限平台；尚无院系数据隔离、JWT、OAuth2 或 SSO。
- 尚无生产部署、HTTPS、登录限流或病毒扫描。运行镜像和打包依赖的漏洞扫描不等于安全认证，发现项与发布阻断以当次报告为准，不能宣称已满足公网部署要求。
- 状态历史与操作记录分开；操作记录没有字段前后值、归档或防篡改保障。
- 自动化测试数量及档案列表的固定两条 SQL 不代表高并发、性能指标或完整质量保障。
- 协调备份与恢复已在本机小样本验收；尚无定时备份、自动加密、异地保管或生产 RTO／RPO 证据。Session 是单进程内存状态，不支持无损滚动发布。
- 没有真实高校交付、真实用户规模、学生／班级管理、审批、Excel 导入导出或 AI 功能证据。
- 新数据库结构不能仅通过切回旧 Git 提交回退；须恢复对应数据库与上传目录备份。

## 历史与材料同步

第一阶段记录见[基础重构验收](acceptance/phase-1-foundation.md)，2026-08-07 的 MySQL 报告见[历史验收](acceptance/2026-08-07-mysql.md)。历史测试数字与旧路由只描述当时状态，不代替当前证据。本轮未修改 CareerWorkspace 或简历；后续同步需按实际开发日期和当前验收结果更新。
