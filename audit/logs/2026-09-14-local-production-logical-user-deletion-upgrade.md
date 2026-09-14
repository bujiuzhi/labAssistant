# 2026-09-14 本机生产普通用户删除升级

- 操作时间：2026-09-14 14:26:34 至 2026-09-14 14:27:15，Asia/Shanghai
- 环境：本机 Docker Compose 项目 `materials-lab-local-production`，入口 `127.0.0.1:15105`
- 发布版本：`f247d26 feat: 增加普通用户逻辑删除`
- 数据目录：`/Users/bujiu/work/data/labAssistant/local-production-88b0b8d`

## 执行与影响

- 升级前构建与校验不可变 API/Web 镜像，并生成 `releases/f247d26.json` 受控发布清单。
- 受控升级先停止 API/Web 写入口，再创建恢复组：`backups/20260914-142634-41357.dump` 与同名 RustFS 数据归档、校验文件和元数据。
- API 启动时 Flyway 成功执行 `V3__logical_user_deletion.sql`；本次未清库、未重置身份、未修改任何业务组织或用户数据。
- API/Web 均已重新创建并使用 `f247d26` 镜像；PostgreSQL 与 RustFS 保持原有持久目录。

## 验证与恢复

- 已验证：`bash scripts/production.sh --env .env.production check` 通过，PostgreSQL、RustFS 和 API 内部健康均为 ready。
- 已验证：Web 入口 `GET /api/v1/health/ready` 返回成功；Flyway V3 成功记录存在，`user_account.deleted_at` 与 `user_account.deleted_by_id` 字段存在。
- 已验证：API 与 Web 均处于健康状态，公开绑定仍为 `127.0.0.1:15105`。
- 恢复要点：如需恢复升级前数据，只能使用本恢复组通过 `restore-new` 恢复至新的隔离项目和全新数据目录；不得覆盖当前运行环境。应用回退前还须核实 V3 与旧应用的数据库兼容性。
