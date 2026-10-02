# 数据库与头像的备份、恢复（4C）

2026-10-02 起 Compose 使用同版本 MySQL 8.4.11 派生运行镜像，见[镜像指南](mysql-runtime-image.md)。备份格式不变；恢复继续使用备份清单中的原始镜像 ID，不会自动换成新构建。旧备份依赖的官方镜像仍应保留。

适用于本项目单实例 Compose、MySQL 8.4.11、Flyway V1–V4。脚本只支持项目独占的命名卷、未发布宿主机端口的数据库；不用于共享数据库、外部卷、多实例或跨版本迁移。需要 Python 3.9+、Docker、Compose，以及已运行过的应用与数据库镜像。

## 备份前确定维护窗口

备份包含账号密码哈希、档案、操作记录和头像，属于敏感资料。脚本以目录 700、文件 600 保存；`backups/` 已被 Git 忽略。它没有加密或签名功能，应存放在受控且加密的存储位置，并另行保留异机副本。SHA-256 用于发现损坏，不证明备份来源可信；只恢复自己保管的可信备份，因为 SQL 恢复本质上会执行其中的语句。

开始前通知使用者暂停操作，确认没有其他管理员直接写数据库或头像卷。脚本停止当前项目的应用，再使用该数据库容器的 root 凭据持有一个专用连接，执行 `FLUSH TABLES WITH READ LOCK`。只在该独立实例上阻止写入；数据库和文件导出都结束后才释放锁。应用停止阻止上传、编辑和登录审计写入，数据库锁防止导出期间 SQL 变化。不要在这段时间手动重启应用、修改文件或重启数据库。

数据库导出使用同镜像的 mysqldump、应用库账号、UTF-8、single-transaction、no-tablespaces 和关闭 GTID_PURGED。头像卷只读挂载到无网络的临时工具容器。脚本拒绝共享卷、特殊文件、链接及有目录穿越风险的文件名。备份期间正常部署不可写入，但主机管理员仍能绕过这些约束，因此维护窗口必须由单一操作者协调。[MySQL 导出选项](https://dev.mysql.com/doc/refman/8.4/en/mysqldump.html)、[Docker 卷](https://docs.docker.com/engine/storage/volumes/)

## 创建备份

明确项目名，不能依赖当前终端隐式的 COMPOSE_PROJECT_NAME。环境文件须为 600，包含该运行实例真实的 DB_PASSWORD、MYSQL_ROOT_PASSWORD；脚本不会通过修改它们重置已有数据库密码。

```sh
mkdir -m 700 backups
chmod 600 .env.compose
./scripts/backup.sh --project counselor --env-file .env.compose \
  --output backups/counselor-20260925-01
```

输出目录必须不存在，父目录必须预先存在。成功产物：

```text
counselor-20260925-01/
├── database.sql
├── uploads.tar
└── manifest.json
```

清单记录备份时间、源项目、执行脚本时的 Git 提交、实际容器镜像 ID、MySQL 版本、Flyway 版本与校验信息、全部 11 张表的行数和数据行摘要、SQL / tar 摘要与大小、每张图片的大小和摘要，以及当前档案的图片引用。未被当前档案引用的合法图片也原样保留，不自动清理。

`source_checkout_commit` 表示操作者工作区提交，不把它冒充容器的构建提交；实际应用身份以 `app_image` 的不可变镜像 ID 为准。恢复要求相同应用与数据库镜像在目标 Docker 主机可用。迁移到另一台主机前，按该 ID 用 `docker image save` / `docker image load` 另行保存、传输镜像；本轮没有验收跨架构迁移。

**进入维护窗口并停止应用后，无论备份成功或失败都不自动恢复写入。** 成功后可先校验备份，再显式启动源应用：

```sh
python3 scripts/maintenance.py verify --backup backups/counselor-20260925-01
docker compose --project-name counselor --env-file .env.compose start app
```

启动后用同一项目和环境文件运行 `docker compose ... ps app`，确认状态为 healthy，再访问页面。旧版 Compose 的 `start` 没有 `--wait` 参数；自动演练通过轮询容器健康状态等待，不通过 `up` 重建。

失败目录保留 `.incomplete`，不能作为有效备份恢复。不要覆盖或删除旧备份来重试，改用新目录名。检查具体失败原因、数据库锁是否释放及图片引用，再决定恢复源应用。常见错误包括缺少被引用的图片、无权限读取文件、空间不足、数据表不符合 V1–V4 或数据库连接断开。

## 恢复到新的空环境

**不允许覆盖现有项目，连已停止或空项目也拒绝。** 目标不得有同名 Compose 容器、网络或数据卷。没有 force / overwrite 开关，不执行 DROP DATABASE 或删除旧卷。原应用和数据卷保持可用，回退由操作者决定。

1. 在当前版本代码目录中，为恢复环境准备独立环境文件和未使用的本机端口。DB_PASSWORD / MYSQL_ROOT_PASSWORD 可使用新的随机值；网页登录密码来自备份。清空首次管理员变量，避免误解为重设账号。
2. 校验备份：脚本先复制到私有临时目录并重新验证，导入时只读这份已检查的副本。文件缺失、摘要不符、非法 tar 路径、链接、重复文件或缺少引用图片时，在创建目标前即拒绝。
3. 脚本创建新数据库及停止状态的应用容器，再次检查数据库无表、头像卷为空、数据库版本一致，才导入。使用库级应用账号导入，mysql 客户端关闭 LOCAL INFILE 和自动重连并使用 binary-mode。
4. 导入后核对全部表行数、数据行摘要、Flyway 历史、管理员存在性、图片引用和每个文件摘要。所有检查完成前不启动应用。

```sh
cp .env.compose.example .env.restore
chmod 600 .env.restore
# 编辑 .env.restore：新数据库密码、空的首次账号变量，以及 APP_PORT=8081。
./scripts/restore.sh --project counselor-restored-20260925 \
  --env-file .env.restore --backup backups/counselor-20260925-01

# 使用 start 启动脚本创建的精确镜像容器，先不要使用 up 重建。
docker compose --project-name counselor-restored-20260925 \
  --env-file .env.restore start app
```

访问 `http://127.0.0.1:8081`，使用备份中的管理员账号登录。依次检查检索、档案详情、图片、只读权限，并在可丢弃的恢复副本中完成一次编辑，确认状态历史和审计继续写入。新实例不会恢复内存 Session，必须重新登录。

恢复脚本为创建过程临时固定清单中的镜像 ID；已创建的容器保留这些 ID。后续若用 `up` 重建，须在受控 Compose 覆盖文件中继续固定同一镜像，或走已验证的升级流程，不能让 mutable `:local` 标签悄悄替换运行版本。

失败时目标可能已部分导入，脚本保持应用停止，不自动清空或回滚目标。保留目标用于排查；修正后选择另一个全新项目名重试，不在半成品上重复导入。源项目始终不作为恢复目标。只有完成网页验收后，才考虑切换服务入口；本脚本不开放公网或自动切流。

## 演练与边界

```sh
python3 -B -m unittest discover -s scripts/tests -v
python3 -B scripts/verify-recovery.py
```

演练使用随机 `counselor-recovery-*` 项目和回环端口，生成虚构数据，在新环境完成恢复及真实 HTTP 登录、检索、编辑和图片读取。同时验证数据库锁阻止写入、异常释放锁、损坏备份拒绝、已有目标拒绝和缺失引用图片的失败状态。结束后仅清理本次项目及测试备份，保留不含密码哈希和图片正文的摘要报告到 `target/recovery-verification/`。

同一工作区的维护命令使用文件锁避免并发操作同一项目；它不是跨主机的运维协调器。脚本不支持未完成的迁移、重复 demo 迁移、存储过程、事件、额外业务表或跨数据库版本恢复。新增迁移后应先扩展清单与演练，再启用新版本备份。恢复耗时只反映本次小样本，不承诺固定 RTO；RPO 取决于实际备份频率，本轮没有自动定时备份或零丢失承诺。

本轮具体数据、耗时和测试结果见 [4C 验收记录](../acceptance/phase-4c-backup-recovery.md)。
