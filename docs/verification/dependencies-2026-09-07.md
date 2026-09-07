# 4A 依赖版本与维护核对

核对日期：2026-09-07。基线 `6936373`，分支 `codex/deployment-config`。

## 结论

本轮为配置验收保留 Java 17 / Boot 3.5 / MyBatis 3.0 结构，只将 Spring Security 从 6.5.6 升到官方修复 CVE-2026-22746 的 6.5.10。该补丁是针对当前停用账号认证路径的最小修复，不是“所有依赖均已无漏洞”的认证。Boot、Tomcat、Flyway 与数据库的完整发布组合仍需在公网开放前单独升级和验收。

## 实际解析版本

来源为本机 `java -version`、`pom.xml` 和 Maven `dependency:tree`，完整树保存在[依赖快照](dependency-tree-2026-09-07.txt)。

| 组件 | 基线 | 本轮最终版本／状态 |
| --- | --- | --- |
| Java | Temurin 17.0.20+8 | 不变 |
| Spring Boot | 3.5.7 | 不变 |
| Spring Framework | 6.2.12 | 不变 |
| Spring Security | 6.5.6 | 6.5.10；config、web、core、crypto、test 统一版本 |
| 嵌入式 Tomcat | 10.1.48 | 不变，存在需要评估的后续安全修复 |
| MyBatis Starter / MyBatis-Spring | 3.0.3 / 3.0.3 | 不变；MyBatis core 3.5.14 |
| Flyway core / mysql | 11.7.2 | 不变 |
| MySQL Connector/J | 9.4.0 | 不变 |
| H2 | 2.3.232 | 仅本地演示与快速测试；deploy 拒绝使用 |
| MySQL Server | 本机隔离 9.0.1 | 本轮启动流程验收使用；不是已选定的生产数据库版本 |

MyBatis 官方将 Starter 3.0 与 Boot 3.2–3.5、Java 17+ 对应。本轮不把 Starter 4.0 混入 Boot 3.5。[官方兼容表](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)

## 安全公告与适用性

| 公告／范围 | 当前代码检查 | 处置 |
| --- | --- | --- |
| CVE-2026-22746：停用／锁定等账号在 DaoAuthenticationProvider 中的计时防护 | 本项目用数据库 UserDetails 的 enabled 状态，且原版本 6.5.6 在公告影响范围内 | 升至公告修复版 6.5.10；新增测试验证停用账号仍实际执行密码匹配，同时拒绝登录 |
| CVE-2026-41707：DPoP proof 重放 | 依赖树与配置未引入 OAuth2 资源服务器或 DPoP | 当前未发现触发功能，记录为版本维护风险 |
| CVE-2026-47841：WebAuthn 与分布式会话 | 未启用 WebAuthn、Redis/JDBC Session | 当前未发现触发条件 |
| CVE-2026-47842：AesBytesEncryptor 的特定构造方式 | 当前只用 PasswordEncoder/bcrypt，没有调用该加密器 | 当前未发现触发调用；不将其误认为 bcrypt 漏洞 |

第一项的官方影响范围是 6.5.0–6.5.9，6.5.10 提供修复。测试核对密码校验调用，不用容易受机器负载影响的时间阈值。[CVE-2026-22746](https://spring.io/security/cve-2026-22746/)

其余三项官方公告列出的 6.5 分支修复为企业渠道 6.5.12，不能假设本项目拥有该仓库或支持授权。当前没有相关功能不等于版本永久安全，后续引入这些功能前需要重新核对。[DPoP](https://spring.io/security/cve-2026-41707/)、[WebAuthn](https://spring.io/security/cve-2026-47841/)、[AES](https://spring.io/security/cve-2026-47842/)

Tomcat 10.1.48 位于多项后续公告的版本范围内，例如 CVE-2026-24880 涉及代理接受特定非法 chunk 扩展的场景，修复列于 10.1.53。当前本机 HTTP 验收没有代理，不能据此判断未来代理配置安全。本文不将任意单个修复版当成最终升级目标，也不宣称已完成全部 Tomcat 公告的可利用性分析。[Tomcat 官方安全公告](https://tomcat.apache.org/security-10.html)

## 发布前仍需完成

1. 核对实施当天可以取得的 Boot 维护版本及对应 BOM，选择完整兼容组合；企业维护期与公开仓库可取得的修复并不等同。不能仅凭“3.5 是最后一个次版本”认定项目已获得长期维护。[Spring 支持政策](https://spring.io/support-policy/)
2. 在独立提交中完成 Boot、Framework、Tomcat 等必要升级；复查本轮 Security 覆盖属性，若 BOM 已包含合适修复则移除覆盖。升级后验证登录、CSRF、私有图片、迁移及真实 HTTP，不能只看编译通过。
3. 数据库候选优先评估 MySQL LTS 路线，固定具体镜像版本后，再与对应 Flyway 版本做迁移验收。本机 9.0.1 验收不替代版本支持声明。[MySQL 发行路线](https://dev.mysql.com/doc/refman/8.4/en/mysql-releases.html)、[Flyway MySQL 说明](https://documentation.red-gate.com/flyway/reference/database-driver-reference/mysql)
4. 对最终依赖树做完整漏洞扫描与误报核对，记录工具版本、数据更新时间和处置。当前结果是官方公告的定向核对，尚未做全面 SCA 扫描。

上面是发布前遗留项，不影响本轮在回环地址和临时数据库验证部署配置，但不能跳过这些项直接开放公网。
