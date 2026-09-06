# 项目证据与表述边界

核验日期：2026-09-06，第二阶段工作树。产品名称：高校辅导员信息管理系统（Campus Counselor Management）。仓库目录仍为 `user-management`。

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
| 图片 | JPG/PNG 类型检查、UUID 存储、失败新图清理、提交后旧图清理、失败日志 |
| 登录 | 配置账号与 Session；拦截 counselors、departments 和兼容 users 路由 |
| 数据库 | H2 演示；MySQL 持久化；Flyway V1–V3 和显式旧数据工号映射 |
| 测试 | 26 项通过：Web、SQL 次数、业务、事务、迁移、文件存储 |
| MySQL 验收 | 独立 MySQL 9.0.1 迁移、重启和备份恢复；详见 counselor-records-acceptance.md |

Java 代码位于 `src/main/java/io/github/gflabandon/counselor/`，Java 迁移位于 `src/main/java/db/migration/`。自动化报告位于本地 `target/surefire-reports/`，不提交构建产物。

## 可解释的项目描述

用于演示高校辅导员档案维护的 Java Web 项目，支持工号与姓名检索、院系归属、任职状态、头像上传和状态记录。按 Controller、Service、Mapper 分层实现，采用 Flyway 管理数据库变化，以版本号检查避免过期编辑覆盖。

本次是在旧通用用户管理原型上进行业务重构，不是从真实学校需求推导出的已交付系统。新能力的开发和验收日期为 2026-09-06，不能倒填为过去已完成的功能。

## 不应声称

- 档案不是系统账号，旧角色关系不参与接口授权。尚无完整 RBAC、Spring Security、密码哈希、JWT 或 OAuth2。
- 尚无 CSRF 防护、头像独立授权和生产级文件安全检查；不能宣称已满足公网部署要求。
- 状态历史不是覆盖全部字段和院系操作的完整审计。
- 26 项测试及固定两条列表 SQL 不代表高并发、性能指标或完整质量保障。
- 没有真实高校交付、真实用户规模、学生／班级管理、审批、Excel 导入导出或 AI 功能证据。
- 新数据库结构不能仅通过切回旧 Git 提交回退；须恢复对应数据库与上传目录备份。

## 历史与材料同步

第一阶段记录见 `refactoring-foundation.md`，2026-08-07 的 MySQL 报告见 `mysql-acceptance.md`。历史测试数字与旧路由只描述当时状态，不代替当前证据。本轮未修改 CareerWorkspace 或简历；后续同步需按实际开发日期和当前验收结果更新。
