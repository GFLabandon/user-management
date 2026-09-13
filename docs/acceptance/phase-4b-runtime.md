# 4B：容器运行、持久化与健康检查

日期：2026-09-13。基线 `ed0b0c2`（4A），分支 `codex/deployment-runtime`；依赖升级独立提交 `96507d8`。本阶段保留 Java 17、MyBatis、Thymeleaf 单体，不改 Flyway V1–V4，不连接已有数据库，不开放公网。

## 交付

- Dockerfile 多阶段构建，构建阶段以普通用户执行 Maven Wrapper 和全部测试；运行阶段仅包含 JRE、应用 JAR 与 Java 健康探针，以 UID/GID 10001 运行。
- Java 17.0.20+8、MySQL 8.4.11 固定标签与多架构摘要；应用根文件系统只读，临时目录限额，头像与数据库分别持久化。宿主机仅绑定回环 HTTP，数据库不发布端口。
- Compose 数据库健康检查使用应用账号执行 TCP SELECT 1，再启动应用。readiness 包含 DB，liveness 独立于 DB；健康响应只含 status，其他管理端点拒绝访问。
- 独立无状态探针授权链，不读取业务会话。请求响应头增加服务器生成的 X-Request-ID；业务完成和失败日志带该编号，不写路径、查询参数、正文、Cookie 或异常消息。数据库访问失败返回通用 503。
- 两个容器均配置 Docker local 日志驱动，10 MB × 3 份；应用优雅退出等待 30 秒，Compose 窗口 40 秒。
- `scripts/verify-compose.py` 用随机项目、端口和凭据执行真实 HTTP 验收，只清理该次容器和卷；结果保存在被忽略的 target 目录。
- 更新部署指南、当前项目证据及下一轮 4C 任务说明。

## 环境和回归

本机 macOS arm64，Temurin 17.0.20+8，Docker Desktop / Engine 29.7.2；容器 Linux arm64。Boot 4.0.8、Security 7.0.7、MyBatis Starter 4.0.1、Flyway 11.14.1、Connector/J 9.7.0。完整版本见[依赖记录](../verification/dependencies-2026-09-13.md)、[最终依赖树](../verification/dependency-tree-4b-2026-09-13.txt)。

执行：

```sh
./mvnw -B --no-transfer-progress verify dependency:tree -DoutputFile=docs/verification/dependency-tree-4b-2026-09-13.txt
docker build -t campus-counselor-management:local .
python3 scripts/verify-compose.py
git diff --check
```

本机与 Linux 镜像构建各 **75 项通过，0 失败、0 错误、0 跳过**。新增 4 项健康端点测试、3 项请求日志测试；原 68 项业务、安全、迁移、配置用例保留。Boot 配置处理器迁到新接口及 spring.factories 键，仍验证配置错误发生在数据库初始化之前。

最终应用镜像 ID：`sha256:55d4a21a9132018993f327f5501397236886eb92b33b95987c8f58b6c8ba492a`。基础镜像的摘要保存在 Dockerfile / compose.yaml；未推送镜像或 Git 分支。

## 真实 HTTP 与 MySQL 验收

最终报告：`target/compose-verification/counselor-verify-f627c37e6bb6/result.json`，`passed=true`，以下 10 组检查全部通过。失败运行也保留在本机 target 目录。已修正脚本中的同源绝对跳转处理、PNG 夹具 CRC，以及 MySQL 客户端未显式设置 utf8mb4 的中文查询问题；没有放宽服务端校验。

| 检查 | 结果 |
| --- | --- |
| 慢数据库 | 人为延迟 MySQL 12 秒，未健康时应用不启动 |
| 空库缺少初始化凭据 | V1–V4 完成，账号数为 0，应用非零退出；填写凭据后启动成功 |
| 首次登录与管理端点 | 只有初始化的管理员，没有 demo 档案；匿名及管理员都不能访问其他管理端点 |
| 真实 multipart 与图片 | 无 CSRF 上传为 403；有效上传建档成功，中文院系与姓名查询正常，PNG 需登录读取 |
| 只读权限与旧会话 | 伪造写请求被拒绝，停用账号后旧会话转到重新登录入口 |
| 容器约束 | UID 10001、根目录只读、回环端口、DB 不发布端口、日志驱动与限额符合预期 |
| 数据库故障 | 匿名和携带登录 Cookie 的 liveness 均 200，readiness 均 503；业务返回 503，Docker 标记 unhealthy，应用未重启 |
| 数据库恢复与日志 | 原应用与登录继续使用，readiness 自动恢复；错误编号可在日志找到，未出现凭据、测试查询内容或客户端伪造编号 |
| 仅重建应用 | 移除首次凭据后启动成功；密码哈希、账号／档案／院系／状态历史数量和头像 SHA-256 一致；内存会话按预期失效 |
| 整组 down/up | 保留数据卷，重新登录成功，数据数量、哈希与图片再次一致 |

临时项目结束后执行仅针对该随机项目的 down --volumes，确认未遗留 counselor-verify 容器或数据卷。已有 LangChain、Neo4j、Redis 容器保持停止状态，没有操作其数据；测试凭据已清理。仅保留可复用镜像、构建缓存与本机脱敏报告。

## 已知边界与下一步

Flyway 11.14.1 对 MySQL 8.4 和 H2 2.5.250 仍输出超出内置验证范围的提示。本阶段保留该提示，不能把具体用例通过表述为供应商完整兼容认证；4D 继续核对支持范围和迁移回归。H2 覆盖至 2.5.250 是为修复实际触发的约束校验缺陷，并非部署数据库。

当前没有完整 SCA／镜像漏洞扫描、HTTPS、登录限流、可信代理、告警或多实例会话。Docker unhealthy 不自动摘流，也不自动重启仍在运行的进程。日志保留限制已核对配置，未用大量日志填满卷测试轮转；退出窗口已配置，未做长事务停止压测。

下一阶段为 **4C：数据库与头像协调备份、清单校验和独立空环境恢复**。容器重建保留卷不是备份；恢复须同时核验账号、档案、状态历史、审计和图片引用，再以真实 HTTP 登录、搜索、编辑、读取图片验收。
