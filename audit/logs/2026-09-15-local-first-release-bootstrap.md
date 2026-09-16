# 2026-09-15 本机首版 V1 清库重建与启动

## 决策

经明确授权，将本机生产演练数据从旧的多版本 Flyway 历史切换为正式首版单 V1 基线。操作范围仅限项目数据根目录中的 PostgreSQL 与 RustFS 对象数据目录；未使用 `flyway repair`，未改写历史记录。

## 执行与影响

先创建完整恢复组：`20260915-110014-6200.dump` 与同名 RustFS 数据归档。随后停止并移除 `materials-lab-local-production` 的旧 Compose 容器和网络，删除：

- `postgres/`
- `rustfs/data/`

保留 `backups/`、`releases/`、`rustfs/logs/` 及运行密钥目录。使用提交 `c9b3b234ceed` 构建不可变 API/Web 镜像和发布清单，再通过 `prepare`、`bootstrap`、`up` 完成重建。未推送远程仓库，未操作远程服务器。

## 验证与恢复

- Flyway 成功执行且仅有版本 `1`。
- 业务组织数 `0`、内部平台组织数 `1`、平台管理员数 `1`；未创建项目、实验或测试数据。
- API、Web、PostgreSQL 与 RustFS 均健康；本机 Web 入口为 `127.0.0.1:15105`。
- 若需恢复旧演练数据，停止当前服务后使用同一恢复组的 PostgreSQL 转储与 RustFS 数据归档按生产脚本恢复；恢复前应先在隔离环境验证。
