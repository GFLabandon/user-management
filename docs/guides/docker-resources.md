# 本机 Docker 资源说明与清理记录

核对日期：2026-10-02。资源名称不代表是否可删除；容器已停止也不代表其项目数据无用。

合并后追加清理：回收 565.9 MB Docker 构建缓存，镜像／容器／卷数量保持 12／4／6；另删除约 3.04 GB 可重新下载的本地 Trivy 缓存。详见[维护记录](../verification/maintenance-2026-10-02.md)。下面保留同日较早一轮的清理过程。

后续安全修复新增 `campus-counselor-mysql:8.4.11-runtime-1` 派生运行镜像及 Go 构建缓存；它们用于当前数据库构建与验收，见[镜像说明](mysql-runtime-image.md)。原官方 MySQL 镜像仍用于升级演练，并可能被旧备份依赖。

| 看到的内容 | 用途 | 本次处理 |
| --- | --- | --- |
| `campus-counselor-management:local` | 本项目可运行的应用镜像 | 保留 |
| `campus-counselor-migrations:local` | MySQL 迁移验收工具 | 保留，可由 Dockerfile 重建 |
| `campus-counselor-queries:local` | 第 6 阶段查询测量工具 | 本轮新增，保留以便复现 |
| Compose 中固定摘要的 MySQL 镜像 | 本项目数据库验收及恢复 | 保留；无标签也可能仍被摘要引用 |
| 旧的无标签应用／迁移镜像 | 历次构建后留下的版本 | 删除 10 个，未被容器或本仓库备份引用 |
| Build cache | 编译、依赖下载和镜像构建缓存 | 回收超过一小时未使用的部分，Docker 报告 411.3 MB；后续构建会再增加 |
| 14 个长随机名匿名卷 | 内容仅为旧 Neo4j 的 query／debug 等日志，约 1.4 MB | 逐卷检查文件类型／名称且确认未挂载后删除 |
| `langchain-rag-*` 容器、卷、网络及镜像 | LangChain 项目和模型／搜索数据 | 保留 |
| `medsafetyassistant-local-*` 容器、卷、网络及镜像 | MedSafetyAssistant 的 Neo4j／Redis | 保留 |
| `day4-chroma-store` | 未挂载的 Chroma 数据卷，约 1.3 MB | 数据是否仍需保留不明确，本次保留 |

最初有 20 个镜像、20 个卷。清理了 10 个旧构建镜像和 14 个日志卷，随后新增 1 个查询验收镜像。固定摘要的 MySQL 曾被当作无标签旧镜像删除，进一步核对配置后已按原摘要重新拉取成功；没有改变 `compose.yaml` 的固定版本，也没有删除数据库业务数据。

保留的 4 个容器都已停止：LangChain 的 API／OpenSearch，MedSafetyAssistant 的 Neo4j／Redis；它们可以供对应项目后续恢复。保留的 6 个卷中，5 个关联上述容器，另一个为 Chroma。未执行全局容器／卷清空。

日常可先用 `docker system df` 看分类占用，再用 `docker ps -a`、`docker image ls`、`docker volume ls` 对应项目判断。恢复备份依赖原始镜像 ID，清理旧镜像前还要检查可信备份记录；不能只看到 `<none>` 就删除。Docker 报告的逻辑释放空间不等于 macOS 磁盘文件立即缩小。

本轮查询验收使用随机隔离项目，报告记录退出前已清理临时资源；之后 Docker 服务已停止，当前是否开启以 Docker Desktop 实际状态为准。
