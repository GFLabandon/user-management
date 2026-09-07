# 部署配置与启动（4A）

当前提供独立 `deploy` 配置和固定启动入口。容器编排、健康检查、HTTPS 入口、备份脚本分别属于后续 4B–4D；此页不表示已经完成公网部署。

## 三种用途

| 入口 | 用途 |
| --- | --- |
| `./mvnw spring-boot:run` | 默认 demo + H2，虚构资料与演示账号 |
| `--spring.profiles.active=mysql` | 保留本地 MySQL 调试及受控非 Web 迁移流程 |
| `scripts/run-deploy.sh` | 固定启用 deploy，执行数据库连接前的配置检查 |

启动脚本不接受附加命令行参数，防止误把它改成 demo。可通过 `APP_JAR` 指定已构建的 JAR，通过 `JAVA_BIN` 指定 Java 可执行文件。脚本不会自动读取 `.env` 文件。

## 准备并启动

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
- 登录后仍返回登录页：本地 HTTP 验收时检查 Secure Cookie 是否仍为 true；实际 HTTPS 部署保持 true。当前不信任任意转发头，代理信任配置尚待 4B 验收。

具体环境和验证结果见 [4A 验收](../acceptance/phase-4a-deployment-config.md)。使用的依赖与发布前遗留问题见[版本核对](../verification/dependencies-2026-09-07.md)。
