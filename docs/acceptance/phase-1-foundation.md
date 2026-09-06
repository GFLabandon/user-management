# 阶段一：工程命名与页面统一

日期：2026-09-06。分支：`codex/counselor-foundation`。

本轮依据“检查通用简历并制定微调方案”对话中的首轮方案实施，只完成基础重构。当前是开发原型，没有真实学校交付或上线记录。

## 基线与命名

基线提交：`cedba0c7e29acfd5d5e49e0ff186e453dea9666e`，开始时工作树干净。

| 对象 | 当前名称 |
| --- | --- |
| 中文产品名 | 高校辅导员信息管理系统 |
| 英文名称 | Campus Counselor Management |
| Maven groupId | `io.github.gflabandon` |
| artifactId / application.name | `campus-counselor-management` |
| Java 根包 | `io.github.gflabandon.counselor` |
| 启动类 | `CounselorManagementApplication` |
| 构建产物 | `target/campus-counselor-management-0.1.0-SNAPSHOT.jar` |

生产源码、测试、Mapper 扫描和日志包配置已同步迁移。页面统一中文产品名、人员资料用语与反馈，补充姓名唯一和资料角色用途提示。窄屏导航允许换行，避免较长产品名挤压按钮。

仓库地址和目录保留 `user-management`。`User`、`UserController`、`UserService`、`UserMapper`、`/users/**` 和四张原表均保留，登录与文件存储机制不变。姓名仍使用唯一的 `username` 字段；角色关系仍不授予登录账号权限。没有将工程改名伪装成辅导员业务或正式认证的实现。

## 验证结果

环境：macOS arm64、Temurin 17.0.20、Maven Wrapper、默认 H2。依赖版本保持不变。

| 检查 | 结果 |
| --- | --- |
| 基线 `./mvnw --batch-mode --no-transfer-progress test` | 11 项通过，0 失败、0 错误、0 跳过 |
| 重构后 `./mvnw --batch-mode --no-transfer-progress clean verify` | 11 项通过，JAR 打包成功 |
| JAR 清单与实际启动 | 新 Start-Class 与应用标识生效 |
| HTTP 验收 | 登录、错误密码、列表、带 PNG 和多角色新增、搜索、详情、编辑、角色替换、重复资料提示、删除、图片清理、退出和退出后拦截均通过 |
| 浏览器检查 | 登录、列表、新增与编辑页面；部门和角色回填正确；1280px 桌面与 390px 窄屏布局检查 |
| 引用检查 | 源码无旧 Java 包名或旧启动类引用；SQL、MySQL 配置和路由无迁移 |
| `git diff --check` | 通过 |

首次在受限执行环境运行测试时，Mockito / Byte Buddy 无法附加 JVM，产生 8 个环境错误。随后在正常本机执行环境运行相同基线命令及重构后构建，全部通过；没有为绕过问题修改或跳过测试。

HTTP 验收启动命令：

```bash
java -jar target/campus-counselor-management-0.1.0-SNAPSHOT.jar \
  --server.address=127.0.0.1 --server.port=18086 \
  --app.upload-dir=/private/tmp/counselor-foundation-uploads
```

验收只操作独立进程的 H2 内存示例数据与临时上传目录。新增验收记录已删除，图片清理已确认。新版截图为 `docs/images/archive/phase-1/login.png` 和 `directory.png`；原 `.jpg` 留作历史记录。MySQL 本轮未重新验收，历史结果见 [2026-08-07 记录](2026-08-07-mysql.md)。

## 下一阶段

按以下顺序逐步验收，不以预设测试数量作为目标：

1. **辅导员档案**：独立 `Counselor`，补充工号、姓名、部门、任职状态、时间戳与 version。工号唯一、姓名允许重复；补分页、筛选、停用和并发编辑冲突处理。表单采用专用输入对象。
2. **数据迁移**：在数据库副本演练 Flyway，保留旧 id、姓名、部门和照片路径。旧 username 不能直接当工号；旧角色关系不自动变成权限。迁移备份包含数据库和上传文件，验证 MySQL 重启持久化及恢复。
3. **账号权限**：独立 `SystemAccount`，使用 Spring Security、密码哈希、管理员与只读角色、CSRF、受保护头像访问和操作记录。建档不自动开通账号。
4. **部署准备**：可复现的应用与 MySQL 部署、健康检查、日志、持久化目录、凭据检查、备份恢复与依赖检查。完成后再讨论公网演示和真实需求验收。

以上是后续设计方向，并非已实现功能。下一轮同时迁移对象、路由和访问保护，不能仅全局替换 `User` 为 `Counselor`。

## 回退与交接

本轮未迁移数据库、改 GitHub 仓库名、推送、部署或修改简历。第一阶段作为独立本地提交保存于上述分支；提交记录可通过 `git log` 查看。切回基线前先提交或另行保存当前修改；不要通过强制清理丢弃工作。回退运行旧包时重新构建，避免混用 `target/` 中的不同产物。
