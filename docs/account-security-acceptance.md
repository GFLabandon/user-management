# 第三阶段：账号与权限验收

日期：2026-09-06。基线为第二阶段提交 `1adc0da`，开发分支 `codex/account-security`。本记录对应第三阶段工作树；本轮没有推送、部署或修改简历。

## 实现范围

- `system_accounts` 独立保存登录名、bcrypt 哈希、固定角色、启用状态、可选档案关联和版本。建档不自动开账号，旧 `roles/user_roles` 不参与授权。
- Spring Security 接管登录、Session、退出和 CSRF。管理员维护档案、院系和账号，并查看操作记录；只读账号可查看全部档案、院系和已关联的头像。
- 每次请求读取账号最新状态。停用、修改权限、修改密码或其他账号更新都会增加版本，旧会话在下一次请求时失效；已开始执行的请求不追溯撤销。
- 账号管理使用数据库锁串行检查，禁止停用或降级当前管理员，并至少保留一个启用的管理员。登录名不可更改，密码不在表单失败后回显。
- 登录结果、CSRF／权限拒绝，以及账号、档案、院系的主要成功与已处理失败操作写入 `audit_events`。成功记录与业务同事务，失败记录另起事务；不保存密码或档案全文。
- 上传限制 5 MB、边长 2048 像素、总像素 400 万，检查真实图片格式并重新编码。只有已关联档案的图片可被登录用户读取，响应禁止缓存，拒绝符号链接；保存失败保留原图片并清理新文件。

## 自动化结果

命令：`./mvnw --batch-mode --no-transfer-progress clean verify`，JDK 17。

结果：37 项通过，0 失败、0 错误、0 跳过，JAR 打包成功。

| 测试类 | 数量 | 主要验证 |
| --- | ---: | --- |
| CounselorManagementWebTests | 15 | 原档案与院系流程、版本冲突、两条分页 SQL、字段白名单和图片补偿，改用数据库认证主体及 CSRF |
| AccountSecurityTests | 8 | 哈希、登录失败、CSRF、只读拒绝、账号停用、角色／密码变更撤销会话、管理员保护、账号表单、私有图片 |
| CounselorTransactionTests | 3 | 状态记录失败和普通编辑的审计失败均回滚业务与版本 |
| FileStorageServiceTests | 6 | 真 PNG、伪装内容、类型不符、缺失类型、文件超限、尺寸超限、尾部内容移除和中断清理 |
| MigrationTests | 4 | 空库、拒绝未知非空库、缺失映射、明确映射与后续迁移 |
| CounselorManagementApplicationTests | 1 | 应用上下文 |

源码位于 `src/test/java/io/github/gflabandon/counselor/`。完整本地报告为 `target/surefire-reports/`，构建产物不提交。

## 真实 MySQL 与 HTTP 验收

使用独立 MySQL Community Server 9.0.1，仅监听本机 `13317`；应用仅监听 `127.0.0.1:18088`。测试库、头像和随机数据库密码均位于专属临时目录，未接入现有数据库。临时实例验收后关闭，数据目录和凭据文件清理。

1. 先以非 Web 模式迁移至 V3，在库中写入一份虚构档案 `SEC-001`，再以 Web 应用运行 V4。原工号、姓名、ID 和版本保持不变；V4 成功记录存在，初始化账号只保存 `{bcrypt}` 哈希。
2. 另一个空库不提供 `APP_BOOTSTRAP_USERNAME/PASSWORD`：启动因缺少首次凭据失败；不存在隐式管理员。非 Web 迁移命令仍可独立执行，不触发账号初始化。
3. MySQL 登录页面不展示演示密码；管理员使用初始化密码登录成功。缺少 CSRF 的写请求返回 403。
4. 管理员通过真实表单建立只读账号；只读账号可读档案，访问账号管理或伪造停用档案请求均返回 403。
5. 使用真实 PNG 和浏览器同样的 multipart 格式，将 CSRF 隐藏字段放在请求正文中，更新档案成功。无需关闭 CSRF 或把 token 放进 URL。
6. 同一头像：匿名访问跳转登录，只读账号读取成功，响应 `Cache-Control: no-store`；管理员停用只读账号后，该账号原会话访问头像被送回登录页。
7. SQL 核对档案更新成功记录、访问拒绝记录存在，原因字段未包含测试密码。
8. 停止应用，移除初始化账号变量后重启。原管理员密码仍可登录，账号总数、只读停用状态、档案版本和头像均保持；Flyway 不重复迁移。

Flyway 对 MySQL 9.0.1 提示版本高于该 Flyway 版本已测试范围；以上是本机观察结果，不等同于受支持生产组合的认证。部署阶段需单独选择并验证受支持版本。

## 页面检查

- 实际浏览器登录后检查管理员导航和账号列表，停用状态显示正确。
- 新建账号表单在桌面及 390 × 844 手机视口下检查，字段与按钮可访问，页面没有横向溢出。
- 截图仅含虚构验收账号：[账号列表](images/accounts.png)、[手机表单](images/account-form-mobile.png)。测试页面已关闭，临时视口设置已恢复。

## 使用与边界

本地 `demo` 账号为 `admin / demo-admin-pass` 和 `viewer / demo-viewer-pass`；非演示环境首次建立管理员后应移除初始化凭据。变更这些变量不会重设已有密码，后续密码修改通过管理员账号页面进行。

当前只有两种固定角色，所有启用账号可读全部档案，没有院系数据隔离。操作记录不是字段变更快照或防篡改日志；缺少登录限流、密码找回、MFA、HTTPS 部署和依赖漏洞验收。历史图片不自动重新编码，数据库与文件也不共享事务；清理失败会记日志，尚无持久化补偿队列。

下一阶段是可复现部署、持久化、健康检查、日志、备份恢复和发布说明，本轮未开展公网部署。

技术依据：[Spring Security CSRF](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html)、[数据库用户认证](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/passwords/user-details-service.html)、[请求授权](https://docs.spring.io/spring-security/reference/6.5/servlet/authorization/authorize-http-requests.html)。
