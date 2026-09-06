# 项目证据与表述边界

核验日期：2026-09-06，第三阶段提交 `c193486`（已整合至本地 `main`）。产品名称：高校辅导员信息管理系统（Campus Counselor Management）。仓库目录仍为 `user-management`。

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
| 操作记录 | 登录结果、访问拒绝、账号／档案／院系成功与已处理失败；成功记录与业务同事务 |
| 数据库 | H2 演示；MySQL 持久化；Flyway V1–V4 和显式旧数据工号映射 |
| 测试 | 37 项通过：Web、SQL 次数、业务、事务、迁移、图片及账号安全 |
| MySQL 验收 | 第二阶段迁移、重启和备份恢复；第三阶段另验证 V3→V4、首次凭据、CSRF multipart 和权限；详见两阶段验收记录 |

Java 代码位于 `src/main/java/io/github/gflabandon/counselor/`，Java 迁移位于 `src/main/java/db/migration/`。自动化报告位于本地 `target/surefire-reports/`，不提交构建产物。

## 可解释的项目描述

用于演示高校辅导员档案维护的 Java Web 项目，支持工号与姓名检索、院系归属、任职状态、头像上传和状态记录。按 Controller、Service、Mapper 分层实现，采用 Flyway 管理数据库变化，以版本号检查避免过期编辑覆盖。

本次是在旧通用用户管理原型上进行业务重构，不是从真实学校需求推导出的已交付系统。新能力的开发和验收日期为 2026-09-06，不能倒填为过去已完成的功能。

## 不应声称

- 当前授权是两种固定角色，不是可配置权限平台；尚无院系数据隔离、JWT、OAuth2 或 SSO。
- 尚无生产部署、HTTPS、登录限流、病毒扫描或依赖漏洞验收，不能宣称已满足公网部署要求。
- 状态历史与操作记录分开；操作记录没有字段前后值、归档或防篡改保障。
- 37 项测试及固定两条列表 SQL 不代表高并发、性能指标或完整质量保障。
- 没有真实高校交付、真实用户规模、学生／班级管理、审批、Excel 导入导出或 AI 功能证据。
- 新数据库结构不能仅通过切回旧 Git 提交回退；须恢复对应数据库与上传目录备份。

## 历史与材料同步

第一阶段记录见[基础重构验收](acceptance/phase-1-foundation.md)，2026-08-07 的 MySQL 报告见[历史验收](acceptance/2026-08-07-mysql.md)。历史测试数字与旧路由只描述当时状态，不代替当前证据。本轮未修改 CareerWorkspace 或简历；后续同步需按实际开发日期和当前验收结果更新。
