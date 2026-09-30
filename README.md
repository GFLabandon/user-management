# 高校辅导员信息管理系统

Campus Counselor Management

![Java 17](https://img.shields.io/badge/Java-17-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot 4.0](https://img.shields.io/badge/Spring_Boot-4.0-6DB33F?logo=springboot&logoColor=white)
![MyBatis](https://img.shields.io/badge/MyBatis-3-111827)
![Tests](https://img.shields.io/badge/tests-75_passed-177454)

由 `user-management` 演进而来的辅导员档案管理原型，采用 Java 17、Spring Boot、MyBatis、Thymeleaf 与 Flyway。支持建档、检索、院系维护、状态变更和头像上传。

> 当前完成档案业务、账号权限、4A 配置、4B 容器运行、4C 协调备份恢复及 4D 本机 CI 同入口验收。档案与登录账号分开，采用 Spring Security 表单登录、管理员／只读授权与 CSRF 防护。支持本机单实例容器演示，没有真实学校交付或生产运行记录。

![辅导员档案列表](docs/images/archive/phase-2/counselors.png)

## 当前功能

- 工号唯一且统一大写，姓名允许重复；支持院系、在职／停用、备注和头像。
- 按工号或姓名检索，按院系、任职状态筛选；分页、稳定排序及页码越界处理。
- 列表使用一次计数和一次关联分页查询，避免逐条查询院系。
- 新增、编辑、停用档案；保留状态变化、操作者与时间。恢复在职可在编辑页完成。
- 版本号检查：过期编辑和停用请求不能覆盖已保存的修改。
- 院系新增、改名、停用；已被新档案或旧资料引用的院系禁止删除。
- 专用表单对象与字段白名单，数据库主键、照片路径不接受表单直接赋值。
- 独立账号、bcrypt 密码哈希、管理员／只读权限；账号停用或变更后，旧会话在下一次请求失效。
- 登录和写表单保留 CSRF；管理员可维护账号并查看最近 100 条操作记录。
- 头像读取要求登录且被档案引用。上传校验 JPG/PNG 内容、5 MB、2048 像素边长和 400 万总像素，重新编码去除元数据及尾部内容；失败时清理新头像，事务提交后才删除旧头像。
- Flyway 管理表结构；旧资料迁移要求明确工号映射，保留原 ID、姓名、院系、照片路径与旧角色关系。

## 快速运行

要求 JDK 17 或更高版本，无需预装 Maven。仓库和本地目录暂保留 `user-management`。

```bash
git clone https://github.com/GFLabandon/user-management.git
cd user-management
./mvnw spring-boot:run
```

默认启用 `demo` 配置，以内存 H2 加载三条带 `DEMO-` 工号的虚构资料。访问 [本地应用](http://localhost:8080)，管理员 `admin / demo-admin-pass`，只读账号 `viewer / demo-viewer-pass`。H2 数据随进程退出丢失；持久化验收使用 MySQL。

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
java -jar target/campus-counselor-management-0.1.0-SNAPSHOT.jar
```

## 部署配置（4A）

使用 `scripts/run-deploy.sh` 固定启用 deploy，提供 `.env.deploy.example` 中的连接与目录变量。启动前拒绝演示配置混用、root 数据库账号和无效上传目录。步骤见[部署指南](docs/guides/deployment.md)，版本及发布遗留项见[依赖核对](docs/verification/dependencies-2026-09-13.md)。尚未提供 HTTPS 公网部署。

## 容器启动（4B）

要求 Docker 和 Compose v2。复制 [.env.compose.example](.env.compose.example) 为 `.env.compose`，设置文件权限 `600`，填写独立数据库密码、root 密码和首次管理员账号密码后执行：

```sh
docker compose --env-file .env.compose config --quiet
docker compose --env-file .env.compose up -d --build --wait --wait-timeout 240
```

访问 `http://127.0.0.1:8080`，使用填写的账号登录。构建阶段运行全部测试，应用以非 root 用户运行；MySQL 和头像使用独立数据卷。初始化成功后移除首次管理员凭据。正常停止用 `down` 保留卷，不加 `--volumes`。重建应用需要重新登录，档案和头像保留。

健康端点为 `/actuator/health/liveness` 和 `/actuator/health/readiness`；数据库断开时后者返回 503，前者仍表示进程存活。日志按请求编号关联并限制保留大小。详见[部署指南](docs/guides/deployment.md)与 [4B 验收](docs/acceptance/phase-4b-runtime.md)。这不是 HTTPS 公网部署流程。

## 备份与恢复（4C）

提供 `scripts/backup.sh`、`scripts/restore.sh`，要求 Python 3.9+。在维护窗口停止应用、锁定数据库写入，协调导出 SQL 与头像，并生成文件清单、SHA-256 和全部数据行摘要。备份后应用保持停止，由操作者检查后启动。

恢复只接受全新的 Compose 项目，使用备份记录的镜像；拒绝覆盖已有项目、损坏备份或缺少引用图片的备份。数据和图片校验完成后，再启动应用进行网页登录检查。完整命令、凭据与镜像准备见[备份恢复指南](docs/guides/backup-and-restore.md)，实测见 [4C 验收](docs/acceptance/phase-4c-backup-recovery.md)。

## 新 MySQL 数据库（本地调试）

创建空库并为专用账号授予所需权限。当前启动时由 Flyway 执行迁移，账号需要建表、改表、索引及业务读写权限。

```sql
CREATE DATABASE campus_counselor CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

通过环境变量提供连接信息：

```bash
export SPRING_PROFILES_ACTIVE=mysql
export DB_URL='jdbc:mysql://localhost:3306/campus_counselor?useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true'
export DB_USERNAME='counselor_app'
export DB_PASSWORD='your-local-password'
export APP_BOOTSTRAP_USERNAME='your-admin-name'
# 在当前终端安全设置 APP_BOOTSTRAP_PASSWORD，勿将真实密码提交到仓库。
export UPLOAD_DIR='./uploads'
./mvnw spring-boot:run
```

`mysql` 配置不会加载演示资料或演示账号。空账号库首次启动必须提供 `APP_BOOTSTRAP_USERNAME` 和 `APP_BOOTSTRAP_PASSWORD`，密码为 12–64 个字符且 UTF-8 不超过 72 字节，否则启动失败。首次建立管理员后移除初始化凭据，重启不会重设已有密码。先新增院系，再建立档案。连接参数没有 root 或默认数据库回退。以上参数适用于本机验收，正式环境另行配置。项目不自动加载 `.env` 文件，变量说明见 [.env.example](.env.example)。

**已有旧版数据时，不要直接按空库流程运行。** 先备份数据库与上传目录，按[迁移与恢复说明](docs/guides/counselor-migration.md)在副本中演练。自动 baseline 默认关闭；缺少工号映射会停止迁移。旧初始化 SQL 已移到 `docs/legacy/`，不再参与运行时初始化；`DB_INIT_MODE` 已移除。

## 数据与请求链路

```mermaid
flowchart LR
    Browser[Thymeleaf 表单] --> Controller[专用表单校验]
    Controller --> Service[档案事务与版本检查]
    Service --> Mapper[MyBatis 分页与参数化 SQL]
    Mapper --> DB[(H2 / MySQL)]
    Service --> History[状态记录]
    Controller --> Storage[头像存储与失败清理]
```

`Counselor` 保存业务档案，`Department` 保存院系信息。旧 `users/roles/user_roles` 仅用于迁移核对，不参与授权。`SystemAccount` 独立存入 `system_accounts`，可选择关联一份档案；建档不会自动开通账号。

档案、院系和头像由 Spring Security 统一保护；`/accounts` 与 `/audit` 仅管理员可访问。`/users/list` 保留只读跳转；旧新增、编辑、删除地址已退役。

## 测试与验收

```bash
./mvnw --batch-mode --no-transfer-progress clean verify
```

2026-09-13：本机与 Linux arm64 镜像构建均为 75 项测试通过，0 失败、0 错误、0 跳过。覆盖业务、分页 SQL 次数、事务回滚、迁移、文件清理、账号、CSRF、权限拒绝、旧会话撤销、私有头像、部署配置、初始化和认证补丁；新增健康端点隔离、可用性变化及脱敏日志验证。测试数量不代表生产质量或性能指标。

独立 MySQL 9.0.1 的历史迁移与恢复结果见[第二阶段记录](docs/acceptance/phase-2-counselor-records.md)。V3→V4、首次凭据及登录／multipart／权限流程见[第三阶段记录](docs/acceptance/phase-3-account-security.md)；专用非 root MySQL 账号启动和重启见 [4A 记录](docs/acceptance/phase-4a-deployment-config.md)。4B 新增 MySQL 8.4.11 容器故障恢复和持久化验收，可用 `python3 scripts/verify-compose.py` 重复执行。4C 在 2026-09-25 通过 13 项脚本安全测试和 8 组真实 MySQL 恢复验收，覆盖全部表数据、密码哈希、头像、恢复后登录／编辑及失败保护；4C 没有修改 Java 或 V1–V4，当时未重跑上述 75 项 Java 测试。

```sh
python3 -B -m unittest discover -s scripts/tests -v
python3 -B scripts/verify-recovery.py
```

2026-09-30：重新通过 75 项 Java、18 项 Python 测试，以及 10 组运行、5 组真实 MySQL 迁移、8 组恢复验收。CI 配置分为快速测试、MySQL、安全扫描三个任务；本阶段未推送，远程 CI 尚未运行。安全扫描及发布边界见 [4D 验收](docs/acceptance/phase-4d-ci-release.md)和[发布指南](docs/guides/release.md)。

```sh
docker build --target migration-verification -t campus-counselor-migrations:local .
python3 -B scripts/verify-mysql-ci.py
python3 -B scripts/install-trivy.py
python3 -B scripts/check-security.py
```

## 页面预览

| 账号管理（当前） | 档案详情（第二阶段） |
| --- | --- |
| ![账号管理](docs/images/current/accounts.png) | ![档案详情](docs/images/archive/phase-2/counselor-detail.png) |

截图仅含虚构验收资料。除 `accounts.png`、`account-form-mobile.png` 外，其余截图为早期阶段，登录信息以本文为准。

## 项目结构

完整索引见[文档导航](docs/README.md)，后续开发顺序见[优化方案](docs/plans/next-optimization-plan-2026-09-06.md)。

```text
src/main/java/
├── db/migration/                         # V3 旧资料映射迁移
└── io/github/gflabandon/counselor/
    ├── CounselorManagementApplication.java
    ├── config/                          # Mapper 扫描、密码编码、Security 配置
    ├── controller/                      # 登录、档案、院系、账号、审计、头像
    ├── entity/                          # 档案、院系、账号、状态及操作记录
    ├── mapper/                          # SQL 与结果映射
    ├── service/                         # 事务、业务规则、图片存储
    ├── security/                        # 数据库认证与账号会话检查
    └── web/                             # 专用表单和分页结果
src/main/resources/
├── db/migration/                        # 版本化结构迁移
├── db/demo/                             # 仅 demo 配置启用的虚构资料
└── templates/                           # Thymeleaf 页面
src/test/java/                           # 自动化回归测试
docs/
├── guides/                             # 迁移等操作指南
├── acceptance/                         # 各阶段验收记录
├── plans/                              # 后续优化方案
├── verification/                       # 依赖快照与维护核对
├── images/                             # current 当前截图、archive 历史截图
└── legacy/                             # 原版 SQL，供迁移副本与恢复核对
```

## 当前边界

- 当前是两种固定角色，所有启用账号可读全部档案；尚无院系数据范围、MFA、登录限流、密码找回或 SSO。
- 操作记录包含主要业务成功、表单失败、登录结果和权限拒绝；不保存密码或档案全文，也不提供字段前后值或防篡改存储。
- 新上传图片重新编码；历史图片只增加读取权限，不自动重编码。尚无病毒扫描或对象存储。
- 文件与数据库不在同一个事务中，当前提供同步失败补偿和清理失败日志，尚无持久化清理队列。
- 没有学生／班级管理、审批、导入导出、AI 功能、生产部署或高并发证据。

4D 的工作流与发布指南已落地，应用补丁后的高危／严重项已清除；MySQL 官方镜像仍有 28 个阻断项，安全门禁未通过。先处理这些发现并运行候选提交的远程 CI，之后再推进 5A 的错误页面、账号选择与操作记录体验。Flyway 的版本验证提示、HTTPS、登录限流仍需处理。[项目证据](docs/project-evidence.md)区分当前实现、历史记录与待开发能力。
