# 部署配置、容器运行与健康检查（4A / 4B）

当前提供 deploy 启动入口、应用 + MySQL 容器编排、独立数据卷和健康检查。Compose 仅用于本机单实例 HTTP 演示；协调备份恢复已在 4C 验收，见[备份恢复指南](backup-and-restore.md)；发布步骤见 [4D 指南](release.md)，实际发布须核对当次 CI 与扫描结论；HTTPS 尚未实现。

## 运行入口

| 入口 | 用途 |
| --- | --- |
| `./mvnw spring-boot:run` | 默认 demo + H2，虚构资料与演示账号 |
| `--spring.profiles.active=mysql` | 保留本地 MySQL 调试及受控非 Web 迁移流程 |
| `scripts/run-deploy.sh` | 固定启用 deploy，执行数据库连接前的配置检查 |
| `docker compose --env-file .env.compose up -d --build --wait` | deploy、独立 MySQL 与头像卷、回环 HTTP |

启动脚本不接受附加命令行参数，防止误把它改成 demo。可通过 `APP_JAR` 指定已构建的 JAR，通过 `JAVA_BIN` 指定 Java 可执行文件。脚本不会自动读取 `.env` 文件。

## JAR 方式准备并启动

先在独立数据库中验证。已有旧库按[迁移指南](counselor-migration.md)备份和演练后再启动；deploy 禁止临时 baseline、target 和 demo 种子配置。

```sh
./mvnw --batch-mode --no-transfer-progress clean verify
cp .env.deploy.example .env.deploy
chmod 600 .env.deploy
# 用编辑器填写 .env.deploy，所有密码只写入该本机文件。
set -a
. ./.env.deploy
set +a
# UPLOAD_DIR 填写当前用户可管理的绝对路径，然后创建目录。
mkdir -p "$UPLOAD_DIR"
./scripts/run-deploy.sh
```

`.env.deploy` 已被 Git 忽略。不要使用终端跟踪模式 `set -x` 或把密码放入 Java 命令行参数。首次管理员建立后，从文件和当前 shell 中移除 `APP_BOOTSTRAP_USERNAME`、`APP_BOOTSTRAP_PASSWORD`；已有账号重启不会重新设置密码。

| 变量 | 规则 |
| --- | --- |
| `DB_URL` | 必填，单主机 `jdbc:mysql://host[:port]/database`；数据库名使用字母、数字、下划线或连字符 |
| `DB_USERNAME` | 必填，专用数据库账号，拒绝 root（忽略大小写和首尾空白） |
| `DB_PASSWORD` | 必填；是数据库密码，不是网页登录密码 |
| `UPLOAD_DIR` | 必填，预先存在且可创建、写入、删除文件的绝对目录；启动不会创建任意缺失目录 |
| `APP_BOOTSTRAP_USERNAME/PASSWORD` | 仅空账号库首次 Web 启动需要；密码 12–64 字符且 UTF-8 不超过 72 字节；拒绝演示／示例管理员密码 |
| `SERVER_ADDRESS/PORT` | 默认 `127.0.0.1:8080` |
| `SESSION_COOKIE_SECURE` | 默认 true；仅回环 HTTP 验收时显式设 false，否则浏览器不会在 HTTP 中携带会话 Cookie |

URL 不允许包含 user/password 或自定义驱动工厂。允许的连接选项为 `sslMode`、`useSSL`、`allowPublicKeyRetrieval`、`serverTimezone`、`connectionTimeZone`、`connectTimeout`、`socketTimeout`、`useUnicode`、`characterEncoding`，不允许重复键。扩展选项前应先检查用途并增加测试。环境变量／配置文件由受信任的运维人员管理；本校验用于防止误配，不是防御能修改任意 JVM 配置的主机管理员。

回环 HTTP + 临时 MySQL 验收可使用 `jdbc:mysql://127.0.0.1:3306/counselor_test?sslMode=DISABLED&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai` 和 `SESSION_COOKIE_SECURE=false`。这种连接参数只用于隔离本机测试；实际跨主机连接应校验数据库身份并配置 TLS，不能直接复制此例开放服务。

## 启动检查顺序

1. Spring 读取配置后、创建容器 Bean 前，检查 deploy 与 demo 不混用，拒绝无关配置组合及 `app.demo=true`。
2. 检查有效的 MySQL URL、用户名、密码和驱动；拒绝 H2、root、内嵌凭据及另一套 Hikari/Flyway 数据源配置。只检查用户名是否为 root，不会推断数据库账号的实际权限；权限范围需由数据库管理者设置。
3. 检查 Flyway 保持标准完整迁移、禁止 clean/baseline，SQL seed 关闭；检查上传目录并创建、写入、删除一个 `.counselor-startup-*.tmp` 探测文件。
4. 通过后才连接数据库和执行迁移。Web 启动中的账号初始化再根据数据库是否为空判断首次凭据；此判断无法在连接前完成。缺少首次凭据可能已完成表结构迁移，但不会创建默认管理员。

初版使用同一专用账号执行 Flyway 和业务 SQL，需要目标库上的建表、索引与业务读写权限，不授予全局权限或授权其他用户的能力。独立迁移账号属于后续扩展，本轮明确拒绝用另一套 Flyway 凭据绕过检查。

## 常见失败

- `[DEPLOY_CONFIG]`：配置在数据库连接前被拒绝。错误只说明字段或规则，不回显连接串、密码或文件路径；按上表纠正后重新启动。
- `UPLOAD_DIR`：检查目录是否存在、是否绝对路径，以及启动 Java 的用户是否确实具有写入和删除权限。若清理探测文件失败，按错误提示检查该目录内 `.counselor-startup-` 文件。
- `Empty account database requires ...`：配置通过且数据库已连接，但空账号库没有首次管理员凭据。
- `No enabled administrator`：已有账号库没有可用管理员。不会自动插入后门账号，应按可信备份和账号恢复流程处理。
- 登录后仍返回登录页：本地 HTTP 验收时检查 Secure Cookie 是否仍为 true；实际 HTTPS 部署保持 true。当前不信任任意转发头，HTTPS 与可信代理配置尚未实施。

具体环境和验证结果见 [4A 验收](../acceptance/phase-4a-deployment-config.md)。使用的依赖与发布前遗留问题见[版本核对](../verification/dependencies-2026-09-13.md)。

## 容器方式启动

要求 Docker Engine / Docker Desktop 和支持 `--wait` 的 Compose v2。无需本机 Java、MySQL 或 Maven；镜像构建阶段使用 Maven Wrapper 运行全部测试。构建和运行镜像固定 Temurin 17.0.20+8 的版本及多架构摘要，数据库固定 MySQL 8.4.11 的摘要。本轮验证架构为 Linux arm64，未实测 amd64。

```sh
cp .env.compose.example .env.compose
chmod 600 .env.compose
# 编辑 .env.compose：分别填写两个数据库密码和首次管理员凭据。
docker compose --env-file .env.compose config --quiet
docker compose --env-file .env.compose up -d --build --wait --wait-timeout 240
```

默认访问 `http://127.0.0.1:8080`，使用自己填写的首次管理员账号登录。修改 `APP_PORT` 可避免占用已有端口。容器内绑定 `0.0.0.0:8080`，宿主机仅发布 `127.0.0.1`；数据库没有发布端口。不会加载演示档案或固定账号。MySQL 使用 `counselor_app`，只授予 `counselor` 库权限；root 密码与应用数据库密码应不同。

`.env.compose` 被 Git 忽略，构建上下文使用白名单，不带入环境文件、Git、上传目录或本机打包结果。环境文件由受信任的主机管理员保管；Docker 管理者仍能读取容器环境，因此不要分享完整 `docker inspect` 或不带 `--quiet` 的 Compose 配置输出。当前未实现外部秘密管理服务。

首次登录成功后，清空 `.env.compose` 中的 `APP_BOOTSTRAP_USERNAME`、`APP_BOOTSTRAP_PASSWORD`，再重建应用。已有账号不会重新初始化密码。MySQL 初始化变量也只作用于空卷；修改环境文件不会替已有数据库账号改密码，不要为了改密码删除数据卷。

```sh
docker compose --env-file .env.compose up -d --no-deps --force-recreate --wait app
```

应用以 UID/GID `10001:10001` 运行，根文件系统只读，临时文件写入有限额的 `/tmp`，头像写入 `/app/uploads` 卷。构建时预置上传目录所有权，由新卷初始化继承；不要改为任意宿主机目录挂载后直接递归修改权限。

## 持久化、停止与重建

默认项目名 `counselor`，数据卷为 `counselor_mysql-data` 和 `counselor_uploads`。如果使用 `--project-name`，后续命令须使用同一项目名，否则会创建另一套空卷。

```sh
# 停止容器，保留数据卷。
docker compose --env-file .env.compose down
# 重新创建容器并使用原数据。
docker compose --env-file .env.compose up -d --wait
# 修改代码后的构建和应用重建。
docker compose --env-file .env.compose up -d --build --wait app
```

正常维护不要使用 `down --volumes`、`volume prune` 或 `system prune`。两个卷必须一起备份；重建容器保留数据，不等于备份。协调备份与空环境恢复按 [4C 指南](backup-and-restore.md)执行。Session 保存在进程内，重建应用需要重新登录；当前只保证单实例，不支持共享会话或无损滚动发布。

## 健康与故障定位

```sh
curl --fail http://127.0.0.1:8080/actuator/health/liveness
curl --fail http://127.0.0.1:8080/actuator/health/readiness
docker compose --env-file .env.compose ps
docker compose --env-file .env.compose logs --tail 100 app
```

| 检查 | 正常 | 数据库停止时 |
| --- | --- | --- |
| liveness | 200，只有 status 字段 | 仍为 200，进程存活 |
| readiness | 200；初始化结束且数据库可用 | 503，不返回连接或组件细节 |
| Docker app health | healthy | 连续失败后 unhealthy，进程继续运行 |
| 已登录业务读取 | 正常页面 | 通用 503，X-Request-ID 可定位日志 |

探针使用独立无状态 SecurityFilterChain，不查询账号，也不受旧登录 Cookie 影响。仅允许 GET 两个探针；health 根路径、组件子路径、env、beans 等管理地址连管理员也不能访问。探针与业务使用同一 HTTP 端口。

Compose 等待数据库以应用账号完成 TCP `SELECT 1` 后才启动应用。应用连接获取与验证设置超时，启动迁移允许有限重试。数据库恢复后 readiness 自动恢复。Compose 的 unhealthy 标记本身不停止转发或自动重启，`restart: unless-stopped` 处理进程退出等事件；当前没有代理摘流或主动告警。

每个请求由服务器产生新的 `X-Request-ID`，业务日志记录该编号、方法、状态与耗时；不记录客户端编号、路径、查询串、正文、Cookie、密码或异常消息。数据库失败记固定错误类别和 503。两个容器都向标准输出写日志，使用 Docker local 驱动，每份 10 MB、保留 3 份。没有再写第二套应用日志文件。错误页面优化属于 5A，目前编号在响应头中。

应用收到停止信号后最多等待 30 秒完成在途请求，Compose 给 40 秒退出窗口。应先停止应用再停止数据库；不要以强制 kill 作为正常维护方法。

Compose 内部数据库连接采用隔离本机网络的明文测试配置，HTTP 的 Secure Cookie 明确为 false。对外部署必须单独配置 HTTPS、可信代理和数据库 TLS，并恢复 Secure Cookie=true，不能只修改端口绑定就上线。[健康检查原理](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)、[Compose 启动顺序](https://docs.docker.com/compose/how-tos/startup-order/)

## 可重复的独立验收

```sh
docker build -t campus-counselor-management:local .
python3 scripts/verify-compose.py
```

脚本使用 Python 3.9+ 标准库，创建随机 `counselor-verify-*` 项目、回环端口和凭据。它模拟慢数据库、缺少首次凭据、真实登录与 multipart、权限和旧会话撤销、数据库停止／恢复、应用和整组容器重建。结束后仅删除该次测试的容器和卷，清理临时凭据，保留脱敏报告到 `target/compose-verification/`。不会操作默认 counselor 或其他项目。验收会运行几分钟，首次构建还需下载依赖。

结果见 [4B 验收](../acceptance/phase-4b-runtime.md)。Flyway 对 H2 2.5 和 MySQL 8.4 仍提示超出其内置验证版本；此提示保留，不能把本项目用例通过写成供应商兼容认证。
