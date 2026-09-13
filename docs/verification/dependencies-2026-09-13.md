# 4B 前置依赖升级

日期：2026-09-13；从 `ed0b0c2`（4A）继续，分支 `codex/deployment-runtime`。依赖升级单独提交；容器运行结果另见 [4B 验收](../acceptance/phase-4b-runtime.md)。

## 选定组合

以已发布的 Boot 4.0 维护版统一管理 Framework、Security、Tomcat、Flyway 和驱动，移除 Security 6.5.10 单独覆盖。保留 Java 17 和现有 MVC / Thymeleaf 单体。官方确认 [Boot 4.0.8 已发布到 Maven Central](https://spring.io/blog/2026/08/20/spring-boot-4-0-8-available-now/)；[MyBatis 兼容表](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)将 4.0 对应 Boot 4.0+ 和 Java 17+。版本可下载性通过 Maven Central 元数据及实际构建核对。

| 组件 | 实际解析版本 |
| --- | --- |
| Spring Boot | 4.0.8 |
| Spring Framework | 7.0.9 |
| Spring Security | 7.0.7，全部模块跟随 BOM |
| Tomcat | 11.0.24 |
| MyBatis Starter / Spring / Core | 4.0.1 / 4.0.0 / 3.5.19 |
| Thymeleaf / extras-springsecurity6 | 3.1.5.RELEASE；使用 Boot 管理的现有模块名 |
| Flyway core / mysql | 11.14.1 |
| Connector/J | 9.7.0 |
| H2 | 2.5.250，显式覆盖，限演示／测试 |
| Java | 本机及容器均 Temurin 17.0.20+8 |
| 容器数据库候选 | MySQL 8.4.11 LTS；真实运行以 4B 报告为准 |

[完整解析树](dependency-tree-2026-09-13.txt)记录依赖升级提交时的组合；4B 增加 Actuator 后另保存最终树。

## 兼容变更与回归

按照 [Boot 4 迁移指南](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)，使用 webmvc、flyway 及对应 Web / Security 测试 starter；更新 MockMvc 自动配置包名。保留 EnvironmentPostProcessor 原注册入口，真实 SpringApplication 配置阶段测试继续通过。

首次回归定位到两处问题：

- Security 7 默认登录重定向为 `/login`，更新原绝对 URL 断言，仍逐项核验业务路由要求登录。[官方迁移说明](https://docs.spring.io/spring-security/reference/6.5/migration-7/web.html)
- BOM 的 H2 2.4.240 在多连接旧数据迁移时抛出 `Check constraint invalid` / `database has been closed`；2.5.250 发布记录修复 #4302。覆盖至此版后原迁移用例通过，没有修改已执行的 V1–V4。[H2 发布记录](https://github.com/h2database/h2database/releases/tag/version-2.5.250)

`./mvnw -B --no-transfer-progress verify dependency:tree`：68 项，0 失败／错误／跳过，打包成功。包含停用账号密码匹配、真实模板角色隐藏、CSRF、旧会话失效、私有图片、事务回滚、空库与旧数据迁移。容器 HTTP 与 MySQL 验收由后续 4B 记录补充，不能只以此测试数字替代。

## 仍需维护

Security 7.0.7 达到先前列出的 DPoP、WebAuthn 和 AES 公告修复版；本项目仍未引入这些功能。[Spring 安全公告](https://spring.io/security/)

本轮是兼容升级与定向回归，尚未执行完整 SCA 或镜像漏洞扫描。4D 应补自动依赖检查，公网开放前仍需复核最新公告、HTTPS 和登录限流。固定版本／镜像摘要保障本次构建可追查，不表示其以后无需更新。
