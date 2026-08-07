# 项目证据与简历口径

最后核验：2026-08-07，本仓库当前工作树。

本文档是 CareerWorkspace 或其他简历材料引用 `user-management` 时的事实入口。实现证据以本仓库源码、测试和可运行页面为准。

## 已实现事实

| 能力 | 当前证据 |
| --- | --- |
| Spring Boot 应用与标准 Maven 目录 | `pom.xml`、`src/main/java/.../UserManagementApplication.java` |
| MVC 分层 | `controller/UserController.java`、`service/UserService.java`、`service/impl/UserServiceImpl.java` |
| MyBatis 数据访问 | `mapper/UserMapper.java`，使用 `#{}` 参数绑定、关联结果映射和模糊搜索 |
| 关系模型 | `db/*/schema.sql`，包含 users、departments、roles、user_roles 四张表 |
| 用户功能 | 新增、列表、详情、编辑、删除、姓名/备注模糊搜索 |
| 角色维护 | 表单角色勾选，Service 事务内替换 user_roles 关系 |
| 登录检查 | `LoginController` 写入 Session，`LoginInterceptor` 统一保护 `/users/**` |
| 参数校验 | `User` Bean Validation + Controller `BindingResult` |
| 图片处理 | JPG/PNG 扩展名和 Content-Type 白名单、UUID 重命名、独立目录与删除清理 |
| 环境配置 | 默认 H2 零配置启动；`mysql` profile 从环境变量读取连接信息 |
| 自动化验证 | `./mvnw test`：11 项通过，覆盖 Web、Service/MyBatis、文件存储和上下文启动 |
| 真实 MySQL 验收 | `docs/mysql-acceptance.md`：MySQL Community Server 9.0.1 + Connector/J 完整 CRUD/关系/上传流程 |
| 页面展示 | Thymeleaf 响应式登录、目录、新增、编辑和详情页；截图位于 `docs/images/` |

## 推荐简历表述

可根据版面选择 2—3 条，不需要全部堆入简历：

- 基于 Java 17、Spring Boot、Spring MVC、MyBatis 与 MySQL/H2 实现服务端渲染用户管理项目，按 Controller、Service、Mapper 分层完成用户增删改查、详情与姓名/备注模糊搜索。
- 设计用户、部门、角色及用户—角色多对多表关系，在事务内同步角色分配；通过 MyBatis 参数绑定和关联结果映射返回部门、角色信息。
- 使用 Session + Interceptor 统一保护管理页面，结合 Bean Validation、PRG 反馈和 JPG/PNG 白名单、UUID 重命名实现表单与头像上传处理。
- 将默认运行环境改为内存 H2、MySQL 连接改为环境变量，并补充 Maven Wrapper、GitHub Actions 与 11 项自动化测试；另使用真实 MySQL 9.0.1 验收建表、CRUD、关系更新与上传清理链路。

## 面试口径

一句话版本：

> 这是我用于练习 Java Web 后端工程基础的完整可运行项目，我按 Controller、Service、Mapper 分层实现用户 CRUD、条件查询、部门/角色关系、Session 拦截和图片上传，并补了 H2/MySQL 双环境与自动化测试。

如果被问“是不是 RBAC 权限系统”：

> 项目实现了用户、角色、部门和用户—角色关系建模，也能维护角色分配；目前登录保护仍是配置化演示账号加 Session 拦截，没有做基于角色的接口授权，所以更准确地说是 RBAC 风格的数据模型，不是成熟权限平台。

## 不应声称

- 不写“生产级”“企业级”“高并发”“已上线”或真实用户量。
- 不写 Spring Security、JWT、OAuth2、密码哈希、细粒度鉴权；当前未实现。
- 不写 CSRF 防护或公网安全部署；当前演示登录不应直接暴露到公网。
- 不把 Thymeleaf 页面称为 Vue/React 前端或 REST API。
- 不声称对象存储、病毒扫描、图片内容识别；当前只有扩展名与 Content-Type 白名单。
- 不把 11 项测试扩大成完整质量保障或性能测试。

## CareerWorkspace 同步提示

旧资料若仍引用嵌套的 `user-management/src/...`，需要改成当前独立仓库的根目录相对路径 `src/...`。旧的 `src/main/resources/schema.sql` 已拆分为 `src/main/resources/db/h2/schema.sql` 与 `src/main/resources/db/mysql/schema.sql`。
